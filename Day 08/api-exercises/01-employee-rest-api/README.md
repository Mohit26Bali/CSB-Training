# Exercise 1: Employee Management REST API

A REST API for the **employees** resource:
- GET, POST, PUT, PATCH and DELETE
- the HTTP status codes 200, 201, 204, 404 and 400

Both challenge extensions are included: **DTOs** (the model is never exposed) and **Bean Validation**.

The exercise is about REST, so the employees are kept **in memory**: no database is needed. The application starts with 3 sample employees, and everything resets when it stops.

## Tech

Java 17 · Maven · Spring Boot 3.5.6 (Spring Web, Validation)

## Structure

```text
01-employee-rest-api/
├── pom.xml
├── postman/employee-rest-api.postman_collection.json
└── src/main/java/com/example/employee/
    ├── EmployeeApplication.java                  run this
    ├── controller/EmployeeController.java        the 6 endpoints, status codes
    ├── service/EmployeeService.java              operations, DTO <-> model
    ├── repository/EmployeeRepository.java        in-memory storage (ConcurrentHashMap)
    ├── model/Employee.java, EmployeeStatus.java  ACTIVE / ON_LEAVE / INACTIVE
    ├── dto/EmployeeRequest, StatusUpdateRequest, EmployeeResponse, ErrorResponse
    └── exception/EmployeeNotFoundException, GlobalExceptionHandler
```

## How to run

1. In Eclipse: **File → Import → Maven → Existing Maven Projects**, select `01-employee-rest-api`, then run `EmployeeApplication`.
2. In Postman, **Import** `postman/employee-rest-api.postman_collection.json` and run the requests in order.

## Endpoints

| Method | URI | Purpose | Success | Errors |
|---|---|---|---|---|
| GET | `/api/employees` | all employees | **200** | |
| GET | `/api/employees/{id}` | one employee | **200** | 404, 400 (non-numeric id) |
| POST | `/api/employees` | create | **201** + `Location` header | 400 |
| PUT | `/api/employees/{id}` | **replace** all details | **200** | 404, 400 |
| PATCH | `/api/employees/{id}/status` | change **only** the status | **200** | 404, 400 |
| DELETE | `/api/employees/{id}` | delete | **204** (no body) | 404 |

**URI design:**
- The resource is a **plural noun** (`/employees`), and the id selects one member (`/employees/4`).
- The HTTP method expresses the action, so there are no verbs such as `/getEmployee` in the URI.
- `/status` is a sub-resource that PATCH changes on its own.

## Sample requests and responses (observed)

### GET /api/employees → 200

```json
[{"id":1,"name":"Asha Rao","email":"asha.rao@company.com","department":"Engineering","salary":95000.00,"status":"ACTIVE"},
 {"id":2,"name":"Ravi Kumar","email":"ravi.kumar@company.com","department":"Finance","salary":72000.00,"status":"ACTIVE"},
 {"id":3,"name":"Meera Iyer","email":"meera.iyer@company.com","department":"HR","salary":68000.00,"status":"ON_LEAVE"}]
```

### GET /api/employees/2 → 200

```json
{"id":2,"name":"Ravi Kumar","email":"ravi.kumar@company.com","department":"Finance","salary":72000.00,"status":"ACTIVE"}
```

### POST /api/employees → 201, `Location: /api/employees/4`

Request:

```json
{"name":"Kiran Das","email":"Kiran.Das@company.com","department":"Engineering","salary":82000.00,"status":"ACTIVE"}
```

Response (the e-mail is stored in lower case):

```json
{"id":4,"name":"Kiran Das","email":"kiran.das@company.com","department":"Engineering","salary":82000.00,"status":"ACTIVE"}
```

### PUT /api/employees/4 → 200

Request:

```json
{"name":"Kiran Das","email":"kiran.das@company.com","department":"Platform","salary":88000.00,"status":"ACTIVE"}
```

Response:

```json
{"id":4,"name":"Kiran Das","email":"kiran.das@company.com","department":"Platform","salary":88000.00,"status":"ACTIVE"}
```

### PATCH /api/employees/4/status → 200

Request: `{"status":"ON_LEAVE"}`

Response. Only `status` changed; department and salary are untouched:

```json
{"id":4,"name":"Kiran Das","email":"kiran.das@company.com","department":"Platform","salary":88000.00,"status":"ON_LEAVE"}
```

### DELETE /api/employees/4 → 204 (empty body)

Then `GET /api/employees/4` → **404**:

```json
{"timestamp":"2026-10-11T22:17:53","status":404,"error":"Not Found","message":"Employee not found with id 4","path":"/api/employees/4"}
```

## Error cases (observed)

| Request | Status | Response |
|---|---|---|
| `PUT /api/employees/99` | **404** | `"message":"Employee not found with id 99"` |
| `PATCH /api/employees/99/status` | **404** | `"message":"Employee not found with id 99"` |
| `DELETE /api/employees/99` | **404** | `"message":"Employee not found with id 99"` |
| `POST` `{"name":"","email":"not-an-email","department":"HR","salary":-5}` | **400** | `"fieldErrors":{"email":"email must be a valid e-mail address","name":"name is required","salary":"salary must be greater than 0","status":"status is required (ACTIVE, ON_LEAVE or INACTIVE)"}` |
| `PATCH .../1/status` `{"status":"RETIRED"}` | **400** | `"message":"Request body is missing, not valid JSON, or has a value in the wrong format"` |
| `PATCH .../1/status` `{}` | **400** | `"fieldErrors":{"status":"status is required (ACTIVE, ON_LEAVE or INACTIVE)"}` |
| `GET /api/employees/abc` | **400** | `"message":"'abc' is not a valid id"` |

## Explanation

**Resources and HTTP methods:**

| Method | Meaning |
|---|---|
| GET | read; changes nothing (safe) |
| POST | create a new member of the collection |
| PUT | **replace** the whole resource; every field must be sent |
| PATCH | **change part** of a resource; only the status here |
| DELETE | remove the resource |

GET, PUT and DELETE are *idempotent*: repeating them gives the same result.

**Status codes:**

| Code | Meaning |
|---|---|
| 200 OK | success, with a body |
| 201 Created | a new resource was created; `Location` tells the client its URI |
| 204 No Content | success, nothing to return (delete) |
| 400 Bad Request | invalid input |
| 404 Not Found | no resource with that id |

**DTOs (challenge extension).** `EmployeeRequest` is what the client may send; for example, it cannot choose the id. `EmployeeResponse` is what it receives. The internal `Employee` model can change without breaking the API.

**Bean Validation (challenge extension).** The DTO fields are annotated: `@NotBlank` on name, `@Email` on email, and `@Positive` + `@Digits` on salary. `@Valid` in the controller checks them before the service runs, and `GlobalExceptionHandler` returns every invalid field at once.
