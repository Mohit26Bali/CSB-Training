# 14. CRUD with Spring Data JPA

**Concept:** Spring Boot + JPA. A `Product` **entity** and a `ProductRepository extends JpaRepository<Product, Long>`, with create / read / update / delete through **controller → service → repository**.

**There is no JDBC and no hand-written SQL.** Hibernate generates every statement, and they are logged so they can be checked. The optional extension adds **search + pagination + sorting**.

## Tech

Java 17 · Maven · **Spring Boot 3.5.6** · **spring-boot-starter-data-jpa** (Spring Data JPA 3.5.4, **Hibernate ORM 6.6.29**) · starter-validation · MySQL 8

**Database:** shared Day 07 database `Day_007_Assigments`, table **`a14_products`** (12 sample rows).
- `spring.jpa.hibernate.ddl-auto=validate`: Hibernate **checks** that the table matches the entity, but never creates or alters it.
- The table comes from `schema.sql`, like every other Day 07 table.

## Structure

```text
14-spring-data-jpa-crud/
├── pom.xml
└── src/main/
    ├── java/com/company/boot/
    │   ├── JpaCrudApplication.java              @SpringBootApplication (run this)
    │   ├── entity/Product.java                  @Entity @Table(a14_products), @Id @GeneratedValue
    │   ├── repository/ProductRepository.java    interface extends JpaRepository - NO implementation class
    │   ├── service/ProductService.java          CRUD logic, @Transactional
    │   ├── controller/ProductController.java    REST endpoints, Pageable
    │   ├── dto/ProductRequest, ProductResponse, PageResponse, ErrorResponse
    │   └── exception/ResourceNotFoundException, DuplicateResourceException, GlobalExceptionHandler
    └── resources/
        ├── application.properties               ddl-auto=validate, SQL logging, max page size
        ├── db-secret.properties                 password (git-ignored; copy db-secret.properties.example)
        └── schema.sql                           a14_products + 12 rows
```

## Endpoints

| Method | URL | Operation | Repository method used |
|---|---|---|---|
| POST | `/api/products` | create → **201** | `existsByNameIgnoreCase`, **`save`** |
| GET | `/api/products/{id}` | read → 200 / 404 | **`findById`** |
| GET | `/api/products?page=&size=&sort=` | list (paged) | **`findAll(Pageable)`** |
| GET | `/api/products?category=` | list one category (paged) | `findByCategoryIgnoreCase` (derived query) |
| PUT | `/api/products/{id}` | update → 200 / 404 | `findById` + *dirty checking* |
| DELETE | `/api/products/{id}` | delete → **204** / 404 | `findById`, **`delete`** |
| GET | `/api/products/search?name=&category=&minPrice=&maxPrice=` | search (paged) | `search` (`@Query` JPQL) |

Paging parameters (optional extension):
- `page` is 0-based.
- `size` defaults to 5, with a maximum of 20.
- `sort` takes the form `field` or `field,desc`.

## How to run

1. Run `src/main/resources/schema.sql` in MySQL. It recreates only `a14_products`; re-run it to reset the data.
2. **Eclipse:** **File → Import → Maven → Existing Maven Projects**, select `14-spring-data-jpa-crud`, then right-click `JpaCrudApplication` → **Run As → Java Application**.
3. **Test with Postman or a browser:**
   - `GET http://localhost:8080/api/products?page=0&size=3`
   - `POST http://localhost:8080/api/products` with the body `{"name":"Webcam 1080p","category":"Electronics","price":2499.00,"stock":15}`
4. Every SQL statement and its parameter values appear in the Eclipse **Console** (`org.hibernate.SQL` / `o.h.orm.jdbc.bind`).

## Observed output: CRUD with the generated SQL

### CREATE: `POST /api/products` `{"name":"Webcam 1080p","category":"Electronics","price":2499.00,"stock":15}` → **201** `Location: /api/products/13`

```json
{"id":13,"name":"Webcam 1080p","category":"Electronics","price":2499.00,"stock":15,"createdAt":"2026-10-11T11:59:48","updatedAt":"2026-10-11T11:59:48"}
```

```text
select p1_0.product_id from a14_products p1_0 where upper(p1_0.name)=upper(?) limit ?        <- existsByNameIgnoreCase
  binding parameter (1:VARCHAR) <- [Webcam 1080p]
  binding parameter (2:INTEGER) <- [1]
insert into a14_products (category,created_at,name,price,stock,updated_at) values (?,?,?,?,?,?)   <- save()
  binding parameter (1:VARCHAR) <- [Electronics]
  binding parameter (2:TIMESTAMP) <- [2026-10-11T11:59:48]
  binding parameter (3:VARCHAR) <- [Webcam 1080p]
  binding parameter (4:NUMERIC) <- [2499.00]
  binding parameter (5:INTEGER) <- [15]
  binding parameter (6:TIMESTAMP) <- [2026-10-11T11:59:48]
```

