# 12. Bean Validation (@Valid)

**Concept:** Spring MVC. `POST /api/products` takes a `ProductRequest` DTO whose rules are **declared** with annotations:

- **Built-in:** `@NotBlank`, `@NotNull`, `@Size`, `@Email`, `@Positive`, `@PositiveOrZero`, `@Digits`
- **Custom (the optional extension):** `@ValidSku` and `@DiscountLessThanPrice`

`@Valid` runs them all. One `@RestControllerAdvice` turns violations into JSON.

**There is not a single manual `if` check for input format** in the controller, service or DTO.

**Business rules** need the database, so they live in the service:
- the category must exist
- the price must not exceed the category ceiling
- the SKU must be unique

## Tech

Java 17 · Maven · **Spring Boot 3.5.6** (starter-web, **starter-validation** = Hibernate Validator 8, starter-jdbc) · MySQL 8

**Database:** shared Day 07 database `Day_007_Assigments`.
- **`a12_categories`** holds the business-rule data: category code and price ceiling.
- **`a12_products`** holds the saved products. `sku` is `UNIQUE`, and `category_code` is a foreign key to `a12_categories`.

## Structure

```text
12-bean-validation/
├── pom.xml
└── src/main/
    ├── java/com/company/boot/
    │   ├── BeanValidationApplication.java              @SpringBootApplication (run this)
    │   ├── controller/ProductController.java           POST (@Valid @RequestBody), GET with @Positive/@Size params
    │   ├── dto/
    │   │   ├── ProductRequest.java                     all constraints declared here
    │   │   ├── ProductResponse.java
    │   │   └── ErrorResponse.java                      list of {field, rejectedValue, message}
    │   ├── validation/                                 OPTIONAL EXTENSION - custom annotations
    │   │   ├── ValidSku.java + SkuValidator.java               field-level
    │   │   └── DiscountLessThanPrice.java + ...Validator.java  class-level (cross-field)
    │   ├── service/ProductService.java                 BUSINESS rules (need the database)
    │   ├── repository/ProductRepository.java           JdbcTemplate
    │   └── exception/
    │       ├── BusinessRuleException.java
    │       └── GlobalExceptionHandler.java             400 / 409 / 422 JSON
    └── resources/
        ├── application.properties
        ├── db-secret.properties                        password (git-ignored; copy db-secret.properties.example)
        └── schema.sql                                  a12_categories (+3 rows), a12_products
```

## The rules

| Field | Constraints |
|---|---|
| `name` | `@NotBlank`, `@Size(min = 3, max = 100)` |
| `sku` | `@NotBlank`, **`@ValidSku`** (custom: `XXX-99999`, prefix ELE/BOK/GRO) |
| `category` | `@NotBlank` |
| `price` | `@NotNull`, `@Positive`, `@Digits(integer = 8, fraction = 2)` |
| `discountPrice` | `@PositiveOrZero` (optional) |
| `stock` | `@NotNull`, `@PositiveOrZero` |
| `supplierEmail` | `@NotBlank`, `@Email` |
| `tags` | `@Size(max = 5)` on the list; **each element** `@NotBlank @Size(max = 20)` |
| *whole object* | **`@DiscountLessThanPrice`** (custom, class-level: `discountPrice < price`) |
| `GET ?category=` | `@Size(min = 3, max = 20)` directly on the `@RequestParam` |
| `GET ?maxPrice=` | `@Positive` directly on the `@RequestParam` |

The business rules are checked **only after** validation passes:

| Rule | Status |
|---|---|
| category exists in `a12_categories` | 422 |
| `price <= max_price` of that category | 422 |
| SKU not used yet | 409 |

## How to run

1. Run `src/main/resources/schema.sql` in MySQL. It recreates only the `a12_` tables; re-run it to reset the data.
2. **Eclipse:** **File → Import → Maven → Existing Maven Projects**, select `12-bean-validation`, then right-click `BeanValidationApplication` → **Run As → Java Application**.
3. **POST with Postman** to `http://localhost:8080/api/products` (Body → raw → JSON):

   ```json
   {
     "name": "Wireless Mouse", "sku": "ELE-10001", "category": "Electronics",
     "price": 799.00, "discountPrice": 699.00, "stock": 50,
     "supplierEmail": "sales@logitech.com", "tags": ["mouse", "wireless"]
   }
   ```

