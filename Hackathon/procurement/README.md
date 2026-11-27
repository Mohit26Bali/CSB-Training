# Simple Procurement Request System

Employees request office items and software; procurement managers approve or reject within a monthly budget, then mark approved requests as ordered and delivered. Spring Boot REST API with JWT security, plus a React front end.

## Quick start

You need Java 17 (`JAVA_HOME` must point at it), Node 20+, and MySQL 8 on `localhost:3306`.
The database `procurement_requests` is created automatically (user `root`, password `mohit123` by default).

```bash
# terminal 1: backend  (http://localhost:8119)
cd backend
./mvnw spring-boot:run          # PowerShell: .\mvnw spring-boot:run

# terminal 2: front end  (http://localhost:5119)
cd frontend
npm install                     # first time only
npm run dev
```

Open <http://localhost:5119>. The sun/moon button switches between light and dark mode.
On first start the backend seeds demo users and sample data.

### Demo logins

| Username | Password | Role |
|---|---|---|
| `procurement` | `manager123` | PROCUREMENT_MANAGER |
| `kiran.shah` | `manager123` | PROCUREMENT_MANAGER |
| `aarav.sharma`, `diya.patel`, `rohan.mehta`, `ananya.iyer`, … (12 employees) | `employee123` | EMPLOYEE |

## Features

- **Employee:** create a request (item, category, quantity, estimated cost per unit, justification) with a live total; track only their own requests through PENDING → APPROVED → ORDERED → DELIVERED (or REJECTED, with the reason); filter by status and category and search; a summary of counts and totals per status.
- **Procurement manager:** review queue; approve, reject (reason required), mark as ordered (with an optional purchase-order reference) and mark as delivered; a dashboard with the month's budget, spend by category and the oldest pending request.
- Budget limit validation, category filtering and a spending dashboard (the case's bonus features).

## Business rules

| Rule | Result |
|---|---|
| Quantity must be a positive whole number (at most 10,000) | `400` with a field error |
| Estimated cost must be greater than zero (at most 2 decimal places) | `400` with a field error |
| Only procurement managers can approve, reject, order or deliver | `403` for employees |
| Rejection requires a reason | `400` with a field error |
| A rejected (or still pending) request cannot be ordered; delivery needs ORDERED; no step can be repeated | `409` |
| Approval is refused if it would take this month's approved spend over the monthly budget | `409`, the message names the amount left |
| A manager cannot decide their own request | `403` |
| Employees see only their own requests | list is filtered by the logged-in user |

**Concurrency:** approving first locks a single-row "budget gate" and then the request row, under `READ_COMMITTED`, so two approvals can never both fit under the same remaining budget (the tests race five 30,000 approvals against a 100,000 budget and exactly three win), and two managers cannot decide the same request.

The cost entered is **per unit**; the total is quantity × unit cost. Spend counts in the month a request was approved. The monthly limit defaults to ₹500,000 and is set with the `MONTHLY_BUDGET` environment variable.

## API

All endpoints except login and register need `Authorization: Bearer <token>`.

| Method | URL | Role | Purpose |
|---|---|---|---|
| POST | /api/auth/login | public | Log in, returns a token |
| POST | /api/auth/register | public | Create an account (always the lowest role) |
| GET | /api/auth/me | any | Current user |
| GET | /api/requests/categories | any | Categories |
| POST | /api/requests | EMPLOYEE | Create `{itemName, category, quantity, estimatedUnitCost, justification}` |
| GET | /api/requests/my?status=&category=&search= | EMPLOYEE | Own requests, newest first |
| GET | /api/requests/my/summary | EMPLOYEE | Count and total per status |
| GET | /api/manager/requests?status=&category=&search= | PROCUREMENT_MANAGER | All requests |
| PUT | /api/manager/requests/{id}/approve | PROCUREMENT_MANAGER | Approve (budget checked) |
| PUT | /api/manager/requests/{id}/reject | PROCUREMENT_MANAGER | Reject with `{reason}` |
| PUT | /api/manager/requests/{id}/order | PROCUREMENT_MANAGER | Mark ordered, optional `{orderReference}` |
| PUT | /api/manager/requests/{id}/deliver | PROCUREMENT_MANAGER | Mark delivered |
| GET | /api/manager/dashboard | PROCUREMENT_MANAGER | Budget, status totals, spend by category |

Errors share one shape, and validation errors list each bad field so the UI can mark the right input:

```json
{ "status": 400, "error": "Bad Request", "message": "...", "fieldErrors": { "field": "..." }, "timestamp": "..." }
```

## Architecture

```
React (5119) / Postman  ->  Controller  ->  Service  ->  Repository  ->  MySQL
```

```
backend/src/main/java/com/hackathon/procurement
├── config        SecurityConfig (JWT filter chain, CORS), DataInitializer (demo data)
├── security      JwtService, JwtAuthenticationFilter, JSON 401/403 handlers
├── controller    AuthController, RequestController (employee), ManagerRequestController
├── service       ProcurementService (all rules), AuthService, LoginAttemptService
├── repo          UserRepository, PurchaseRequestRepository (row lock), BudgetGateRepository (budget lock)
├── entity        User, PurchaseRequest, BudgetGate, Role, RequestStatus, ItemCategory
├── dto           requests and responses, ErrorResponse
└── exception     GlobalExceptionHandler and the business exceptions
frontend/src      api (typed client) · auth (session, route guard) · components · pages · config.ts (branding, nav, demo accounts)
```

Controllers handle HTTP only, services hold the business rules, repositories only query.
Data processing in the services uses Java Streams. Tables are created by Hibernate: `users`, `purchase_requests` (employee FK, item, category, quantity, unit cost, status, decision / order / delivery fields) and `budget_gate`.

## Tests

```bash
cd backend
./mvnw test        # 24 tests on in-memory H2 (no MySQL needed): rules, visibility, roles, the order/deliver workflow, budget limit, concurrency
```

API walkthrough with the Postman CLI (backend must be running; the run is repeatable):

```bash
postman collection run docs/postman/Procurement.postman_collection.json
```

It runs 40 requests (66 assertions). It registers a fresh employee and uses tiny amounts, so it can be run many times. You can also import the file into the Postman app.

## Configuration

Environment variables override the defaults in `application.properties`:
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` (**set your own, 32+ characters, outside local use**), `CORS_ORIGINS`.

## Images

The login-page photos in `frontend/public/hero` come from [Pexels](https://www.pexels.com) (free licence)
and were downloaded with `../tools/fetch_images.py` using `images.json` (needs `PEXELS_API_KEY`).

## Known limitations

- One company-wide monthly budget; there are no per-department budgets.
- Filtering is done in memory (Java Streams), which is fine for one company's requests but would need paging at scale.
- Employees cannot edit or withdraw a request after submitting it.
- Login throttling is in memory, so it resets on restart.
- The JWT is kept in `sessionStorage`; a production system would use an httpOnly cookie.
