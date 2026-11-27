package com.springlab.lab.micro.contract;

import com.springlab.lab.micro.core.Operation;
import com.springlab.lab.micro.core.ServiceName;

import java.util.List;

/**
 * The public contract of the (simulated, in-process) API Gateway. A request is described by method, path, a lab credential state
 * and a client id; the response carries an HTTP-like status. This is a teaching model of what a gateway does (authenticate, rate
 * limit, route, translate failures), not a production gateway and not Spring Cloud Gateway.
 */
public final class GatewayContract {

    private GatewayContract() {
    }

    public static final Operation<HttpRequest, HttpResponse> ROUTE = new Operation<>(ServiceName.GATEWAY, "route");
    /** Lab fixture: sets the per-client request limit per window (the window is {@link #WINDOW_MILLIS} simulated ms). */
    public static final Operation<RateLimit, Ack> CONFIGURE_RATE_LIMIT = new Operation<>(ServiceName.GATEWAY, "configureRateLimit");

    public static final int WINDOW_MILLIS = 1000;

    /** The public routes. Anything else, including the internal Inventory and Registry services, has no route. */
    public static final List<String> ROUTES = List.of("GET /users/{id} -> user.getUser", "GET /products/{id} -> product.getProduct",
            "POST /orders -> order.placeOrder");

    /** The state of the caller credential. The lab models it as a state: no real token, header or secret is used or recorded. */
    public enum Credential { VALID, MISSING, EXPIRED }

    public record HttpRequest(String method, String path, Credential credential, String clientId, Long userRef, Long productRef, Integer quantity) {
        public static HttpRequest get(String path, String clientId) {
            return new HttpRequest("GET", path, Credential.VALID, clientId, null, null, null);
        }

        public HttpRequest with(Credential c) {
            return new HttpRequest(method, path, c, clientId, userRef, productRef, quantity);
        }
    }

    /** {@code routedTo} is the service the gateway forwarded to, or null when it answered itself. {@code downstream} is that call outcome. */
    public record HttpResponse(int status, String reason, String routedTo, String downstream) {
    }

    public record RateLimit(int limit) {
    }

    public record Ack(boolean done) {
    }
}
