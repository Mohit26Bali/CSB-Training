# Exercise 2: E-Commerce Product REST API

A REST API for the products of an online store. It covers:
- full **CRUD**
- **filtering** by category and price range
- **pagination** (`page`, `size`) and **sorting** (`sortBy`, `direction`)
- **validation**
- a custom **`ProductNotFoundException`**
- a **`@RestControllerAdvice`** returning one standard error structure

All three challenge extensions are included:
- search by product name
- `PATCH` for the inventory quantity
- **optimistic locking with `@Version`**

## Tech

Java 17 · Maven · Spring Boot 3.5.6 (Web, Data JPA, Validation) · MySQL 8, database **`product_store`**

## Structure

```text
02-product-rest-api/
├── pom.xml
├── postman/product-rest-api.postman_collection.json
└── src/main/
    ├── java/com/example/product/
    │   ├── ProductApplication.java                 run this
    │   ├── controller/ProductController.java       endpoints + query parameters
    │   ├── service/ProductService.java             CRUD, parameter checks, PageRequest, version check
    │   ├── repository/ProductRepository.java       JpaRepository + search query (optional filters)
    │   ├── model/Product.java                      @Entity with @Version
    │   ├── dto/ProductRequest, ProductUpdateRequest, QuantityUpdateRequest, ProductResponse, PageResponse, ErrorResponse
    │   └── exception/ProductNotFoundException, InvalidQueryException, ProductModifiedException, GlobalExceptionHandler
    └── resources/
        ├── application.properties
        ├── db-secret.properties                    MySQL password (git-ignored; copy db-secret.properties.example)
        ├── schema.sql                              products table
        └── sample-data.sql                         12 products
```

## How to run

1. Create the database:

   ```text
   mysql -u root -p < src/main/resources/schema.sql
   mysql -u root -p < src/main/resources/sample-data.sql
   ```

   Running both again resets the data.
2. In Eclipse, import it as an existing Maven project and run `ProductApplication`.
3. In Postman, import `postman/product-rest-api.postman_collection.json` and run it in order on fresh data.

## Endpoints

| Method | URI | Purpose | Success | Errors |
|---|---|---|---|---|
| POST | `/api/products` | create | **201** + `Location` | 400 |
| GET | `/api/products/{id}` | read one | **200** | 404 |
| GET | `/api/products` | list with filters, pagination, sorting | **200** | 400 |
| PUT | `/api/products/{id}` | replace (must send `version`) | **200** | 400, 404, **409** |
| PATCH | `/api/products/{id}/quantity` | update the inventory quantity only | **200** | 400, 404 |
| DELETE | `/api/products/{id}` | delete | **204** | 404 |

**Query parameters of `GET /api/products`.** All are optional:

| Parameter | Default | Meaning |
|---|---|---|
| `category` | | exact category, case-insensitive |
| `minPrice`, `maxPrice` | | price range, inclusive |
| `name` | | part of the product name (challenge: search) |
| `active` | | `true` / `false` |
| `page` | `0` | page number, starting at 0 |
| `size` | `10` | 1–100 rows per page |
| `sortBy` | `id` | `id`, `name`, `category`, `price`, `quantity`, `brand` |
| `direction` | `asc` | `asc` / `desc` |

## Observed output: CRUD

```text
POST /api/products  {"name":"Webcam 1080p","category":"Electronics","price":2499.00,"quantity":15,"brand":"Logitech"}
-> 201  Location: /api/products/13
{"id":13,"name":"Webcam 1080p","category":"Electronics","price":2499.00,"quantity":15,"brand":"Logitech","active":true,"version":0}

GET /api/products/13  -> 200  (same body)

PUT /api/products/13  {"name":"Webcam 4K","category":"Electronics","price":4999.00,"quantity":10,"brand":"Logitech","active":true,"version":0}
-> 200
{"id":13,"name":"Webcam 4K","category":"Electronics","price":4999.00,"quantity":10,"brand":"Logitech","active":true,"version":1}

PATCH /api/products/13/quantity  {"quantity":7}
-> 200
{"id":13,"name":"Webcam 4K","category":"Electronics","price":4999.00,"quantity":7,"brand":"Logitech","active":true,"version":2}

DELETE /api/products/13  -> 204 (empty body)

GET /api/products/13  -> 404
{"timestamp":"2026-10-11T22:21:09","status":404,"message":"Product not found with id 13","path":"/api/products/13"}
```

`active` was left out of the POST, so the product became active by default. Each update increased `version` by 1.

## Observed output: filtering, pagination, sorting

| Request | Result |
|---|---|
| `GET /api/products` | ids 1–10, `"page":0,"size":10,"totalElements":12,"totalPages":2,"sortBy":"id","direction":"asc"` |
| `GET /api/products?category=electronics` | 6 products (ids 1–5 and 12); case-insensitive |
| `GET /api/products?minPrice=1000&maxPrice=5000` | Mechanical Keyboard 3499, USB-C Hub 1299, Running Shoes 4599, Denim Jacket 2999 (4 products) |
| `GET /api/products?category=Electronics&minPrice=1000&maxPrice=10000&sortBy=price&direction=desc` | Headphones 8999 → Keyboard 3499 → USB-C Hub 1299 |
| `GET /api/products?page=1&size=5&sortBy=name&direction=asc` | the second 5 by name: Mechanical Keyboard, Noise Cancelling Headphones, Old Wired Mouse, Running Shoes, Trail Shoes; `"page":1,"size":5,"totalElements":12,"totalPages":3` |
| `GET /api/products?name=shoe` | Running Shoes, Trail Shoes |
| `GET /api/products?active=false` | Old Wired Mouse |
| `GET /api/products?page=9&size=5` | `{"content":[],"page":9,"size":5,"totalElements":12,"totalPages":3,...}`: an empty page, not an error |

