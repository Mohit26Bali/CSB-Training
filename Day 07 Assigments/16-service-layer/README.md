# 16. Service Layer Business Logic

**Concept:** Spring architecture. Requests flow **Controller → Service → Repository**, and **every business rule lives in the service**:
- price must be positive, with at most 2 decimals
- stock cannot be negative and cannot exceed capacity
- restock must be positive
- order quantity must be 1–10, and stock must be sufficient
- the price comes from the database, and the discount is calculated by the service

The controllers contain **no `if` at all**. Each layer logs its own step, so the flow is visible in the console.

The optional extension adds **service interfaces** (`ProductService`, `OrderService`) and **14 unit tests** of the implementations, with mocked repositories: no Spring context, no database.

## Tech

Java 17 · Maven · **Spring Boot 3.5.6** (starter-web, starter-data-jpa, starter-test for unit tests) · MySQL 8

**Database:** shared Day 07 database `Day_007_Assigments`:
- **`a16_products`**: seed data is 3 products.
- **`a16_orders`**: has a foreign key to products.

`schema.sql` has **no CHECK constraints** on price or stock on purpose, because the rules are the service's job.

## Structure

```text
16-service-layer/
├── pom.xml
└── src/
    ├── main/java/com/company/boot/
    │   ├── ServiceLayerApplication.java              @SpringBootApplication (run this)
    │   ├── controller/                               CONTROLLER LAYER - HTTP only, 0 if-statements
    │   │   ├── ProductController.java
    │   │   └── OrderController.java
    │   ├── service/                                  SERVICE LAYER - business rules
    │   │   ├── ProductService.java                   interface   (optional extension)
    │   │   ├── OrderService.java                     interface
    │   │   └── impl/
    │   │       ├── ProductServiceImpl.java           rules R1-R7
    │   │       └── OrderServiceImpl.java             rules O1-O7, @Transactional
    │   ├── repository/                               REPOSITORY LAYER - persistence only
    │   │   ├── ProductRepository.java
    │   │   └── OrderRepository.java
    │   ├── entity/Product.java, PurchaseOrder.java   plain data, no rules
    │   ├── dto/  CreateProductRequest, RestockRequest, PlaceOrderRequest, ProductResponse, OrderResponse, ErrorResponse
    │   └── exception/BusinessRuleException (+Type), ResourceNotFound, DuplicateResource, GlobalExceptionHandler
    ├── main/resources/application.properties, db-secret.properties (git-ignored), schema.sql
    └── test/java/com/company/boot/service/impl/      OPTIONAL EXTENSION - unit tests
        ├── ProductServiceImplTest.java               9 tests
        └── OrderServiceImplTest.java                 5 tests
```

## Each layer's responsibility

| Layer | Does | Does NOT |
|---|---|---|
| **Controller** (`@RestController`) | map URL + method, read the JSON body, call **one** service method, choose the success status (201/200) | check prices, stock or quantities; talk to the database |
| **Service** (`@Service`) | **all business rules**, calculations (price, discount, total), transactions (`@Transactional`), coordinating several repositories | know about HTTP (no `HttpStatus`, no `HttpServletRequest`) |
| **Repository** (`JpaRepository`) | load and store rows (generates SQL) | decide whether data is valid |
| **Web error handler** (`@RestControllerAdvice`) | translate exceptions into HTTP status + JSON (`INVALID_VALUE` → 400, `STATE_CONFLICT` → 422, not-found → 404, duplicate → 409) | contain rules |

Checked with a code search:
- `if (` statements in `controller/`: **0**
- `if (` statements in `service/impl/`: **12**
- imports of `org.springframework.http`/`.web`/`HttpServlet` in `service/` and `repository/`: **0**
- validation annotations (`@Valid`, `jakarta.validation`) anywhere: **0**. All checks are in the service.

### The rules (all in the service)

| Code | Rule | Broken → |
|---|---|---|
| R1 | name required, ≤ 100 chars | 400 `NAME_REQUIRED` / `NAME_TOO_LONG` |
| R2 | name unique (case-insensitive) | 409 `NAME_MUST_BE_UNIQUE` |
| R3 | price required and > 0 | 400 `PRICE_MUST_BE_POSITIVE` |
| R4 | price ≤ 2 decimals | 400 `PRICE_MAX_TWO_DECIMALS` |
| R5 | stock required and ≥ 0 | 400 `STOCK_CANNOT_BE_NEGATIVE` |
| R6 | restock quantity > 0 | 400 `RESTOCK_QUANTITY_MUST_BE_POSITIVE` |
| R7 | stock ≤ 10 000 (warehouse capacity) | 422 `STOCK_CAPACITY_EXCEEDED` |
| O1 | productId required | 400 `PRODUCT_ID_REQUIRED` |
| O2 | 1 ≤ quantity ≤ 10 per order | 400 `QUANTITY_OUT_OF_RANGE` |
| O3 | product exists | 404 |
| O4 | enough stock | 422 `INSUFFICIENT_STOCK` |
| O5 | unit price taken from the database, never from the client | – |
| O6 | 10 % discount when gross ≥ 5000 | – |
| O7 | stock decrease + order insert in **one** transaction | – |

