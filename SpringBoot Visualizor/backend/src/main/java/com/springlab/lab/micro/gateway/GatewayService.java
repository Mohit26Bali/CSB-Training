package com.springlab.lab.micro.gateway;

import com.springlab.lab.micro.contract.GatewayContract;
import com.springlab.lab.micro.contract.GatewayContract.Ack;
import com.springlab.lab.micro.contract.GatewayContract.Credential;
import com.springlab.lab.micro.contract.GatewayContract.HttpRequest;
import com.springlab.lab.micro.contract.GatewayContract.HttpResponse;
import com.springlab.lab.micro.contract.GatewayContract.RateLimit;
import com.springlab.lab.micro.contract.OrderContract;
import com.springlab.lab.micro.contract.OrderContract.PlaceOrder;
import com.springlab.lab.micro.contract.ProductContract;
import com.springlab.lab.micro.contract.ProductContract.GetProduct;
import com.springlab.lab.micro.contract.UserContract;
import com.springlab.lab.micro.contract.UserContract.GetUser;
import com.springlab.lab.micro.core.CallContext;
import com.springlab.lab.micro.core.CallOptions;
import com.springlab.lab.micro.core.CallResult;
import com.springlab.lab.micro.core.HandlerRegistry;
import com.springlab.lab.micro.core.Outcome;
import com.springlab.lab.micro.core.ServiceModule;
import com.springlab.lab.micro.core.ServiceName;
import com.springlab.lab.micro.core.ServiceResult;
import com.springlab.lab.micro.run.MicroRunState;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The API Gateway: a SIMULATED, in-process model of what a gateway does for every request: (1) reject callers without a valid
 * credential, (2) limit each client to N requests per window, (3) find the route, (4) forward the call to the service through the
 * service bus, (5) translate a failure of the service into an HTTP-like status. It owns no data.
 *
 * <p>It is not a production gateway and not Spring Cloud Gateway: the route table is a few lines of code, the credential is a
 * lab state (no token is read or stored), and the rate limiter is a fixed window counter on the simulated clock.
 */
@Service
public class GatewayService implements ServiceModule {

    private static final Pattern USER = Pattern.compile("/users/(\\d{1,18})");
    private static final Pattern PRODUCT = Pattern.compile("/products/(\\d{1,18})");
    private static final int DOWNSTREAM_TIMEOUT_MILLIS = 200;

    /** The limiter state of ONE run: per client, the start of the current window and the requests counted in it. */
    static final class Limiter {
        int limit = 1_000_000;
        final Map<String, long[]> windows = new HashMap<>();
    }

    private final MicroRunState state;

    GatewayService(MicroRunState state) {
        this.state = state;
    }

    @Override
    public ServiceName service() {
        return ServiceName.GATEWAY;
    }

    @Override
    public void register(HandlerRegistry.Registrar registrar) {
        registrar.on(GatewayContract.ROUTE, this::route);
        registrar.on(GatewayContract.CONFIGURE_RATE_LIMIT, this::configure);
    }

    ServiceResult<Ack> configure(CallContext context, RateLimit request) {
        state.of("gateway.limiter", Limiter::new).limit = request.limit();
        return ServiceResult.ok(new Ack(true));
    }

    ServiceResult<HttpResponse> route(CallContext context, HttpRequest request) {
        if (request.credential() == Credential.MISSING) {
            return answer(401, "MISSING_CREDENTIALS");
        }
        if (request.credential() == Credential.EXPIRED) {
            return answer(401, "EXPIRED_CREDENTIALS");
        }
        Limiter limiter = state.of("gateway.limiter", Limiter::new);
        long now = context.bus().clock().now();
        long[] window = limiter.windows.computeIfAbsent(request.clientId(), k -> new long[] {now, 0});
        if (now - window[0] >= GatewayContract.WINDOW_MILLIS) {
            window[0] = now;
            window[1] = 0;
        }
        if (window[1] >= limiter.limit) {
            return answer(429, "RATE_LIMIT_EXCEEDED");
        }
        window[1]++;

        CallOptions options = CallOptions.timeout(DOWNSTREAM_TIMEOUT_MILLIS);
        Matcher user = USER.matcher(request.path());
        Matcher product = PRODUCT.matcher(request.path());
        if ("GET".equals(request.method()) && user.matches()) {
            return forward("user", context.bus().call(ServiceName.GATEWAY, UserContract.GET_USER, new GetUser(Long.parseLong(user.group(1))), options));
        }
        if ("GET".equals(request.method()) && product.matches()) {
            return forward("product", context.bus().call(ServiceName.GATEWAY, ProductContract.GET_PRODUCT, new GetProduct(Long.parseLong(product.group(1))), options));
        }
        if ("POST".equals(request.method()) && "/orders".equals(request.path()) && request.userRef() != null && request.productRef() != null
                && request.quantity() != null) {
            return forward("order", context.bus().call(ServiceName.GATEWAY, OrderContract.PLACE_ORDER,
                    PlaceOrder.simple(request.userRef(), request.productRef(), request.quantity()), options));
        }
        return answer(404, "NO_ROUTE");
    }

    private static ServiceResult<HttpResponse> answer(int status, String reason) {
        return ServiceResult.ok(new HttpResponse(status, reason, null, null));
    }

    /** Translates the outcome of the forwarded call into an HTTP-like status. A dependency failure inside the service keeps its meaning. */
    private static ServiceResult<HttpResponse> forward(String service, CallResult<?> result) {
        String downstream = result.outcome() + (result.code() == null ? "" : " (" + result.code() + ")");
        int status = switch (result.outcome()) {
            case SUCCESS -> 200;
            case BUSINESS_FAILURE -> businessStatus(result.code());
            case UNAVAILABLE, BULKHEAD_REJECTED, CIRCUIT_OPEN -> 503;
            case TIMEOUT -> 504;
            case TECHNICAL_ERROR -> 502;
        };
        String reason = result.outcome() == Outcome.SUCCESS ? "OK" : result.code() == null ? result.outcome().name() : result.code();
        return ServiceResult.ok(new HttpResponse(status, reason, service, downstream));
    }

    private static int businessStatus(String code) {
        if (code == null) {
            return 422;
        }
        if (code.endsWith("NOT_FOUND")) {
            return 404;
        }
        if (code.endsWith("_TIMEOUT")) {
            return 504;
        }
        if (code.endsWith("_UNAVAILABLE") || code.endsWith("_BULKHEAD_REJECTED")) {
            return 503;
        }
        if (code.endsWith("_TECHNICAL_ERROR")) {
            return 502;
        }
        return 422;
    }
}
