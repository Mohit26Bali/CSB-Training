# 10. Spring MVC: REST Controller and Mappings

**Concept:** Spring MVC. A `@RestController` with three GET endpoints:

- `GET /api/hello`
- `GET /api/hello/{name}` (**`@PathVariable`**)
- `GET /api/greet?name=` (**`@RequestParam`**)

The optional extension adds **POST + `ResponseEntity`**:
- POSTed greetings are stored in MySQL.
- A POST answers **201 Created** with a `Location` header.
- A GET by id answers **200 / 404**.

## Tech

Java 17 · Maven · **Spring Boot 3.5.6** (starter-web, starter-jdbc) · MySQL 8

**Database:** shared Day 07 database `Day_007_Assigments`, table **`a10_greetings`** (used by the POST extension only).

## Structure

```text
10-rest-controller-mappings/
├── pom.xml
└── src/main/
    ├── java/com/company/boot/
    │   ├── RestMappingsApplication.java          @SpringBootApplication (run this)
    │   ├── controller/
    │   │   ├── HelloController.java              /api/hello, /api/hello/{name}, /api/greet
    │   │   └── GreetingController.java           POST/GET /api/greetings (optional extension)
    │   ├── model/
    │   │   ├── Greeting.java                     record: response JSON / table row
    │   │   └── GreetingRequest.java              record: POST request body
    │   ├── service/GreetingService.java
    │   └── repository/GreetingRepository.java    JdbcTemplate + generated key
    └── resources/
        ├── application.properties                datasource, server.error.include-message
        ├── db-secret.properties                  password (git-ignored; copy db-secret.properties.example)
        └── schema.sql                            a10_greetings + 2 sample rows
```

## Endpoints

| Method | URL | Annotation used | Success |
|---|---|---|---|
| GET | `/api/hello` | `@GetMapping` | 200, text |
| GET | `/api/hello/{name}` | `@PathVariable` | 200, text |
| GET | `/api/greet?name=..&lang=..` | `@RequestParam` (name required, lang defaults to `en`; `hi`/`fr` also supported) | 200, JSON |
| POST | `/api/greetings` | `@RequestBody` + `ResponseEntity.created(...)` | 201 + `Location` |
| GET | `/api/greetings` | | 200, JSON list |
| GET | `/api/greetings/{id}` | `@PathVariable int` + `ResponseEntity` | 200 or 404 |

## How to run

1. Run `src/main/resources/schema.sql` in MySQL (`mysql -u root -p < schema.sql`). It recreates only `a10_greetings`; re-run it to reset the data.
2. **Eclipse:** **File → Import → Maven → Existing Maven Projects**, select `10-rest-controller-mappings`, then right-click `RestMappingsApplication` → **Run As → Java Application**.
3. **Test the GET endpoints** in a browser: http://localhost:8080/api/hello, http://localhost:8080/api/hello/Mohit, http://localhost:8080/api/greet?name=Mohit&lang=hi.
4. **Test the POST** with Postman (Body → raw → JSON), or with curl in **cmd**:

   ```text
   curl -i -X POST -H "Content-Type: application/json" -d "{\"name\":\"Mohit\",\"message\":\"Good evening\"}" http://localhost:8080/api/greetings
   ```

   In PowerShell, type `curl.exe`, not `curl`. Plain `curl` there is an alias for `Invoke-WebRequest`.

## Observed output: every endpoint (method, URL, status, response)

### Required endpoints

| # | Method | URL | Status | Content-Type | Response |
|---|---|---|---|---|---|
| 1 | GET | `/api/hello` | **200** | text/plain | `Hello, World!` |
| 2 | GET | `/api/hello/Mohit` | **200** | text/plain | `Hello, Mohit!` |
| 3 | GET | `/api/hello/John%20Smith` | **200** | text/plain | `Hello, John Smith!` (URL-decoded) |
| 4 | GET | `/api/greet?name=Mohit` | **200** | application/json | `{"greeting":"Hello, Mohit!","name":"Mohit","lang":"en"}` |
| 5 | GET | `/api/greet?name=Mohit&lang=hi` | **200** | application/json | `{"greeting":"Namaste, Mohit!","name":"Mohit","lang":"hi"}` |
| 6 | GET | `/api/greet?name=Mohit&lang=fr` | **200** | application/json | `{"greeting":"Bonjour, Mohit!","name":"Mohit","lang":"fr"}` |
| 7 | GET | `/api/greet?lang=hi&name=Asha` | **200** | application/json | `{"greeting":"Namaste, Asha!","name":"Asha","lang":"hi"}` (parameter order does not matter) |

A `String` return value is sent as `text/plain`, and a `Map` return value as JSON (converted by Jackson).

### Optional extension: POST + ResponseEntity

```text
GET /api/greetings                                        -> 200
[{"id":1,"name":"Asha","message":"Good morning","createdAt":"2026-10-11T11:40:52"},
 {"id":2,"name":"Ravi","message":"Happy coding","createdAt":"2026-10-11T11:40:52"}]

POST /api/greetings  {"name":"Mohit","message":"Good evening"}
                                                          -> 201   Location: /api/greetings/3
{"id":3,"name":"Mohit","message":"Good evening","createdAt":"2026-10-11T11:41:15"}

POST /api/greetings  {"name":"Priya"}                     -> 201   Location: /api/greetings/4
{"id":4,"name":"Priya","message":"Welcome!","createdAt":"2026-10-11T11:41:15"}      <- default message

GET /api/greetings/3                                      -> 200
{"id":3,"name":"Mohit","message":"Good evening","createdAt":"2026-10-11T11:41:15"}
```