## How to run

1. Run `src/main/resources/schema.sql` in MySQL. It recreates both `a16_` tables with 3 products; re-run it to reset.
2. **Eclipse:** **File → Import → Maven → Existing Maven Projects**, select `16-service-layer`, then right-click `ServiceLayerApplication` → **Run As → Java Application**.
3. **In Postman:**
   - `POST http://localhost:8080/api/products` with `{"name":"Webcam 1080p","price":2499.00,"stock":15}`
   - `POST http://localhost:8080/api/orders` with `{"productId":2,"quantity":2}`
   - `POST http://localhost:8080/api/products/1/restock` with `{"quantity":100}`
   - `POST http://localhost:8080/api/products/import` with a JSON array of products
   - `GET http://localhost:8080/api/products`
4. **Watch the Console.** Every request prints its flow through `CONTROLLER` → `SERVICE` → `SQL` (repository).
5. **Unit tests (optional extension):** right-click `src/test/java` (or one test class) → **Run As → JUnit Test**. From a terminal, `mvn test`. No database or server is needed.

## Observed output: the request flow

### Create a product → 201

```text
POST /api/products  {"name":"Webcam 1080p","price":2499.00,"stock":15}   -> 201
{"id":4,"name":"Webcam 1080p","price":2499.00,"stock":15}

12:15:52.016 INFO  CONTROLLER     : POST /api/products received -> delegating to ProductService.create()
12:15:52.045 INFO  SERVICE        : create product: checking rules R1-R5 and R7
12:15:52.292 DEBUG SQL            : select p1_0.product_id from a16_products p1_0 where upper(p1_0.name)=upper(?) limit ?
12:15:52.342 INFO  SERVICE        : all rules passed -> calling repository.save()
12:15:52.397 DEBUG SQL            : insert into a16_products (name,price,stock) values (?,?,?)
12:15:52.433 INFO  SERVICE        : product saved with id 4
12:15:52.457 INFO  CONTROLLER     : returning 201 Created
```

### Place an order: the service calculates price, discount and stock

```text
POST /api/orders  {"productId":2,"quantity":2}   -> 201
{"orderId":2,"productId":2,"productName":"Mechanical Keyboard","quantity":2,"unitPrice":3499.00,"discount":699.80,"total":6298.20,"remainingStock":3,"createdAt":"2026-10-11T12:15:53"}

12:15:53.662 INFO  CONTROLLER     : POST /api/orders received -> delegating to OrderService.placeOrder()
12:15:53.670 INFO  SERVICE        : placeOrder: checking rules O1-O4
12:15:53.672 DEBUG SQL            : select p1_0.product_id,p1_0.name,p1_0.price,p1_0.stock from a16_products p1_0 where p1_0.product_id=?
12:15:53.674 INFO  SERVICE        : rules passed: gross=6998.00 discount=699.80 total=6298.20
12:15:53.676 DEBUG SQL            : insert into a16_orders (created_at,discount,product_id,quantity,total,unit_price) values (?,?,?,?,?,?)
12:15:53.681 INFO  SERVICE        : order 2 saved, stock of product 2 now 3
12:15:53.683 DEBUG SQL            : update a16_products set name=?,price=?,stock=? where product_id=?     <- same transaction (O7)
12:15:53.691 INFO  CONTROLLER     : returning 201 Created
```

`POST /api/orders {"productId":1,"quantity":3}` gives **201** with `"unitPrice":799.00,"discount":0.00,"total":2397.00,"remainingStock":47`. The gross of 2397 is below 5000, so no discount was applied.

### Restock → 200

```text
POST /api/products/1/restock  {"quantity":100}   -> 200
{"id":1,"name":"Wireless Mouse","price":799.00,"stock":147}

CONTROLLER : POST /api/products/1/restock received -> ProductService.restock()
SERVICE    : restock product 1 by 100: checking rules R6, R7
SQL        : select ... from a16_products p1_0 where p1_0.product_id=?
SERVICE    : rules passed -> stock 47 -> 147
SQL        : update a16_products set name=?,price=?,stock=? where product_id=?
```

## Failure / edge-case evidence: rules enforced by the service

