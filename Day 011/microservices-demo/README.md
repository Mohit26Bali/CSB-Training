# Order Management Microservices (Spring Cloud)

A small Order Management system built with Spring Boot and Spring Cloud. It shows service discovery (Eureka), client-side load balancing (Spring Cloud LoadBalancer), an API Gateway (Spring Cloud Gateway), fault tolerance with Resilience4J circuit breakers and fallbacks, Actuator, structured logging, Docker Compose and Kubernetes.

## Architecture

| Component | Responsibility | Port |
|---|---|---|
| `eureka-server` | Service registry / discovery | 8761 |
| `api-gateway` | Single entry point, routing, filters, gateway circuit breaker | 8080 |
| `order-service` | Creates and retrieves orders; calls Product Service by its logical name | 8081 |
| `product-service` | Product information | 8082 |
| `product-service` (instance 2) | Same application, second instance for load balancing | 8083 |

```text
Client ──► API Gateway :8080 ──lb://order-service──► Order Service :8081 ──http://product-service──► Product Service :8082 / :8083
                 │                                         │  @LoadBalanced RestClient + @CircuitBreaker
                 └──lb://product-service (CircuitBreaker filter) ──────────────────────────────────────►
All services register with / look up instances in Eureka :8761
```

## Versions

| | Version |
|---|---|
| Java | 17 |
| Spring Boot | 3.5.6 |
| Spring Cloud | **2025.0.3** (the release train compatible with Spring Boot 3.5.x; the 2025.1 / 2026.0 trains need Spring Boot 4) |
| Resilience4J | from the Spring Cloud CircuitBreaker starter |

Dependencies per module:
- **eureka-server:** Eureka Server.
- **product-service:** Web, Actuator, Eureka Client.
- **order-service:** Web, Actuator, AOP, Eureka Client, LoadBalancer, CircuitBreaker Resilience4J.
- **api-gateway:** Gateway (WebFlux), Actuator, Eureka Client, LoadBalancer, CircuitBreaker Reactor Resilience4J.

## Project structure

```text
microservices-demo/
├── pom.xml                 parent: Boot 3.5.6, Spring Cloud BOM, the four modules
├── eureka-server/          @EnableEurekaServer
├── product-service/        ProductController: GET /products/{id} (includes "instance")
├── order-service/          OrderController, ProductClient (@LoadBalanced RestClient + @CircuitBreaker), ClientConfig
├── api-gateway/            routes and filters in application.yml, FallbackController
├── docker-compose.yml      all services + 2 product instances
├── k8s/microservices.yaml  Kubernetes Deployments and Services (Eureka replaced by Kubernetes Services)
└── postman/                Postman collection (4 scenario folders)
```

Each module has a `Dockerfile`. Each client module has an `application-kubernetes.yml` profile.

> Verification note: the 12 unit and integration tests pass (`mvn test`), `docker compose config` and the Kubernetes manifest parse cleanly, but the container images and the cluster deployment were not built or run when this repo was prepared (no Docker daemon was available).

## Running locally

1. **Build.** Run `mvn clean package` in `microservices-demo`. This builds all modules and runs the tests.
2. **Start the services in this order.** In Eclipse, import as *Existing Maven Projects* and run each `*Application` class. Or use the terminal:

   ```bash
   java -jar eureka-server/target/eureka-server.jar
   java -jar product-service/target/product-service.jar                     # 8082
   java -jar product-service/target/product-service.jar --server.port=8083  # second instance
   java -jar order-service/target/order-service.jar
   java -jar api-gateway/target/api-gateway.jar
   ```

   In Eclipse, start the second product instance from a copy of the run configuration with the program argument `--server.port=8083`.
3. **Open the Eureka dashboard** at http://localhost:8761. After about 30 seconds it lists API-GATEWAY, ORDER-SERVICE and PRODUCT-SERVICE (2 instances).
4. **Call everything through the gateway:**

   ```bash
   curl http://localhost:8080/products/1
   curl http://localhost:8080/api/products/2
   curl -X POST http://localhost:8080/orders -H "Content-Type: application/json" -d "{\"productId\":1,\"quantity\":2}"
   curl http://localhost:8080/orders/1
   ```

