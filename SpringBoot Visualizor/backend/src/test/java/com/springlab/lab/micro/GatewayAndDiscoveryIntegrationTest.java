package com.springlab.lab.micro;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Topics 3 and 4 against real MySQL. */
class GatewayAndDiscoveryIntegrationTest extends AbstractMicroLabTest {

    // ---------------------------------------------------------------------------------------------------- topic 3

    @Test
    void thePublicRoutesReachTheirServicesAndAnythingElseIsAnsweredAtTheEdge() throws Exception {
        JsonNode run = run("gw-routing");
        assertThat(evidence(run, "route-user", "Response status")).isEqualTo("200 OK");
        assertThat(evidence(run, "route-user", "Forwarded to")).isEqualTo("user");
        assertThat(evidence(run, "route-user", "Services called behind the gateway")).isEqualTo("1");
        assertThat(evidence(run, "route-product", "Forwarded to")).isEqualTo("product");
        assertThat(evidence(run, "route-order", "Response status")).isEqualTo("200 OK");
        assertThat(evidence(run, "route-order", "Forwarded to")).isEqualTo("order");
        assertThat(evidence(run, "route-order", "Services called behind the gateway")).as("order, user, product, inventory").isEqualTo("4");

        assertThat(evidence(run, "route-internal", "Response status")).isEqualTo("404 NO_ROUTE");
        assertThat(evidence(run, "route-internal", "Services called behind the gateway")).isEqualTo("0");
        assertThat(evidence(run, "route-unknown", "Response status")).isEqualTo("404 NO_ROUTE");
        assertThat(evidence(run, "route-unknown", "Services called behind the gateway")).isEqualTo("0");
        assertThat(evidence(run, "seed", "Gateway")).contains("SIMULATED").contains("not a production gateway");
    }

    @Test
    void anUnauthenticatedRequestNeverReachesAService() throws Exception {
        JsonNode run = run("gw-auth");
        assertThat(evidence(run, "valid", "Response status")).isEqualTo("200 OK");
        assertThat(evidence(run, "missing", "Response status")).isEqualTo("401 MISSING_CREDENTIALS");
        assertThat(evidence(run, "missing", "Services called behind the gateway")).isEqualTo("0");
        assertThat(evidence(run, "expired", "Response status")).isEqualTo("401 EXPIRED_CREDENTIALS");
        assertThat(evidence(run, "expired", "Services called behind the gateway")).isEqualTo("0");
        assertThat(evidence(run, "services-protected", "Rejected requests reached no service")).isEqualTo("true");
        assertThat(evidence(run, "seed", "Credential model")).contains("not a real token");
    }

    @Test
    void theRateLimiterAcceptsTheLimitPerClientPerWindowAndThenRecovers() throws Exception {
        JsonNode run = run("gw-rate-limit");
        assertThat(evidence(run, "burst", "Statuses in order")).isEqualTo("200, 200, 200, 429, 429");
        assertThat(evidence(run, "burst", "Accepted (200)")).isEqualTo("3");
        assertThat(evidence(run, "burst", "Rejected (429)")).isEqualTo("2");
        assertThat(evidence(run, "other-client", "Other client status")).as("limits are per client").isEqualTo("200 OK");
        assertThat(evidence(run, "next-window", "First client again status")).isEqualTo("200 OK");
        assertThat(evidence(run, "check", "Limit applied correctly")).isEqualTo("true");
    }

    @Test
    void theRateLimitFollowsTheWhatIfValues() throws Exception {
        assertThat(evidence(run(student, "gw-rate-limit", "{\"limit\":1,\"requests\":4}"), "burst", "Statuses in order")).isEqualTo("200, 429, 429, 429");
        JsonNode roomy = run(student, "gw-rate-limit", "{\"limit\":10,\"requests\":5}");
        assertThat(evidence(roomy, "burst", "Statuses in order")).isEqualTo("200, 200, 200, 200, 200");
        assertThat(evidence(roomy, "check", "Limit applied correctly")).isEqualTo("true");
    }

    @Test
    void theGatewayTranslatesServiceFailuresIntoHttpLikeStatuses() throws Exception {
        assertThat(evidence(run(student, "gw-failure", "{\"fault\":\"service-down\"}"), "faulted", "Response status")).startsWith("503");
        assertThat(evidence(run(student, "gw-failure", "{\"fault\":\"service-slow\"}"), "faulted", "Response status")).startsWith("504");
        assertThat(evidence(run(student, "gw-failure", "{\"fault\":\"service-error\"}"), "faulted", "Response status")).startsWith("502");
    }

    @Test
    void aDownGatewayMakesHealthyServicesUnreachableForClients() throws Exception {
        JsonNode run = run(student, "gw-failure", "{\"fault\":\"gateway-down\"}");
        assertThat(evidence(run, "faulted", "Response status")).contains("UNAVAILABLE").startsWith("no answer");
        assertThat(evidenceStartingWith(run, "faulted", "Product service, called directly")).isEqualTo("SUCCESS");
        assertThat(evidence(run, "healthy", "Response status")).isEqualTo("200 OK");
    }

    // ---------------------------------------------------------------------------------------------------- topic 4