4. **Search** with `GET http://localhost:8080/api/products?category=ELECTRONICS&maxPrice=1000`.

## Observed output: valid payloads

| Request | Status | Response |
|---|---|---|
| the full JSON above | **201** `Location: /api/products/1` | `{"id":1,"name":"Wireless Mouse","sku":"ELE-10001","category":"ELECTRONICS","price":799.00,"discountPrice":699.00,"stock":50,"supplierEmail":"sales@logitech.com","tags":["mouse","wireless"],"createdAt":"2026-10-11T11:51:37"}` |
| `{"name":"Clean Code","sku":"BOK-20001","category":"books","price":450,"stock":0,"supplierEmail":"orders@pearson.com"}` | **201** `Location: /api/products/2` | `{"id":2,...,"category":"BOOKS","price":450.00,"discountPrice":null,"stock":0,...,"tags":[]}` |

In the second request:
- `discountPrice` and `tags` were omitted. They have no `@NotNull`, so `null` is valid.
- `stock: 0` passes `@PositiveOrZero`.

## Failure / edge-case evidence: invalid payloads

### Built-in constraints (400)

**`{}`** (empty object). **All 6** required-field violations are reported at once:

```json
{"timestamp":"2026-10-11T11:51:38","status":400,"error":"Bad Request","message":"Validation failed: 6 error(s)","errors":[
 {"field":"category","rejectedValue":null,"message":"category is required"},
 {"field":"name","rejectedValue":null,"message":"name is required"},
 {"field":"price","rejectedValue":null,"message":"price is required"},
 {"field":"sku","rejectedValue":null,"message":"sku is required"},
 {"field":"stock","rejectedValue":null,"message":"stock is required"},
 {"field":"supplierEmail","rejectedValue":null,"message":"supplierEmail is required"}]}
```

**`{"name":"TV",...,"price":-5,"stock":-1,"supplierEmail":"not-an-email"}`**: `@Size`, `@Positive`, `@PositiveOrZero` and `@Email` all fail together:

```json
"message":"Validation failed: 4 error(s)","errors":[
 {"field":"name","rejectedValue":"TV","message":"name must be between 3 and 100 characters"},
 {"field":"price","rejectedValue":-5,"message":"price must be greater than 0"},
 {"field":"stock","rejectedValue":-1,"message":"stock cannot be negative"},
 {"field":"supplierEmail","rejectedValue":"not-an-email","message":"supplierEmail must be a valid e-mail address"}]
```

| Payload detail | Status | Error |
|---|---|---|
| `"price":0` | **400** | `price` / `0` / `price must be greater than 0` (`@Positive` excludes 0) |
| `"price":12.999` | **400** | `price` / `12.999` / `price may have at most 2 decimal places` (`@Digits`) |
| `"tags":["a","","b","c","d","this-tag-is-far-too-long-to-accept"]` | **400** | 3 errors: `tags` / `at most 5 tags are allowed`, `tags[1]` / `tag must not be blank`, `tags[5]` / `tag must be at most 20 characters` (constraints on the list **and** on each element) |

`{min}`, `{max}` and `{fraction}` in the messages are filled in from the annotation attributes.

### Custom constraints (optional extension, 400)

| Payload detail | Status | Error |
|---|---|---|
| `"sku":"KB-1"` | **400** | `sku` / `KB-1` / `sku must look like XXX-99999 with prefix one of [ELE, BOK, GRO]` |
| `"sku":"XYZ-12345"` | **400** | `sku` / `XYZ-12345` / `sku must look like XXX-99999 with prefix one of [ELE, BOK, GRO]`. The format is right, but the prefix is not allowed. |
| `"price":1200,"discountPrice":1500` | **400** | `discountPrice` / `1500` / `discountPrice must be less than price` (class-level rule, reported on the `discountPrice` field) |

### Valid shape, but a business rule is broken (409 / 422)

These requests **pass `@Valid`**. The service rejects them using data from MySQL:

| Payload detail | Status | `message` |
|---|---|---|
| `"category":"Toys"` | **422** | `Unknown category 'TOYS'` |
| `"category":"Books","price":7500` | **422** | `Price 7500 exceeds the BOOKS category limit of 5000.00` |
| `"sku":"ELE-10001"` (already saved) | **409** | `SKU ELE-10001 is already registered` |