If port 8081 is already used on your machine (for example by Jenkins), start order-service with `--server.port=8091`. The gateway finds it through Eureka, so nothing else changes.

## How each part is implemented

| Step | Implementation |
|---|---|
| 1. Eureka Server + register Product Service | `@EnableEurekaServer`; it does not register itself (`register-with-eureka: false`, `fetch-registry: false`). Clients point at `eureka.client.service-url.defaultZone` |
| 2. Two Product Service instances | the same jar on 8082 and 8083 with the same `spring.application.name: product-service`, so both register under one service ID. Each response carries `instance` (the port locally, the container or pod name in Docker / Kubernetes) |
| 3. Order Service calls Product Service by logical name | `ProductClient` calls `http://product-service/products/{id}`. No host or port is hard-coded |
| 4. Spring Cloud LoadBalancer | `ClientConfig` defines a `@LoadBalanced RestClient.Builder`. LoadBalancer resolves `product-service` to one registered instance per request (round robin) |
| 5. Gateway routes | `/products/**` → `lb://product-service`, `/orders/**` → `lb://order-service`. The `lb://` scheme makes the Gateway use LoadBalancer |
| 6. Gateway filters and path rewrite | `RewritePath=/api/products/(?<segment>.*), /products/${segment}`; `StripPrefix=1` for `/api/orders/**`; `AddRequestHeader X-Request-Source` (order routes); default filter `AddResponseHeader X-Gateway` |
| 7. Resilience4J at the Order Service | `@CircuitBreaker(name = "productService", fallbackMethod = "productFallback")` on `ProductClient.getProduct`. The fallback returns `Product(id, "Product temporarily unavailable", 0)`, and the order is saved with status `PENDING` instead of `CREATED` |
| 8. Gateway circuit breaker + fallback | a `CircuitBreaker` filter (`productCircuitBreaker`) on the product routes with `fallbackUri: forward:/fallback/products`. `FallbackController` returns **503** `{"message":"Product service is temporarily unavailable"}` |
| 9. Simulate failure | stop the product instances (see *Testing the circuit breaker* below) |
| 10. Actuator, metrics, structured logging | each service exposes `health`, `info`, `metrics`; order-service also exposes `circuitbreakers` and `circuitbreakerevents`, and the gateway exposes `circuitbreakers`. Console logs stay readable; the log file `logs/<service>.log` is **structured JSON (ECS format)** via `logging.structured.format.file: ecs` |
| 11. Docker Compose | `docker-compose.yml` (see below) |
| 12. Kubernetes | `k8s/microservices.yaml` (see below) |

**Circuit breaker settings.** These are the brief's training values, used for both `productService` and `productCircuitBreaker`: COUNT_BASED window 10, failure threshold 50%, at least 5 calls, 10 s open, 3 calls in half-open. `automaticTransitionFromOpenToHalfOpenEnabled` is on, so the state shows HALF_OPEN after the 10 s wait.

**Timeouts.**
- *Order-service:* the call is synchronous, so the timeout is set on the HTTP client: 2 s connect and 2 s read in `ClientConfig`. A Resilience4J `timelimiter` applies only to asynchronous (`CompletableFuture`) methods, so it would have no effect on this call.
- *Gateway:* the gateway route is reactive, so its `timelimiter` (2 s) does apply.

**Discovery tuned for the demo.** Shorter heartbeat and lease (5 s / 10 s), registry fetch every 5 s, LoadBalancer cache 5 s, and self-preservation off on the server. A stopped instance therefore drops out within about 30 s instead of minutes. Instances register by IP address (`prefer-ip-address: true`). Two reasons:
- the gateway's Netty DNS resolver could not resolve this PC's host name, so every gateway call failed until this was set;
- container host names are not resolvable from outside a container.