The rows in MySQL afterwards:

```text
+-------------+-------+--------------+---------------------+
| greeting_id | name  | message      | created_at          |
+-------------+-------+--------------+---------------------+
|           1 | Asha  | Good morning | 2026-10-11 11:40:52 |
|           2 | Ravi  | Happy coding | 2026-10-11 11:40:52 |
|           3 | Mohit | Good evening | 2026-10-11 11:41:15 |
|           4 | Priya | Welcome!     | 2026-10-11 11:41:15 |
+-------------+-------+--------------+---------------------+
```

## Failure / edge-case evidence

| # | Method | URL / body | Status | Response | Who produced it |
|---|---|---|---|---|---|
| 1 | GET | `/api/greet` (no `name`) | **400** | `"message":"Required parameter 'name' is not present."` | Spring (required `@RequestParam`) |
| 2 | GET | `/api/greet?name=` | **400** | `{"error":"Query parameter 'name' must not be blank"}` | our code, `ResponseEntity.badRequest()` |
| 3 | GET | `/api/greet?name=Mohit&lang=de` | **400** | `{"error":"Unsupported lang 'de'. Use en, hi or fr"}` | our code |
| 4 | GET | `/api/hello/` (trailing slash) | **404** | `"message":"No static resource api/hello."` | Spring 6: no trailing-slash matching |
| 5 | GET | `/api/hello/Mohit/extra` | **404** | `"message":"No static resource api/hello/Mohit/extra."` | `{name}` matches one path segment only |
| 6 | GET | `/api/greetings/99` | **404** | (empty body) | our code, `ResponseEntity.notFound()` |
| 7 | GET | `/api/greetings/abc` | **400** | `"message":"Method parameter 'id': Failed to convert value of type 'java.lang.String' to required type 'int'; For input string: \"abc\""` | Spring type conversion |
| 8 | DELETE | `/api/hello` | **405** | `"message":"Method 'DELETE' is not supported."`, header `Allow: GET` | Spring: URL exists, method does not |
| 9 | POST | `/api/hello` | **405** | `"message":"Method 'POST' is not supported."`, header `Allow: GET` | Spring |
| 10 | POST | `/api/greetings` `{"message":"no name"}` | **400** | `{"error":"Field 'name' is required"}` | our code |
| 11 | POST | `/api/greetings` `{"name":"Bad JSON",` | **400** | `"message":"JSON parse error: Unexpected end-of-input within/between Object entries"` | Jackson / Spring |
| 12 | POST | `/api/greetings` with `Content-Type: text/plain` | **415** | `"message":"Content-Type 'text/plain;charset=UTF-8' is not supported."` | Spring: `@RequestBody` expects JSON |
| 13 | GET | `/api/unknown` | **404** | `"message":"No static resource api/unknown."` | Spring: no mapping |

Spring's own errors use Boot's standard JSON error body. For example, case 1 in full:

```json
{"timestamp":"2026-10-11T06:11:15.595+00:00","status":400,"error":"Bad Request","message":"Required parameter 'name' is not present.","path":"/api/greet"}
```

The `message` field appears only because of `server.error.include-message=always`; Boot hides it by default.

## Explanation

**`@RestController`** = `@Controller` + `@ResponseBody`. The class handles HTTP requests, and each method's **return value becomes the response body**, with no HTML view. A `String` is sent as text, and objects, records, `Map`s and `List`s are converted to JSON by Jackson.

**Request mapping** connects an HTTP method + URL to a Java method.

| Annotation | Meaning |
|---|---|
| `@RequestMapping("/api")` on the class | URL prefix for every method in it |
| `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping` | shortcuts for `@RequestMapping(method = ...)` |

`DispatcherServlet` picks the handler by URL and method. When nothing matches, it answers:
- **404** if the URL is unknown
- **405** if the URL is known but the method is wrong (case 8)
- **415** if the body format is wrong (case 12)

**`@PathVariable`** reads part of the **path**, and is used to identify a resource. `/api/hello/{name}` with `/api/hello/Mohit` gives `name = "Mohit"`.
- It is required by the path itself: `/api/hello/` does not match (case 4).
- It is converted to the parameter type (`int id`), and a bad value gives 400 (case 7).

**`@RequestParam`** reads a **query-string** parameter (`?name=Mohit&lang=hi`), and is used for options, filters and search terms.
- It is required by default; when missing, Spring answers 400 (case 1).
- It can be made optional with `defaultValue` (`lang`) or `required = false`.
- An empty value (`?name=`) still counts as "present", so blank checks are our job (case 2).

**`ResponseEntity<T>`** (optional extension) gives control over the **whole response**: status, headers and body.
- `ResponseEntity.ok(body)` gives 200.
- `created(uri).body(...)` gives 201 + `Location`, the REST convention for POST.
- `badRequest()` gives 400.
- `notFound().build()` gives 404.

**`@RequestBody`** turns the JSON request body into a Java object (`GreetingRequest`).

Explicit names (`@PathVariable("name")`, `@RequestParam("name")`) are used so the code does not depend on the `-parameters` compiler flag being enabled in Eclipse.