### Edge cases

| Request | Status | Observation |
|---|---|---|
| `"supplierEmail":"farm@local"` | **201** (saved as product 3) | Hibernate's `@Email` accepts an address **without a top-level domain**, because `user@localhost` is technically valid. For stricter checks, add `@Email(regexp = ".+@.+\\..+")` or a custom constraint. |
| `"price":"abc"` | **400** `Malformed JSON request` | Jackson cannot even create the DTO, so validation **never runs**. Type errors come before constraint errors. |
| `GET /api/products?category=ELECTRONICS&maxPrice=1000` | **200** | `[{"id":1,"name":"Wireless Mouse",...}]` |
| `GET /api/products?maxPrice=-10&category=X` | **400** | `"message":"Invalid request parameter(s)"` with `category` / `X` / `category must be 3-20 characters` and `maxPrice` / `-10` / `maxPrice must be greater than 0`. Constraints work on plain parameters too (Spring 6.1+ method validation → `HandlerMethodValidationException`). |

The table after the tests: only the 3 valid products were stored.

```text
+------------+----------------+-----------+---------------+--------+----------------+-------+--------------------+----------------+
| product_id | name           | sku       | category_code | price  | discount_price | stock | supplier_email     | tags           |
+------------+----------------+-----------+---------------+--------+----------------+-------+--------------------+----------------+
|          1 | Wireless Mouse | ELE-10001 | ELECTRONICS   | 799.00 |         699.00 |    50 | sales@logitech.com | mouse,wireless |
|          2 | Clean Code     | BOK-20001 | BOOKS         | 450.00 |           NULL |     0 | orders@pearson.com | NULL           |
|          3 | Rice 5kg       | GRO-30001 | GROCERY       | 450.00 |           NULL |    20 | farm@local         | NULL           |
+------------+----------------+-----------+---------------+--------+----------------+-------+--------------------+----------------+
```

## Explanation

**Declarative vs manual.** Without Bean Validation, every endpoint repeats checks like `if (name == null || name.isBlank()) ... if (price <= 0) ...`, each building its own error. Here the rules are **declared once on the DTO**:
- Hibernate Validator enforces them.
- They report every violation, not just the first.
- They are reused anywhere the DTO is used.
- `GlobalExceptionHandler` formats every violation the same way.

**`@Valid`** on a controller parameter tells Spring: *validate this object before calling the method*.
- **If anything fails, the method is not called.** `MethodArgumentNotValidException` is thrown, and the advice turns it into a 400.
- **`@Valid` also cascades** into nested objects and list elements.
- **Constraints placed directly on `@RequestParam`/`@PathVariable`** are checked by Spring 6.1+ method validation, giving `HandlerMethodValidationException`.

**Constraints** are annotations from `jakarta.validation.constraints`:

| Kind | Annotations |
|---|---|
| Presence | `@NotNull` (not null), `@NotEmpty` (not null/empty), `@NotBlank` (not null/empty/whitespace) |
| Numbers | `@Positive`, `@PositiveOrZero`, `@Min`, `@Max`, `@Digits` |
| Text and collections | `@Size`, `@Email`, `@Pattern` |

Apart from the `@Not...` ones, **constraints treat `null` as valid**, so a field is optional unless `@NotNull`/`@NotBlank` is added.

**Custom constraints** (optional extension) need an annotation (`@Constraint(validatedBy = ...)` plus `message`, `groups` and `payload`) and a `ConstraintValidator` class:
- `@ValidSku` is a field-level rule whose allowed prefixes are set by an attribute.
- `@DiscountLessThanPrice` is a **class-level** rule, needed because it compares **two fields**. Built-ins only see one field.

**Validation vs business rules:**

| Validation (annotations, 400) | Business rules (service code, 409/422) |
|---|---|
| Is the **request well-formed**? | Is the request **allowed** given the current state of the system? |
| Decided from the request **alone**: required, length, format, range, field-vs-field | Needs **data or state**: does the category exist, what is its price ceiling, is the SKU taken? |
| Stable, the same for every caller | Changes with data: edit `a12_categories.max_price` and the rule changes, with no code change |
| Runs first; failure means the service is never reached | Runs only after validation has passed |

Putting a database lookup inside a constraint annotation is possible, but it mixes the two concerns. Keeping them separate makes both easier to test and to change.