### OpenFeign

OpenFeign is optional in the brief. The `@LoadBalanced RestClient` already shows calls by logical service name, so Feign was not added. A Feign client would replace `ProductClient` with an interface annotated `@FeignClient(name = "product-service")`, and the same discovery and load balancing would resolve the name.

### Retry vs circuit breaker (why the gateway has no Retry filter)

A gateway `Retry` filter was tried on the product route. It did not help when an instance had been killed: after `lb://` is resolved, the request URL is already the dead instance's address, so the retry goes to the same instance. Retries are also only safe for idempotent requests (GET, not `POST /orders`). The circuit breaker plus fallback handles the failure, and the stopped instance drops out of discovery within about 30 s.

## API

| Method | Path (through the gateway, 8080) | Result |
|---|---|---|
| GET | `/products/{id}` | `{"id":1,"name":"Laptop","price":75000,"instance":"8082"}` |
| GET | `/api/products/{id}` | same, path rewritten to `/products/{id}` |
| POST | `/orders` body `{"productId":1,"quantity":2}` | **201** order with the product, `total`, `status` `CREATED` (or `PENDING` with the fallback product) |
| GET | `/orders`, `/orders/{id}` (also `/api/orders/...`) | list / one order; unknown id **404** |
| POST | `/orders` without `productId` | **400** |

Products 1–3 are Laptop, Mouse and Keyboard. Other ids return "Product N". Orders are kept in memory.

## Testing the load balancer

With 8082 and 8083 running, call `GET http://localhost:8080/products/1` repeatedly. The `instance` alternates: 8083, 8082, 8083, 8082.

Then stop 8083:
- With Ctrl+C (graceful), it deregisters at once.
- If the process is killed, Eureka removes it when its lease expires (about 30 s with the settings above). During that time, about half of the calls go to the dead instance and get the gateway fallback.
- Afterwards every request is served by 8082.

## Testing the circuit breaker

1. All services running: `GET http://localhost:8081/actuator/circuitbreakers` shows `productService` **CLOSED**.
2. Stop both product instances.
3. Send 5 or more `POST /orders`. Each order is still accepted (201), with status `PENDING` and the product "Product temporarily unavailable". After 5 failures the failure rate is 100% (above 50%), and the state is **OPEN**. Further calls are short-circuited: Product Service is not called at all.
4. `GET http://localhost:8080/products/1` returns **503** with the gateway fallback message.
5. After 10 s the state becomes **HALF_OPEN**. Restart the product instances and send orders again. After 3 successful trial calls the state is back to **CLOSED**, and the orders are `CREATED`.

The state changes recorded during the verification run (`/actuator/circuitbreakerevents`):

```text
14:07:45 STATE_TRANSITION CLOSED_TO_OPEN
14:07:55 STATE_TRANSITION OPEN_TO_HALF_OPEN
14:08:38 STATE_TRANSITION HALF_OPEN_TO_CLOSED
```

Order-service log while Product Service was down, and a line of the structured (ECS JSON) log file:

```text
WARN ... com.example.order.ProductClient : Product service unavailable for product 1: org.springframework.web.client.ResourceAccessException: I/O error ...
{"@timestamp":"...","log":{"level":"INFO","logger":"com.example.order.OrderController"},"service":{"name":"order-service",...},"message":"Order 10 CREATED for product 2 (product instance 8083)",...}
```

## Docker Compose

```bash
mvn clean package -DskipTests
docker compose up -d --build
```

- **Services:** Eureka, 2 product containers (`product-service-1`, `product-service-2`, same image), order-service and the gateway.
- **Published ports:** only the gateway (8080) and Eureka (8761). Order-service is published on `127.0.0.1:18081` for the Actuator endpoints only.
- **How containers find each other:** they reach Eureka at `http://eureka-server:8761/eureka/` (environment variable `EUREKA_URL`).

**To stop:**
- `docker compose stop product-service-1 product-service-2` simulates the failure.
- `docker compose down` removes everything.