Full response of the sorted, filtered query:

```json
{"content":[
  {"id":4,"name":"Noise Cancelling Headphones","category":"Electronics","price":8999.00,"quantity":12,"brand":"Sony","active":true,"version":0},
  {"id":2,"name":"Mechanical Keyboard","category":"Electronics","price":3499.00,"quantity":20,"brand":"Keychron","active":true,"version":0},
  {"id":5,"name":"USB-C Hub","category":"Electronics","price":1299.00,"quantity":35,"brand":"Anker","active":true,"version":0}],
 "page":0,"size":10,"totalElements":3,"totalPages":1,"sortBy":"price","direction":"desc"}
```

The SQL Hibernate generated for a page: one query for the rows, one for the total.

```text
select ... from products p1_0
where (? is null or lower(p1_0.category)=lower(?)) and (? is null or p1_0.price>=?) and (? is null or p1_0.price<=?)
  and (? is null or lower(p1_0.name) like lower(concat('%',?,'%')) escape '') and (? is null or p1_0.active=?)
order by p1_0.name limit ?,?
select count(p1_0.product_id) from products p1_0 where (... same filters ...)
```

## Observed output: validation and errors

Every error has the **standard structure** `{timestamp, status, message, path}`; validation errors add `fieldErrors`:

```json
{"timestamp":"2026-10-11T22:21:10","status":400,"message":"Validation failed","path":"/api/products",
 "fieldErrors":{"category":"category is required","name":"name must be between 2 and 100 characters","price":"price must be greater than 0","quantity":"quantity cannot be negative"}}
```

| Request | Status | message / fieldErrors |
|---|---|---|
| POST `{"name":"A","category":"","price":0,"quantity":-3}` | **400** | 4 field errors (above) |
| POST `"price":12.999` | **400** | `price may have at most 2 decimal places` |
| POST `"price":"cheap"` | **400** | `Request body is missing, not valid JSON, or has a value in the wrong format` |
| PATCH `.../1/quantity` `{"quantity":-1}` | **400** | `quantity cannot be negative` |
| GET `/api/products/999` | **404** | `Product not found with id 999` |
| DELETE `/api/products/999` | **404** | `Product not found with id 999` |
| GET `?sortBy=colour` | **400** | `sortBy must be one of [id, name, category, price, quantity, brand]` |
| GET `?direction=up` | **400** | `direction must be asc or desc` |
| GET `?size=500` | **400** | `size must be between 1 and 100` |
| GET `?minPrice=5000&maxPrice=1000` | **400** | `minPrice must not be greater than maxPrice` |
| GET `?minPrice=abc` | **400** | `'abc' is not a valid value for minPrice` |

## Challenge extension: optimistic locking with `@Version`

Two users opened product 2 while it was at version 0:

```text
GET /api/products/2 -> 200 {...,"price":3499.00,...,"version":0}            (both users)

PUT /api/products/2 {... "price":3299.00, "version":0}   (user A)
-> 200 {...,"price":3299.00,...,"version":1}

PUT /api/products/2 {... "price":3999.00, "version":0}   (user B, still has version 0)
-> 409 {"timestamp":"2026-10-11T22:21:11","status":409,"message":"Product 2 was changed by someone else (you sent version 0, current version is 1). Reload it and try again.","path":"/api/products/2"}

GET /api/products/2 -> 200 {...,"price":3299.00,...,"version":1}            (user A's change was NOT overwritten)
```

Every UPDATE that Hibernate sends contains the version, and DELETE checks it too:

```text
update products set active=?,brand=?,category=?,name=?,price=?,quantity=?,version=? where product_id=? and version=?
delete from products where product_id=? and version=?
```

There are two levels of protection:
1. **The service compares the version** sent by the client with the current one, giving a clear 409.
2. **Two requests at exactly the same moment** both pass step 1, but the second UPDATE matches no row (`version` has already changed). Hibernate throws `ObjectOptimisticLockingFailureException`, which the advice also turns into a 409.

## Explanation

**CRUD with HTTP:** POST creates (201), GET reads (200), PUT replaces (200), PATCH changes part (200), DELETE removes (204), and a missing id gives 404.

**Filtering with optional parameters.** The JPQL query uses `(:category IS NULL OR LOWER(p.category) = LOWER(:category))`. When a parameter is not sent, it is null and its condition is always true, so a single query handles every combination of filters.

**Pagination and sorting.** The service validates `page`, `size`, `sortBy` and `direction`, then builds `PageRequest.of(page, size, Sort.by(direction, sortBy))`:
- Spring Data adds `ORDER BY ... LIMIT offset,size` and runs a `COUNT` query.
- `PageResponse` returns the rows plus `page`, `size`, `totalElements` and `totalPages`.
- `sortBy` is checked against a list of allowed fields, so a client cannot sort by arbitrary text.

**Validation:** `@NotBlank`/`@Size` on name, `@Positive` + `@Digits` on price and `@PositiveOrZero` on quantity, checked by `@Valid`.

**Global exception handling.** `@RestControllerAdvice` maps each exception type to one status and the same JSON structure:

| Exception | Status |
|---|---|
| `ProductNotFoundException` | 404 |
| validation / bad parameters | 400 |
| version conflicts | 409 |

Controllers and services just throw.