All error bodies have the form `{"timestamp":...,"status":...,"error":...,"rule":...,"message":...,"path":...}`.

| Request | Status | `rule` / `message` | Flow (where it stopped) |
|---|---|---|---|
| `POST /api/products` `{"name":"Free Thing","price":0,"stock":5}` | **400** | `PRICE_MUST_BE_POSITIVE` / `Price must be greater than 0 (got 0)` | CONTROLLER → SERVICE → **no SQL** |
| `... "price":-10 ...` | **400** | `PRICE_MUST_BE_POSITIVE` / `... (got -10)` | no SQL |
| `... "price":9.999 ...` | **400** | `PRICE_MAX_TWO_DECIMALS` / `Price may have at most 2 decimal places (got 9.999)` | no SQL |
| `... "stock":-1 ...` | **400** | `STOCK_CANNOT_BE_NEGATIVE` / `Stock must be 0 or more (got -1)` | no SQL |
| `{"price":100,"stock":1}` (no name) | **400** | `NAME_REQUIRED` / `Product name is required` | no SQL |
| `{"name":"wireless MOUSE",...}` | **409** | `NAME_MUST_BE_UNIQUE` / `A product named 'wireless MOUSE' already exists` | 1 select, no insert |
| `POST /api/products/1/restock` `{"quantity":0}` | **400** | `RESTOCK_QUANTITY_MUST_BE_POSITIVE` / `Restock quantity must be greater than 0 (got 0)` | no SQL |
| `POST /api/products/1/restock` `{"quantity":20000}` | **422** | `STOCK_CAPACITY_EXCEEDED` / `Stock would become 20147, above the warehouse capacity of 10000` | 1 select, no update |
| `POST /api/orders` `{"productId":1,"quantity":0}` | **400** | `QUANTITY_OUT_OF_RANGE` / `Quantity must be between 1 and 10 (got 0)` | no SQL |
| `... "quantity":11` | **400** | `QUANTITY_OUT_OF_RANGE` / `... (got 11)` | no SQL |
| `{"productId":3,"quantity":5}` (only 2 monitors) | **422** | `INSUFFICIENT_STOCK` / `Only 2 unit(s) of '27-inch Monitor' in stock, 5 requested` | 1 select, **no insert/update** |
| `{"productId":99,"quantity":1}` | **404** | `Product not found with id 99` | 1 select |
| `{"quantity":1}` (no productId) | **400** | `PRODUCT_ID_REQUIRED` / `productId is required` | no SQL |

Example of the flow stopping in the service:

```text
12:15:54.701 INFO  CONTROLLER     : POST /api/products received -> delegating to ProductService.create()
12:15:54.710 INFO  SERVICE        : create product: checking rules R1-R5 and R7
12:15:54.719 INFO  WEB-ERROR      : rule PRICE_MUST_BE_POSITIVE broken -> 400
```

The repository is never reached, and the controller's "returning 201" line never appears.

**Edge case: the client tries to set the price.** The request was `POST /api/orders {"productId":2,"quantity":1,"unitPrice":1.00,"total":1.00}`. It got **201**, `"unitPrice":3499.00,"total":3499.00`. The extra fields were ignored, because `PlaceOrderRequest` has no such fields and rule O5 takes the price from the database.

### The same rules from a second entry point (bulk import)

`POST /api/products/import` calls the **same** `create()` method for each item, so no rule is written twice:

```text
request: [{"name":"Desk Lamp","price":999,"stock":10},{"name":"Broken Lamp","price":-1,"stock":1},
          {"name":"Desk Lamp","price":500,"stock":1},{"name":"Cable","price":149.5,"stock":-3},{"name":"USB Hub","price":1299,"stock":20}]
-> 200
["item 0: created id 5",
 "item 1: REJECTED PRICE_MUST_BE_POSITIVE - Price must be greater than 0 (got -1)",
 "item 2: REJECTED NAME_MUST_BE_UNIQUE - A product named 'Desk Lamp' already exists",
 "item 3: REJECTED STOCK_CANNOT_BE_NEGATIVE - Stock must be 0 or more (got -3)",
 "item 4: created id 6"]
```

If the price check had been written in `ProductController.create()`, this import endpoint would have skipped it. Item 1 would have been saved with a negative price.

### Database afterwards