- `product_id` is not in the INSERT. `@GeneratedValue(IDENTITY)` lets MySQL generate it, and Hibernate reads it back into the entity.
- `created_at`/`updated_at` were set by the `@PrePersist` callback.

### READ: `GET /api/products/13` → **200**

```text
select p1_0.product_id,p1_0.category,p1_0.created_at,p1_0.name,p1_0.price,p1_0.stock,p1_0.updated_at from a14_products p1_0 where p1_0.product_id=?
  binding parameter (1:BIGINT) <- [13]
```

### READ (paged): `GET /api/products?page=0&size=3` → **200**

```json
{"content":[{"id":1,"name":"Wireless Mouse",...},{"id":2,"name":"Mechanical Keyboard",...},{"id":3,"name":"USB-C Hub",...}],
 "page":0,"size":3,"totalElements":13,"totalPages":5,"first":true,"last":false,"sort":"id: ASC"}
```

```text
select ... from a14_products p1_0 order by p1_0.product_id limit ?,?      [0] [3]
select count(p1_0.product_id) from a14_products p1_0                       <- for totalElements / totalPages
```

### UPDATE: `PUT /api/products/13` `{"name":"Webcam 4K","category":"Electronics","price":4999.00,"stock":10}` → **200**

```json
{"id":13,"name":"Webcam 4K","category":"Electronics","price":4999.00,"stock":10,"createdAt":"2026-10-11T11:59:48","updatedAt":"2026-10-11T11:59:50"}
```

```text
select ... from a14_products p1_0 where p1_0.product_id=?                                   <- findById
select p1_0.product_id from a14_products p1_0 where upper(p1_0.name)=upper(?) and p1_0.product_id<>? limit ?
update a14_products set category=?,name=?,price=?,stock=?,updated_at=? where product_id=?  <- no save() call: dirty checking
  binding parameter (1:VARCHAR) <- [Electronics]
  binding parameter (2:VARCHAR) <- [Webcam 4K]
  binding parameter (3:NUMERIC) <- [4999.00]
  binding parameter (4:INTEGER) <- [10]
  binding parameter (5:TIMESTAMP) <- [2026-10-11T11:59:50]
  binding parameter (6:BIGINT) <- [13]
```

`created_at` is **not** in the UPDATE, because the field is `@Column(updatable = false)`.

### DELETE: `DELETE /api/products/13` → **204** (empty body), then `GET /api/products/13` → **404**

```text
select ... from a14_products p1_0 where p1_0.product_id=?
delete from a14_products where product_id=?
  binding parameter (1:BIGINT) <- [13]

{"timestamp":"2026-10-11T11:59:51","status":404,"error":"Not Found","message":"Product not found with id 13","path":"/api/products/13"}
```

### Database records checked directly in MySQL after each step

A second run did create → update → delete. The new row got id **14**, because MySQL never reuses an AUTO_INCREMENT value, even after the delete of 13.

```text
--- after CREATE (POST -> 201) ---
| product_id | name         | category    | price   | stock | created_at          | updated_at          |
|         14 | Webcam 1080p | Electronics | 2499.00 |    15 | 2026-10-11 12:00:31 | 2026-10-11 12:00:31 |

--- after UPDATE (PUT /api/products/14 -> 200) ---
|         14 | Webcam 4K    | Electronics | 4999.00 |    10 | 2026-10-11 12:00:31 | 2026-10-11 12:00:33 |

--- after DELETE (DELETE /api/products/14 -> 204) ---
(row 14 gone)      SELECT COUNT(*) FROM a14_products  ->  12
```

**No JDBC.** A search of `src/main/java` for `java.sql`, `JdbcTemplate`, `PreparedStatement`, `ResultSet` and `DriverManager` found **0 matches**.

## Optional extension: search + pagination + sorting

| Request | Result (abridged) | Generated SQL |
|---|---|---|
| `GET /api/products?page=1&size=4&sort=price,desc` | 4 products: USB-C Hub 1299, Java Concurrency 1099, Desk Lamp 999, Effective Java 899; `"page":1,"totalPages":3,"sort":"price: DESC"` | `... order by p1_0.price desc limit ?,?` with `[4] [4]` (offset 4) + `select count(...)` |
| `GET /api/products?category=books&size=2` | Clean Code, Effective Java; `"totalElements":4,"totalPages":2` | derived query `findByCategoryIgnoreCase`: `... where upper(p1_0.category)=upper(?) order by p1_0.product_id limit ?` + count |
| `GET /api/products/search?name=mouse` | Wireless Mouse, Mouse Pad XL | `... where (? is null or lower(p1_0.name) like lower(concat('%',?,'%')) escape '') and (? is null or ...) ...` |
| `GET /api/products/search?category=Electronics&minPrice=1000&maxPrice=10000&sort=price` | USB-C Hub 1299, Mechanical Keyboard 3499, Noise Cancelling Headphones 8999 | same JPQL; the unused filters are bound as `null`, so they switch themselves off |

