# 13. Global Exception Handling

**Concept:** Spring MVC. A small bank-account API whose controllers and services **only throw exceptions**:
- `ResourceNotFoundException`
- `IllegalArgumentException`
- `InsufficientFundsException`
- ...

One **`@ControllerAdvice`** class (`GlobalExceptionHandler`) converts every exception into the **same `ErrorResponse` JSON** with a suitable HTTP status. **No stack trace, exception class name or SQL ever reaches the client.** An unexpected error returns a generic message plus an `errorId`, and the full stack trace is written to the server log under that id. The optional extension adds a validation-exception handler.

## Tech

Java 17 · Maven · **Spring Boot 3.5.6** (starter-web, starter-validation, starter-jdbc) · MySQL 8

**Database:** shared Day 07 database `Day_007_Assigments`, table **`a13_accounts`** (unique e-mail, `CHECK balance >= 0`, 3 sample accounts).

## Structure

```text
13-global-exception-handling/
├── pom.xml
└── src/main/
    ├── java/com/company/boot/
    │   ├── GlobalExceptionHandlingApplication.java   @SpringBootApplication (run this)
    │   ├── controller/AccountController.java         happy path only - no try/catch
    │   ├── service/AccountService.java               throws exceptions, never builds responses
    │   ├── repository/AccountRepository.java         JdbcTemplate
    │   ├── model/Account.java
    │   ├── dto/
    │   │   ├── AccountRequest.java                   @Valid body (optional extension)
    │   │   └── ErrorResponse.java                    the ONE error format
    │   └── exception/
    │       ├── ResourceNotFoundException.java        -> 404
    │       ├── InsufficientFundsException.java       -> 422
    │       └── GlobalExceptionHandler.java           @ControllerAdvice - all @ExceptionHandler methods
    └── resources/
        ├── application.properties                    server.error.include-stacktrace=never
        ├── db-secret.properties                      password (git-ignored; copy db-secret.properties.example)
        └── schema.sql                                a13_accounts + 3 rows
```

## Exception → status mapping

| Exception | Thrown when | Status |
|---|---|---|
| `ResourceNotFoundException` | the account id does not exist | **404** |
| `IllegalArgumentException` | the amount is ≤ 0 or has more than 2 decimals; a transfer to the same account | **400** |
| `InsufficientFundsException` | the balance is too low | **422** |
| `DuplicateKeyException` | the e-mail already exists (unique key) | **409** |
| `MethodArgumentNotValidException` *(optional extension)* | `@Valid` body fails | **400** + `validationErrors` |
| `MethodArgumentTypeMismatchException` | `/api/accounts/abc`, `amount=ten` | **400** |
| `MissingServletRequestParameterException` | `?amount=` missing | **400** |
| `HttpMessageNotReadableException` | malformed JSON | **400** |
| `HttpRequestMethodNotSupportedException` | e.g. `DELETE` on a GET-only URL | **405** |
| `NoResourceFoundException` | unknown URL | **404** |
| `Exception` (safety net) | anything else, i.e. a bug | **500** + `errorId` |

## How to run

1. Run `src/main/resources/schema.sql` in MySQL. It recreates only `a13_accounts` with 3 accounts; re-run it to reset the balances.
2. **Eclipse:** **File → Import → Maven → Existing Maven Projects**, select `13-global-exception-handling`, then right-click `GlobalExceptionHandlingApplication` → **Run As → Java Application**.
3. **Try these:**
   - in a browser: `GET http://localhost:8080/api/accounts/1` and `/api/accounts/99`
   - in Postman: `POST http://localhost:8080/api/accounts/1/withdraw?amount=-50` and `POST http://localhost:8080/api/accounts/transfer?from=1&to=2&amount=1000`
   - the 500 demo: `GET http://localhost:8080/api/accounts/2/instalment?months=0`
4. Watch the Eclipse **Console** for the server-side log of the 500 error.

## Observed output

### Happy path