    @Test
    void instancesRegisterAreListedAndResolvedInTurnAndAnUnknownNameIsRefused() throws Exception {
        JsonNode run = run("disc-register-lookup");
        assertThat(evidence(run, "register", "Registry")).contains("SIMULATED").contains("not Eureka");
        assertThat(evidence(run, "list", "Instances of inventory")).contains("inventory-1 UP healthy").contains("inventory-2 UP healthy");
        assertThat(evidence(run, "resolve", "Instances returned, in order")).isEqualTo("inventory-1, inventory-2, inventory-1");
        assertThat(evidence(run, "unknown-service", "Caller sees")).isEqualTo("BUSINESS_FAILURE (UNKNOWN_SERVICE)");
        assertThat(step(run, "register").get("sql").toString()).contains("micro_lab_registry");
    }

    @Test
    void requestsAreSpreadEvenlyOverTheInstances() throws Exception {
        JsonNode run = run("disc-load-balancing");
        assertThat(evidence(run, "dispatch", "Requests served by inventory-1")).isEqualTo("2");
        assertThat(evidence(run, "dispatch", "Requests served by inventory-2")).isEqualTo("2");
        assertThat(evidence(run, "dispatch", "Requests served by inventory-3")).isEqualTo("2");
        assertThat(evidenceStartingWith(run, "dispatch", "Stock")).as("every request really reserved a unit").isEqualTo("0");
        assertThat(evidence(run, "balance", "Spread evenly (difference at most 1)")).isEqualTo("true");

        JsonNode uneven = run(student, "disc-load-balancing", "{\"instances\":2,\"requests\":5}");
        assertThat(evidence(uneven, "dispatch", "Requests served by inventory-1")).isEqualTo("3");
        assertThat(evidence(uneven, "dispatch", "Requests served by inventory-2")).isEqualTo("2");
        assertThat(evidence(uneven, "balance", "Spread evenly (difference at most 1)")).isEqualTo("true");
    }

    @Test
    void anInstanceThatReportsDownIsSkipped() throws Exception {
        JsonNode run = run("disc-unavailable");
        assertThat(evidence(run, "report-down", "Instances")).contains("inventory-1 DOWN NOT healthy").contains("inventory-2 UP healthy");
        assertThat(evidence(run, "resolve", "Results, in order")).isEqualTo("inventory-2, inventory-2, inventory-2");
        assertThat(evidence(run, "conclusion", "Healthy instances left")).isEqualTo("1");
    }

    @Test
    void whenNoInstanceIsHealthyTheRegistryRefusesAndTheCallerMustCope() throws Exception {
        JsonNode run = run(student, "disc-unavailable", "{\"instances\":2,\"unhealthy\":2}");
        assertThat(evidence(run, "resolve", "Results, in order"))
                .isEqualTo("BUSINESS_FAILURE (NO_HEALTHY_INSTANCE), BUSINESS_FAILURE (NO_HEALTHY_INSTANCE), BUSINESS_FAILURE (NO_HEALTHY_INSTANCE)");
        assertThat(evidence(run, "conclusion", "Registry answer when none is healthy")).contains("NO_HEALTHY_INSTANCE");
    }

    @Test
    void aStaleRegistrationKeepsRecommendingADeadInstanceUntilTheTtlPasses() throws Exception {
        JsonNode run = run("disc-stale-registration");
        assertThat(evidence(run, "crash", "Instances as the registry sees them")).as("the registry has not noticed").contains("inventory-2 UP healthy");
        assertThat(evidence(run, "stale-lookup", "Request 2 sent to inventory-2")).isEqualTo("UNAVAILABLE (CONNECTION_REFUSED)");
        assertThat(evidence(run, "stale-lookup", "Failures")).isEqualTo("2");
        assertThat(evidence(run, "wait-and-heartbeat", "Instances as the registry sees them")).contains("inventory-2 UP NOT healthy").contains("inventory-1 UP healthy");
        assertThat(evidence(run, "lookup-again", "Failures")).as("the dead instance is no longer recommended").isEqualTo("0");
        assertThat(evidence(run, "conclusion", "Dead instance still recommended after the wait")).isEqualTo("false");
    }

    @Test
    void ifTheClientDoesNotWaitLongEnoughTheDeadInstanceIsStillRecommended() throws Exception {
        for (String wait : new String[] {"0", "1000"}) {
            JsonNode run = run(student, "disc-stale-registration", "{\"waitMillis\":" + wait + "}");
            assertThat(evidence(run, "lookup-again", "Failures")).as("wait " + wait).isEqualTo("2");
            assertThat(evidence(run, "conclusion", "Dead instance still recommended after the wait")).isEqualTo("true");
        }
    }

    // ---------------------------------------------------------------------------------------------------- isolation

    @Test
    void registryRowsOfOneRunAreInvisibleToAnotherRunOfTheSameExperiment() throws Exception {
        String a = createRun(student, "disc-register-lookup").get("runId").asText();
        String b = createRun(student, "disc-register-lookup").get("runId").asText();
        JsonNode first = execute(student, a);
        JsonNode second = execute(student, b);
        assertThat(allEvidence(first)).isEqualTo(allEvidence(second));
        assertThat(labRows()).isZero();
    }
}