```text
+------------+---------------------+----------+-------+        +----------+------------+----------+------------+----------+---------+
| product_id | name                | price    | stock |        | order_id | product_id | quantity | unit_price | discount | total   |
|          1 | Wireless Mouse      |   799.00 |   147 |        |        1 |          1 |        3 |     799.00 |     0.00 | 2397.00 |
|          2 | Mechanical Keyboard |  3499.00 |     2 |        |        2 |          2 |        2 |    3499.00 |   699.80 | 6298.20 |
|          3 | 27-inch Monitor     | 15999.00 |     2 |        |        3 |          2 |        1 |    3499.00 |     0.00 | 3499.00 |
|          4 | Webcam 1080p        |  2499.00 |    15 |        +----------+------------+----------+------------+----------+---------+
|          5 | Desk Lamp           |   999.00 |    10 |
|          6 | USB Hub             |  1299.00 |    20 |
+------------+---------------------+----------+-------+
```

There are no invalid products, monitor stock is still 2 after the rejected order, and the keyboard stock went 5 → 3 → 2, matching its two orders.

## Optional extension: service interfaces + unit tests

- **Interfaces.** The controllers depend on `ProductService` / `OrderService` (interfaces). Spring injects the `...Impl` classes, and a test or a different implementation can replace them.
- **Unit tests** use JUnit 5 + Mockito + AssertJ. The repositories are **mocks**, so the tests run **without Spring, MySQL or HTTP**. That is only possible because the rules are in the service.

| Test | Checks |
|---|---|
| `createSavesProductWhenAllRulesPass` | saved, name trimmed |
| `createRejectsZeroOrNegativePrice` | `PRICE_MUST_BE_POSITIVE` for 0 and -10; `save()` **never called** |
| `createRejectsPriceWithThreeDecimals` | `PRICE_MAX_TWO_DECIMALS` |
| `createRejectsNegativeStock` | `STOCK_CANNOT_BE_NEGATIVE` |
| `createRejectsBlankName` | `NAME_REQUIRED` |
| `createRejectsDuplicateName` | `DuplicateResourceException`; `save()` never called |
| `restockRejectsNonPositiveQuantity` | `RESTOCK_QUANTITY_MUST_BE_POSITIVE` |
| `restockRejectsGoingAboveCapacity` | `STOCK_CAPACITY_EXCEEDED`; stock unchanged |
| `importAppliesTheSameRulesPerItem` | the good item is created, the bad item is rejected with its rule |
| `placeOrderWithoutDiscountDecreasesStock` | total 3499.00, discount 0, stock 5 → 4 |
| `placeOrderOf5000OrMoreGets10PercentDiscount` | 2 × 3499 → discount 699.80, total 6298.20 |
| `placeOrderRejectsQuantityOutsideOneToTen` | 0, -3, 11, null rejected; repository **never called** |
| `placeOrderRejectsInsufficientStockAndChangesNothing` | `INSUFFICIENT_STOCK`; stock unchanged; no order saved |
| `placeOrderForUnknownProductThrowsNotFound` | `Product not found with id 99` |

Output of `mvn package`, which runs the tests:

```text
Running com.company.boot.service.impl.OrderServiceImplTest
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.930 s -- in com.company.boot.service.impl.OrderServiceImplTest
Running com.company.boot.service.impl.ProductServiceImplTest
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.145 s -- in com.company.boot.service.impl.ProductServiceImplTest
Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Explanation

**Separation of concerns** means each layer has **one reason to change**:
- The **controller** changes when the HTTP API changes: URLs, JSON shape, status codes.
- The **service** changes when the **business** changes: a new discount, a different capacity, a new rule.
- The **repository** changes when **storage** changes: tables, queries, even another database.

Keeping these apart means a change in one place does not ripple through the others, and each part can be read, tested and replaced on its own.

**Why business rules belong in the service, not the controller:**
1. **One place, every entry point.** The REST endpoint, the bulk import, and later a scheduled job, a message listener or another service all call the service. Rules in a controller protect only that one endpoint. The import demo shows this.
2. **Transactions.** "Decrease stock **and** insert order" must succeed or fail together. `@Transactional` belongs on the service method that coordinates both repositories (O7).
3. **Testability.** The service is plain Java with injected interfaces, so 14 rule tests run in about 3 seconds with mocks. Testing rules inside a controller needs a web layer (MockMvc) or a running server.
4. **No HTTP in business code.** The service throws `BusinessRuleException` with a `Type` (`INVALID_VALUE` / `STATE_CONFLICT`). Only the web layer turns that into 400/422. The same service could serve a CLI or a message queue unchanged.
5. **Trust.** The client sends only *what* it wants (`productId`, `quantity`). The service decides *how much it costs*, so a client cannot set its own price (the edge case above).

The controller still does the HTTP work: reading the body, path variables, and choosing 201 Created + `Location`. That is **its** concern.

Format-only checks such as `@NotBlank` or `@Positive` on a DTO (Assignment 12) are fine as a first filter at the edge. Rules that need data or that every caller must obey (uniqueness, stock, capacity, prices) belong in the service.