| Method | URL | Status | Response (abridged) |
|---|---|---|---|
| GET | `/api/accounts/1` | **200** | `{"accountId":1,"holderName":"Asha Rao","email":"asha@example.com","balance":10000.00,...}` |
| POST | `/api/accounts/1/withdraw?amount=1500` | **200** | `{"accountId":1,...,"balance":8500.00,...}` |
| POST | `/api/accounts/transfer?from=1&to=3&amount=2000` | **200** | `[{"accountId":1,...,"balance":6500.00},{"accountId":3,...,"balance":2000.00}]` |
| GET | `/api/accounts/2/instalment?months=12` | **200** | `{"monthlyInstalment":208.33,"months":12,"accountId":2}` |
| POST | `/api/accounts` `{"holderName":"Kiran Das","email":"kiran@example.com","openingBalance":500}` | **201** | `{"accountId":4,"holderName":"Kiran Das",...,"balance":500.00,...}` |

### ResourceNotFoundException → 404

```text
GET  /api/accounts/99                              -> 404
{"timestamp":"2026-10-11T11:55:28","status":404,"error":"Not Found","message":"Account not found with id 99","path":"/api/accounts/99"}

POST /api/accounts/99/withdraw?amount=100          -> 404
{"timestamp":"2026-10-11T11:55:28","status":404,"error":"Not Found","message":"Account not found with id 99","path":"/api/accounts/99/withdraw"}

POST /api/accounts/transfer?from=1&to=99&amount=100 -> 404
{"timestamp":"2026-10-11T11:55:28","status":404,"error":"Not Found","message":"Account not found with id 99","path":"/api/accounts/transfer"}
```

### IllegalArgumentException → 400

```text
POST /api/accounts/1/withdraw?amount=-50           -> 400
{"timestamp":"2026-10-11T11:55:28","status":400,"error":"Bad Request","message":"Amount must be greater than 0 (got -50)","path":"/api/accounts/1/withdraw"}

POST /api/accounts/1/withdraw?amount=0             -> 400   "message":"Amount must be greater than 0 (got 0)"
POST /api/accounts/1/withdraw?amount=10.555        -> 400   "message":"Amount may have at most 2 decimal places (got 10.555)"

POST /api/accounts/transfer?from=2&to=2&amount=100 -> 400
{"timestamp":"2026-10-11T11:55:29","status":400,"error":"Bad Request","message":"Source and target account must be different","path":"/api/accounts/transfer"}
```

## Failure / edge-case evidence

### Other business and data errors

```text
POST /api/accounts/2/withdraw?amount=999999           -> 422
{"timestamp":"2026-10-11T11:55:29","status":422,"error":"Unprocessable Entity","message":"Insufficient funds in account 2: balance 2500.00, requested 999999","path":"/api/accounts/2/withdraw"}

POST /api/accounts/transfer?from=3&to=1&amount=5000   -> 422
"message":"Insufficient funds in account 3: balance 2000.00, requested 5000"

POST /api/accounts  {"holderName":"Asha Again","email":"ASHA@example.com","openingBalance":100}   -> 409
{"timestamp":"2026-10-11T11:55:29","status":409,"error":"Conflict","message":"A resource with the same unique value already exists","path":"/api/accounts"}
```

For the 409, the client sees a neutral message. The MySQL detail is written **only to the server log**:

```text
WARN  c.c.b.exception.GlobalExceptionHandler : Duplicate key on /api/accounts: Duplicate entry 'asha@example.com' for key 'a13_accounts.uq_a13_accounts_email'
```

### Optional extension: validation handler → 400 with field details

```text
POST /api/accounts  {"holderName":"","email":"bad","openingBalance":-10}   -> 400
{"timestamp":"2026-10-11T11:55:29","status":400,"error":"Bad Request","message":"Validation failed","path":"/api/accounts",
 "validationErrors":{"email":"email must be a valid e-mail address","holderName":"holderName is required","openingBalance":"openingBalance cannot be negative"}}

POST /api/accounts  {}   -> 400
"validationErrors":{"email":"email is required","holderName":"holderName is required","openingBalance":"openingBalance is required"}
```

The response has the same base fields as every other error, plus `validationErrors`.

### Spring MVC's own errors, in the same format