## Failure / edge-case evidence

**1. Table missing at startup (`ddl-auto=validate`).** The app was started **before** running `schema.sql`:

```text
ERROR ... LocalContainerEntityManagerFactoryBean : Failed to initialize JPA EntityManagerFactory: [PersistenceUnit: default] Unable to build Hibernate SessionFactory; nested exception is org.hibernate.tool.schema.spi.SchemaManagementException: Schema-validation: missing table [a14_products]
ERROR ... o.s.boot.SpringApplication             : Application run failed
```

The application refuses to start instead of failing later at the first query.

**2. HTTP-level cases:**

| Request | Status | Response / observation |
|---|---|---|
| `PUT /api/products/999` | **404** | `Product not found with id 999`. Only the `select` ran; no UPDATE. |
| `DELETE /api/products/999` | **404** | `Product not found with id 999`. Only the `select` ran; no DELETE. |
| `POST` `{"name":"wireless mouse",...}` | **409** | `A product named 'wireless mouse' already exists` (case-insensitive check via `upper(...)=upper(?)`) |
| `PUT /api/products/2` renaming to `"Desk Lamp"` | **409** | `A product named 'Desk Lamp' already exists` (`existsByNameIgnoreCaseAndIdNot` skips the product itself) |
| `POST` `{"name":"","price":-1,"stock":-5,...}` | **400** | `"validationErrors":{"name":"name is required","price":"price must be greater than 0","stock":"stock cannot be negative"}`. **No SQL at all.** |
| `GET /api/products?sort=colour` | **400** | `Cannot sort by 'colour': no such product property` (Spring Data `PropertyReferenceException`) |
| `GET /api/products/abc` | **400** | `Parameter 'id' has an invalid value 'abc'` |

**3. Pagination edge cases:**

| Request | Result |
|---|---|
| `GET /api/products?page=50&size=5` | **200**, `{"content":[],"page":50,"size":5,"totalElements":12,"totalPages":3,"first":false,"last":true}`. An empty page, not an error; SQL `limit ?,?` with offset `[250]`. |
| `GET /api/products?size=1000` | **200** with `"size":20`. **Capped** by `spring.data.web.pageable.max-page-size=20`, so a client cannot dump the whole table; SQL `limit ?,?` with `[0] [20]`. All 12 rows fit in the first page, so Spring Data **skipped the `count` query**. |

**4. Update with identical values.** A `PUT /api/products/1` that sends the current values returns **200**, and the log shows only the two `select` statements, **no `update`**. Hibernate's dirty checking found nothing changed, so `updated_at` also stayed the same.

## Explanation

**Entity (`@Entity`)** is a Java class mapped to a table; each object is one row.
- `@Table(name = "a14_products")` sets the table name, and `@Column` sets column names and rules (`nullable`, `length`, `precision`, `updatable = false`).
- JPA requires a **no-argument constructor**, which Hibernate uses to build objects from result rows.
- Lifecycle callbacks like `@PrePersist`/`@PreUpdate` run just before the INSERT/UPDATE.

**`@Id`** marks the primary-key field. JPA uses it to identify the entity: `findById`, the `where product_id=?` in UPDATE/DELETE, and whether `save()` should INSERT or UPDATE. `@GeneratedValue(strategy = IDENTITY)` lets the database assign it (MySQL `AUTO_INCREMENT`).

**`JpaRepository<Product, Long>`** (entity type, id type) is a Spring Data interface. At startup Spring Data creates the implementation, so **no class implements `ProductRepository`**. It inherits:
- `save`, `saveAll`
- `findById`, `findAll`, `findAll(Pageable)`, `findAll(Sort)`
- `existsById`, `count`
- `delete`, `deleteById`
- `flush`

There are two ways to add queries:
- **Derived queries:** Spring Data parses the method name. `findByCategoryIgnoreCase` became `where upper(category)=upper(?)`, and `existsByNameIgnoreCaseAndIdNot` became `... and product_id<>? limit 1`.
- **`@Query` with JPQL:** queries on **entities and fields** (`Product p`, `p.price`), not tables and columns. Hibernate translates them to MySQL SQL.

**`Pageable` / `Page`** turn `page`, `size` and `sort` parameters into `LIMIT offset,size` + `ORDER BY` + an optional `count(*)`. That is why one call returns both the rows and the totals.

**`@Transactional`** on the service:
- Inside a transaction, a loaded entity is **managed**. Changing its fields is enough, and Hibernate issues the UPDATE at commit (**dirty checking**). That is why `update()` never calls `save()`.
- `readOnly = true` is the default for the read methods.

**JPA vs JDBC.** With JDBC (Assignments 10–13), the code wrote SQL, set parameters and mapped `ResultSet`s by hand. With JPA, the mapping is declared once on the entity, and Hibernate generates all of the SQL; the console log above shows exactly what it sent.
