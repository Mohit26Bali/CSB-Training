# REST APIs: Hands-On Exercises 1–3

Exercises 1–3 of the "REST APIs, GraphQL & gRPC" assignment, as three independent Spring Boot projects. Exercises 4–10 (GraphQL, gRPC, microservices, capstone) are not part of this folder.

| Folder | Exercise | Main concepts | Storage |
|---|---|---|---|
| [`01-employee-rest-api`](01-employee-rest-api/README.md) | Employee Management REST API | resources, HTTP methods, status codes, DTOs, validation | in memory |
| [`02-product-rest-api`](02-product-rest-api/README.md) | E-Commerce Product REST API | CRUD, filtering, pagination, sorting, global error handling, optimistic locking | MySQL `product_store` |
| [`03-secure-rest-api`](03-secure-rest-api/README.md) | Secure REST API | Spring Security, JWT login, roles USER/HR/ADMIN, 401 vs 403, refresh tokens, audit log | MySQL `employee_portal` |

**Common stack:** Java 17, Maven, Spring Boot 3.5.6. Every project runs on port **8080**, so run one at a time.

**How to run each project:**
1. For projects 02 and 03, create the database: `mysql -u root -p < src/main/resources/schema.sql`, then `sample-data.sql`.
2. In Eclipse, **File → Import → Maven → Existing Maven Projects**, select the project folder, and run its `...Application` class.
3. In Postman, import the collection from the project's `postman/` folder and run it in order.

**Test credentials (Exercise 3):**

| Username | Password |
|---|---|
| `user` | `user123` |
| `hr` | `hr123` |
| `admin` | `admin123` |

Each project's README contains the endpoints, sample requests and responses observed in a real run, the error cases, and a short explanation of the concepts.