| Request | Status | `message` |
|---|---|---|
| `GET /api/accounts/abc` | **400** | `Parameter 'id' must be a valid int (got 'abc')` |
| `POST /api/accounts/1/withdraw` (no amount) | **400** | `Required parameter 'amount' is missing` |
| `POST /api/accounts/1/withdraw?amount=ten` | **400** | `Parameter 'amount' must be a valid BigDecimal (got 'ten')` |
| `POST /api/accounts` body `{"holderName":"x",` | **400** | `Request body is missing or is not valid JSON` |
| `DELETE /api/accounts/1` | **405** | `Method DELETE is not supported for this URL` |
| `GET /api/nothing-here` | **404** | `No endpoint GET /api/nothing-here` |

### An unexpected error (a bug) → 500, with no stack trace exposed

`GET /api/accounts/2/instalment?months=0` divides by zero. The code deliberately does not guard against this, to demonstrate the safety net. **The client receives:**

```json
{"timestamp":"2026-10-11T11:55:30","status":500,"error":"Internal Server Error","message":"An unexpected error occurred. Please contact support with the errorId.","path":"/api/accounts/2/instalment","errorId":"f4039e41-fa13-42da-b54e-7454a657cf8f"}
```

**The server log** has the full details under the **same** `errorId`:

```text
ERROR c.c.b.exception.GlobalExceptionHandler : errorId=f4039e41-fa13-42da-b54e-7454a657cf8f unexpected error on GET /api/accounts/2/instalment

java.lang.ArithmeticException: / by zero
	at java.base/java.math.BigDecimal.divideAndRound(BigDecimal.java:4675) ~[na:na]
	...
	at com.company.boot.service.AccountService.monthlyInstalment(AccountService.java:83) ~[!/:0.0.1-SNAPSHOT]
	...
	at com.company.boot.controller.AccountController.instalment(AccountController.java:71) ~[!/:0.0.1-SNAPSHOT]
```

**Leak check.** All **25** response bodies from this test run were scanned. There were **0** matches for each of these:
- `at com.`
- `at org.`
- `Exception`
- `trace`
- `java.`
- `SQL`
- `Duplicate entry`
- `uq_a13`

**Data stayed consistent.** The final `GET /api/accounts` shows the balances after only the successful operations:

| Account | Balance |
|---|---|
| 1 | 6500.00 |
| 2 | 2500.00 |
| 3 | 2000.00 |
| 4 | 500.00 |

Every rejected withdraw or transfer left the balances unchanged. `withdraw` and `transfer` are `@Transactional`, so an exception thrown midway would roll back any partial update.

## Explanation

**Centralized exception handling** means one class decides how errors are reported, instead of each controller method having its own `try/catch`.

**How it works:**
1. A controller or service **throws** an exception, e.g. `new ResourceNotFoundException("Account", "id", 99)`.
2. Spring MVC's `DispatcherServlet` catches it and looks for a matching **`@ExceptionHandler`**: first in the controller itself, then in **`@ControllerAdvice`** classes.
3. **The most specific handler wins.** `ResourceNotFoundException` goes to its own handler, while `ArithmeticException` has none and falls through to `@ExceptionHandler(Exception.class)`.
4. The handler returns a `ResponseEntity<ErrorResponse>`, choosing the status and body.

`@ControllerAdvice` is used here. Because every handler returns `ResponseEntity`, the body is written as JSON without `@ResponseBody`. `@RestControllerAdvice` = `@ControllerAdvice` + `@ResponseBody` would work the same way.

**Benefits:**
- **Consistency.** Every error, including Spring's own 400/404/405, has the same JSON shape (`timestamp`, `status`, `error`, `message`, `path`), so clients parse errors one way.
- **Clean code.** `AccountController` has no error code at all, and `AccountService` just throws meaningful exceptions. HTTP concerns stay out of business logic.
- **Correct status codes** in one place:

  | Status | Used for |
  |---|---|
  | 400 | bad input |
  | 404 | missing resource |
  | 405 | wrong method |
  | 409 | conflict |
  | 422 | business rule broken |
  | 500 | bug |

- **Security.** Stack traces, class names and SQL text reveal internals to attackers. The safety-net handler returns a generic message, logs the details, and links the two with an `errorId`. `server.error.include-stacktrace=never` additionally protects Boot's fallback error page.

**A caution.** `IllegalArgumentException` is a very general JDK exception that libraries also throw. Mapping all of them to 400 with their message is convenient for this exercise. In a larger application, prefer your own exception types (like `ResourceNotFoundException`) so the advice handles only errors you meant to expose.
