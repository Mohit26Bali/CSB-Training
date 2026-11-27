# Product Warranty Claim System

Customers register what they have bought and raise warranty claims; service agents review the claims, approve or reject them, and move approved claims through repair to completion. Spring Boot REST API with JWT security, plus a React front end.

## Quick start

You need Java 17 (`JAVA_HOME` must point at it), Node 20+, and MySQL 8 on `localhost:3306`.
The database `warranty_claims` is created automatically (user `root`, password `mohit123` by default).

```bash
# terminal 1: backend  (http://localhost:8107)
cd backend
./mvnw spring-boot:run          # PowerShell: .\mvnw spring-boot:run

# terminal 2: front end  (http://localhost:5107)
cd frontend
npm install                     # first time only
npm run dev
```

Open <http://localhost:5107>. The sun/moon button switches between light and dark mode.
On first start the backend seeds demo users and sample data.

### Demo logins

| Username | Password | Role |
|---|---|---|
| `agent` | `agent123` | SERVICE_AGENT |
| `sunil.agarwal` | `agent123` | SERVICE_AGENT |
| `aarav.sharma`, `diya.patel`, `rohan.mehta`, `ananya.iyer`, … (10 customers) | `customer123` | CUSTOMER |

## Features

- **Customer:** register a purchased product (brand, serial number, purchase date, warranty months) and see how long the warranty lasts (covered, expiring within 30 days, or expired); file a claim with an issue description and a priority; follow every claim through PENDING → APPROVED → IN_REPAIR → COMPLETED (or REJECTED, with the reason) and read the resolution note.
- **Service agent:** a claims queue where pending claims are ordered high priority first, then longest waiting; approve, reject (reason required), start repair, and complete with an optional resolution note; search by product, serial number or customer; filter by status and priority; status counts on the tabs.
- Warranty expiry calculation, claim priority and product search (the case's bonus features), and a photo per product category.

## Business rules

| Rule | Result |
|---|---|
| Only products whose warranty is still active (the last covered day included) can generate a claim | `400`, the message gives the expiry date |
| A product cannot have two active claims (PENDING, APPROVED or IN_REPAIR); completed or rejected claims do not block a new one | `409` |
| Rejection requires a reason | `400` with a field error |
| Serial numbers are unique (case-insensitive) across all customers | `400` with a field error |
| Purchase date cannot be in the future or more than 10 years ago; warranty is 1 to 60 months | `400` with a field error |
| Claim steps follow the workflow: approve or reject only PENDING, repair only APPROVED, complete only IN_REPAIR | `409` |
| Customers can claim only on their own products (someone else's product looks like "not found") | `404` |
| Only agents decide, and only customers register products and file claims | `403` otherwise |

**Concurrency:** filing a claim first locks the product row (under `READ_COMMITTED`), so a double click, or two devices, cannot create two active claims for one product; every agent action locks the claim row, so two agents cannot decide the same claim. The tests check both with parallel threads.

**Warranty end** = purchase date + warranty months, and that day is still covered. A product is "expiring soon" when 30 days or fewer remain.

## API

All endpoints except login and register need `Authorization: Bearer <token>`.

| Method | URL | Role | Purpose |
|---|---|---|---|
| POST | /api/auth/login | public | Log in, returns a token |
| POST | /api/auth/register | public | Create an account (always the lowest role) |
| GET | /api/auth/me | any | Current user |
| GET | /api/products/categories | any | Product categories |
| POST | /api/products | CUSTOMER | Register `{name, brand, category, serialNumber, purchaseDate, warrantyMonths}` |
| GET | /api/products/my?search= | CUSTOMER | Own products with warranty status |
| POST | /api/claims | CUSTOMER | File `{productId, issueDescription, priority?}` |
| GET | /api/claims/my?status= | CUSTOMER | Own claims |
| GET | /api/agent/claims?status=&priority=&search= | SERVICE_AGENT | The claims queue |
| GET | /api/agent/summary | SERVICE_AGENT | Count per status |
| PUT | /api/agent/claims/{id}/approve | SERVICE_AGENT | Approve a pending claim |
| PUT | /api/agent/claims/{id}/reject | SERVICE_AGENT | Reject with `{reason}` |
| PUT | /api/agent/claims/{id}/start-repair | SERVICE_AGENT | APPROVED → IN_REPAIR |
| PUT | /api/agent/claims/{id}/complete | SERVICE_AGENT | IN_REPAIR → COMPLETED, optional `{resolutionNote}` |

Errors share one shape, and validation errors list each bad field so the UI can mark the right input:

```json
{ "status": 400, "error": "Bad Request", "message": "...", "fieldErrors": { "field": "..." }, "timestamp": "..." }
```

## Architecture

```
React (5107) / Postman  ->  Controller  ->  Service  ->  Repository  ->  MySQL
```

```
backend/src/main/java/com/hackathon/warranty
├── config        SecurityConfig (JWT filter chain, CORS), DataInitializer (demo data)
├── security      JwtService, JwtAuthenticationFilter, JSON 401/403 handlers
├── controller    AuthController, CustomerController (products and claims), AgentController
├── service       ProductService, ClaimService (all claim rules), AuthService, LoginAttemptService
├── repo          UserRepository, ProductRepository (row lock), ClaimRepository (row lock)
├── entity        User, Product, Claim, Role, ClaimStatus, ClaimPriority, ProductCategory
├── dto           requests and responses, ErrorResponse
└── exception     GlobalExceptionHandler and the business exceptions
frontend/src      api (typed client) · auth (session, route guard) · components · pages · config.ts (branding, nav, demo accounts)
```

Controllers handle HTTP only, services hold the business rules, repositories only query.
Data processing in the services uses Java Streams. Tables are created by Hibernate: `users`, `products` (owner FK, **UNIQUE serial number**, purchase date, warranty months) and `claims` (product and customer FKs, status, priority, decision / repair / completion fields).

## Tests

```bash
cd backend
./mvnw test        # 29 tests on in-memory H2 (no MySQL needed): warranty maths, expiry, duplicate claims, ownership, workflow steps, queue order, roles, concurrency
```

API walkthrough with the Postman CLI (backend must be running; the run is repeatable):

```bash
postman collection run docs/postman/Warranty.postman_collection.json
```

It runs 49 requests (80 assertions). It registers a fresh customer and uses unique serial numbers, so it can be run many times. You can also import the file into the Postman app.

## Configuration

Environment variables override the defaults in `application.properties`:
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` (**set your own, 32+ characters, outside local use**), `CORS_ORIGINS`.

## Images

The login-page photos in `frontend/public/hero` and the product pictures in `frontend/public/products` (`images-products.json`) come from [Pexels](https://www.pexels.com) (free licence)
and were downloaded with `../tools/fetch_images.py` using `images.json` (needs `PEXELS_API_KEY`).

## Known limitations

- A customer cannot withdraw a claim after filing it, and cannot edit or remove a registered product.
- Warranty is a simple period from the purchase date; extended warranties and proof of purchase uploads are not modelled.
- Filtering is done in memory (Java Streams), which is fine for one service centre but would need paging at scale.
- Login throttling is in memory, so it resets on restart.
- The JWT is kept in `sessionStorage`; a production system would use an httpOnly cookie.