## Kubernetes

Docker Desktop's Kubernetes was used. The images from `docker compose build` are used directly (`imagePullPolicy: Never`).

```bash
docker compose build
kubectl apply -f k8s/microservices.yaml
kubectl -n microservices-demo get pods
curl http://localhost:30080/products/1
```

**Eureka is replaced by Kubernetes Service discovery.** The `kubernetes` profile (`SPRING_PROFILES_ACTIVE=kubernetes`) switches the Eureka client off and maps the logical names to Kubernetes Service DNS names:

```yaml
spring.cloud.discovery.client.simple.instances:
  product-service: [{ uri: http://product-service:8082 }]
```

- **Unchanged code:** `lb://product-service` and `http://product-service/...` still work, so the application code does not change.
- **Load balancing:** the Kubernetes Service spreads connections over the 2 product pods (kube-proxy picks one at random for each new connection). The gateway's connection pool is disabled in this profile, and order-service runs with `-Dhttp.keepAlive=false`. Every call is then a new connection, so the spreading is visible.
- **Health probes:** readiness and liveness probes use `/actuator/health/readiness` and `/actuator/health/liveness`.
- **Entry point:** the gateway is the only externally reachable Service (NodePort 30080).

**Failure test:**
- `kubectl -n microservices-demo scale deploy product-service --replicas=0` → orders become `PENDING` and the gateway returns 503.
- `--replicas=2` → orders are `CREATED` again.

**Remove it:** `kubectl delete namespace microservices-demo`.

## Tests

`mvn test` runs **12 tests**. They need neither Eureka nor the other services, because small `HttpServer` stubs stand in for downstream services, registered through Spring Cloud's simple discovery client:

| Module | Test | Checks |
|---|---|---|
| eureka-server | `EurekaServerApplicationTests` | the registry endpoint `/eureka/apps` is up |
| product-service | `ProductControllerTest` | product JSON includes `instance`; unknown id gets a generic product |
| order-service | `OrderServiceTests` | order via the logical name (`CREATED`, total); **both instances used** by the load balancer; get by id / list; 400 and 404 |
| order-service | `OrderCircuitBreakerTests` | product service down → fallback product and `PENDING`; after 5 failures the breaker is **OPEN**; the next call is not permitted and still gets the fallback |
| api-gateway | `GatewayRoutesTests` | `/products/**` routed with the `X-Gateway` header; `/api/products/2` rewritten to `/products/2`; `/orders` and `/api/orders/1` get `X-Request-Source` |
| api-gateway | `GatewayFallbackTests` | product service down → **503** fallback message |

## Postman

`postman/microservices-demo.postman_collection.json` has 4 folders. Each one is run while the system is in that state:

| Folder | System state | Result (local run) |
|---|---|---|
| 1 All services running | everything up, 2 product instances | 15 requests, **30/30** assertions |
| 2 Product instance 8083 stopped | 8083 stopped, about 30 s later | **8/8**: all requests served by 8082 |
| 3 All product instances stopped | both product instances stopped | **22/22**: fallback orders `PENDING`, breaker OPEN, gateway 503 |
| 4 Product service restarted | instances restarted, about 45 s later | **8/8**: orders `CREATED`, breaker CLOSED again |

```bash
postman collection run postman/microservices-demo.postman_collection.json -i "1 All services running"
```

For the Docker run, add `--env-var order=http://localhost:18081`. In Docker, folders 1, 3 and 4 passed (30/30, 22/22 and 8/8). Folder 2 checks for port 8082, so it applies to the local run only.

## Production notes

These are demo settings:
- one Eureka server
- HTTP without authentication
- in-memory orders
- short heartbeat and circuit-breaker timings

In production you would run several registry nodes (or the platform's discovery), use TLS and authentication at the gateway, keep internal service ports private, tune timeouts and breaker thresholds from real measurements, use retries only for idempotent calls, and ship metrics, logs and trace IDs to an observability stack.
