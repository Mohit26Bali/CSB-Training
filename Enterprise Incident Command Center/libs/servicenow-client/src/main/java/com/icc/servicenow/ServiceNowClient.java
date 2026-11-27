package com.icc.servicenow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A small client for ServiceNow's Table API: create a record, update a record, find one record by a field.
 * It uses Spring's own RestClient and Basic authentication with the integration user.
 *
 * Every failure becomes a ServiceNowException with a kind (see there), so the callers never read HTTP details.
 * No other class of the project talks to ServiceNow (hard rule 6).
 */
public class ServiceNowClient {

    private static final String TABLE_PATH = "/api/now/table/{table}";
    /** What this instance answers when it refuses a value such as an unknown close code or caller (observed 2026-11-24). */
    private static final String REJECTED_VALUE_MARKER = "IP may not be null";

    private final RestClient http;
    private final ObjectMapper json = new ObjectMapper();

    public ServiceNowClient(RestClient http) {
        this.http = http;
    }

    /** The normal way to build it: timeouts and the Basic login come from the configuration. */
    public static ServiceNowClient create(ServiceNowConfig config) {
        HttpClient jdk = HttpClient.newBuilder().connectTimeout(config.connectTimeout()).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(jdk);
        factory.setReadTimeout(config.readTimeout());
        String login = config.username() + ":" + config.password();
        RestClient client = RestClient.builder()
                .baseUrl(config.baseUrl().replaceAll("/+$", ""))
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder().encodeToString(login.getBytes(StandardCharsets.UTF_8)))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        return new ServiceNowClient(client);
    }

    /** POST: creates a record and returns it as ServiceNow answers (it includes sys_id and number). */
    public Map<String, Object> create(String table, Map<String, ?> fields) {
        String body = call(() -> http.post()
                .uri(TABLE_PATH, table)
                .contentType(MediaType.APPLICATION_JSON)
                .body(fields)
                .retrieve()
                .body(String.class));
        return single(body);
    }

    /** PATCH: changes only the fields that are sent. */
    public Map<String, Object> update(String table, String sysId, Map<String, ?> fields) {
        String body = call(() -> http.patch()
                .uri(TABLE_PATH + "/{sysId}", table, sysId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(fields)
                .retrieve()
                .body(String.class));
        return single(body);
    }

    /** GET: the first record whose field has this value, if any. */
    public Optional<Map<String, Object>> findOne(String table, String field, String value) {
        String body = call(() -> http.get()
                .uri(uriBuilder -> uriBuilder.path(TABLE_PATH)
                        .queryParam("sysparm_query", "{query}")
                        .queryParam("sysparm_limit", "1")
                        .build(Map.of("table", table, "query", field + "=" + value)))
                .retrieve()
                .body(String.class));
        List<Map<String, Object>> rows = list(body);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * Finds the record that carries our correlation id. This is what makes a retry safe: before creating a record again,
     * the sync asks whether ServiceNow already has it (D9).
     */
    public Optional<Map<String, Object>> findByCorrelationId(String table, String correlationId) {
        return findOne(table, "correlation_id", correlationId);
    }

    // ---- reading what ServiceNow answers ----

    /** The sys_id of a record. */
    public static String sysId(Map<String, Object> record) {
        return valueOf(record.get("sys_id"));
    }

    /** A field as text. A reference field comes as an object with a "value" (the sys_id): this returns that value. */
    public static String valueOf(Object field) {
        if (field == null) {
            return null;
        }
        if (field instanceof Map<?, ?> map) {
            Object value = map.get("value");
            return value == null ? null : String.valueOf(value);
        }
        return String.valueOf(field);
    }

    // ---- internals ----

    private String call(Supplier<String> request) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            throw translate(e);
        } catch (ResourceAccessException e) {
            throw new ServiceNowException(ServiceNowException.Kind.UNREACHABLE, 0,
                    "ServiceNow did not answer (a free instance sleeps after a while: open it in a browser to wake it). " + e.getMessage(), e);
        }
    }

    private ServiceNowException translate(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        String said = serviceNowMessage(e.getResponseBodyAsString());
        ServiceNowException.Kind kind;
        String message;
        if (status == 401) {
            kind = ServiceNowException.Kind.UNAUTHORIZED;
            message = "ServiceNow refused the integration user (401): wrong password, locked, or it must reset its password. " + said;
        } else if (status == 403) {
            kind = ServiceNowException.Kind.FORBIDDEN;
            message = "The integration user is not allowed to do this (403): a role or an access rule is missing. " + said;
        } else if (status == 404) {
            kind = ServiceNowException.Kind.NOT_FOUND;
            message = "ServiceNow does not have that record or table (404). " + said;
        } else if (status >= 500 && said.contains(REJECTED_VALUE_MARKER)) {
            kind = ServiceNowException.Kind.REJECTED_VALUE;
            message = "ServiceNow refused a value. It answers HTTP " + status + " \"" + REJECTED_VALUE_MARKER + "\" for an unknown close code or caller:"
                    + " check that the resolution code is one of the ten allowed values and that the caller exists. ServiceNow said: " + said;
        } else if (status >= 500) {
            kind = ServiceNowException.Kind.SERVER_ERROR;
            message = "ServiceNow had an error (" + status + "). " + said;
        } else {
            kind = ServiceNowException.Kind.BAD_REQUEST;
            message = "ServiceNow refused the request (" + status + "). " + said;
        }
        return new ServiceNowException(kind, status, message, e);
    }

    /** The text of ServiceNow's error body: {"error":{"message":"...","detail":"..."},"status":"failure"}. */
    private String serviceNowMessage(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            JsonNode error = json.readTree(body).path("error");
            String message = error.path("message").asText("");
            String detail = error.path("detail").asText("");
            String both = detail.isBlank() ? message : message + " (" + detail + ")";
            return both.isBlank() ? body : both;
        } catch (JsonProcessingException e) {
            return body;
        }
    }

    private Map<String, Object> single(String body) {
        try {
            JsonNode result = json.readTree(body).path("result");
            return json.convertValue(result, new TypeReference<Map<String, Object>>() { });
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new ServiceNowException(ServiceNowException.Kind.SERVER_ERROR, 0, "ServiceNow answered something we cannot read: " + e.getMessage(), e);
        }
    }

    private List<Map<String, Object>> list(String body) {
        try {
            JsonNode result = json.readTree(body).path("result");
            return json.convertValue(result, new TypeReference<List<Map<String, Object>>>() { });
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new ServiceNowException(ServiceNowException.Kind.SERVER_ERROR, 0, "ServiceNow answered something we cannot read: " + e.getMessage(), e);
        }
    }
}
