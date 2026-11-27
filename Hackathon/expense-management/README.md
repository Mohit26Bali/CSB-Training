# Smart Employee Expense Management System

Employees submit business expenses; managers review them and approve or reject (a rejection needs a reason).
Spring Boot REST API with JWT security, plus a React front end.

## Quick start

You need Java 17 (`JAVA_HOME` must point at it), Node 20+, and MySQL 8 on `localhost:3306`.
The database `expense_management` is created automatically (user `root`, password `mohit123` by default).

```bash
# terminal 1: backend  (http://localhost:8111)
cd backend
./mvnw spring-boot:run          # PowerShell: .\mvnw spring-boot:run

# terminal 2: front end  (http://localhost:5111)
cd frontend
npm install                     # first time only
npm run dev
```

Open <http://localhost:5111>. The sun/moon button switches between light and dark mode.
On first start the backend seeds demo users and about 40 expenses.

### Demo logins

| Username | Password | Role |
|---|---|---|
| `manager` | `manager123` | MANAGER |
| `neha.kapoor` | `manager123` | MANAGER |
| `aarav.sharma`, `diya.patel`, `rohan.mehta`, `ananya.iyer`, … (12 employees) | `employee123` | EMPLOYEE |

## Features

- **Secure login** with JWT and role-based access. New sign-ups are always EMPLOYEE.
- **Employee:** submit an expense (date, category, amount, description), see only their own expenses and status,
  filter by status or category, search, see a summary of pending / approved / rejected totals, and read the
  manager's reason when an expense is rejected.
- **Manager:** a review queue of pending expenses, approve or reject (reason required), see who decided and when,
  filter and search by employee, category or text, and a dashboard with totals and approved spend by category.

## Business rules

| Rule | Result |
|---|---|
| Amount must be greater than zero (at most 2 decimal places) | `400` with a field error |
| Expense date cannot be in the future | `400` with a field error |
| Category must be one of the listed categories | `400` with a field error |
| Rejection requires a reason | `400` with a field error |
| Only managers can approve or reject | `403` for employees |
| A processed expense cannot be processed again | `409` |
| A manager cannot decide their own expense | `403` |
| Employees see only their own expenses | list is filtered by the logged-in user |

**Concurrency:** deciding an expense takes a row lock (under `READ_COMMITTED`), so two managers clicking at the
same moment cannot both process it. The test suite checks this with parallel threads.

## API

All endpoints except login and register need `Authorization: Bearer <token>`.

| Method | URL | Role | Purpose |
|---|---|---|---|
| POST | `/api/auth/login` | public | Log in, returns a token |
| POST | `/api/auth/register` | public | Create an employee account |
| GET | `/api/auth/me` | any | Current user |
| GET | `/api/expenses/categories` | any | Category values and labels |
| POST | `/api/expenses` | EMPLOYEE | Submit `{expenseDate, category, amount, description}` |
| GET | `/api/expenses/my?status=&category=&search=` | EMPLOYEE | Own expenses, newest first |
| GET | `/api/expenses/my/summary` | EMPLOYEE | Counts and totals per status |
| GET | `/api/manager/expenses?status=&category=&search=` | MANAGER | All expenses |
| PUT | `/api/manager/expenses/{id}/approve` | MANAGER | Approve a pending expense |
| PUT | `/api/manager/expenses/{id}/reject` | MANAGER | Reject with `{"reason": "..."}` |
| GET | `/api/manager/dashboard` | MANAGER | Summary, approved spend by category, oldest pending |

Errors share one shape, and validation errors list each bad field so the UI can mark the right input:

```json
{ "status": 400, "error": "Bad Request", "message": "Amount must be greater than zero",
  "fieldErrors": { "amount": "Amount must be greater than zero" }, "timestamp": "2026-10-30T17:20:00" }
```

## Architecture

```
React (5111) / Postman  ->  Controller  ->  Service  ->  Repository  ->  MySQL
```

```
backend/src/main/java/com/hackathon/expense
├── config        SecurityConfig (JWT filter chain, CORS), DataInitializer (demo data)
├── security      JwtService, JwtAuthenticationFilter, JSON 401/403 handlers
├── controller    AuthController, ExpenseController (employee), ManagerExpenseController
├── service       ExpenseService (all business rules), AuthService, LoginAttemptService
├── repo          UserRepository, ExpenseRepository (row-locking query)
├── entity        User, Expense, Role, ExpenseStatus, ExpenseCategory
├── dto           requests and responses, ErrorResponse
└── exception     GlobalExceptionHandler and the business exceptions
frontend/src      api (typed client) · auth (session, route guard) · components · pages/{employee,manager}
```

Tables: `users` (unique username, BCrypt password, role, enabled) and `expenses` (employee FK, date, category, amount
`DECIMAL(12,2)`, description, status, submitted/decided timestamps, `decided_by`, `rejection_reason`), created by Hibernate.

## Tests

```bash
cd backend
./mvnw test        # 23 tests on in-memory H2 (no MySQL needed): rules, visibility, roles, concurrency, validation
```

API walkthrough with the Postman CLI (backend must be running; the run is repeatable):

```bash
postman collection run docs/postman/ExpenseManagement.postman_collection.json
```

It runs 36 requests (64 assertions). You can also import the file into the Postman app.

## Configuration

Environment variables override the defaults in `application.properties`:
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` (**set your own, 32+ characters, outside local use**), `CORS_ORIGINS`.

## Images

The login-page photos in `frontend/public/hero` come from [Pexels](https://www.pexels.com) (free licence) and were
downloaded with `../tools/fetch_images.py` using `images.json` (needs `PEXELS_API_KEY`).

## Known limitations

- Login throttling is in memory, so it resets on restart.
- The JWT is kept in `sessionStorage`; a production system would use an httpOnly cookie.
- Filtering and the dashboard are computed in memory (Java Streams). That is fine for a team's expenses, but a
  large dataset would need database-side queries and paging.
- Managers cannot submit their own expenses (the spec gives only employees that action).
