# Asset Allocation System

IT keeps a register of laptops, monitors, phones and other equipment, hands each item to one employee at a time, and checks it back in when the employee raises a return request. Every asset keeps its full assignment history. Spring Boot REST API with JWT security, plus a React front end.

## Quick start

You need Java 17 (`JAVA_HOME` must point at it), Node 20+, and MySQL 8 on `localhost:3306`.
The database `asset_allocation` is created automatically (user `root`, password `mohit123` by default).

```bash
# terminal 1: backend  (http://localhost:8115)
cd backend
./mvnw spring-boot:run          # PowerShell: .\mvnw spring-boot:run

# terminal 2: front end  (http://localhost:5115)
cd frontend
npm install                     # first time only
npm run dev
```

Open <http://localhost:5115>. The sun/moon button switches between light and dark mode.
On first start the backend seeds demo users and sample data.

### Demo logins

| Username | Password | Role |
|---|---|---|
| `assetadmin` | `admin123` | ASSET_ADMIN |
| `it.desk` | `admin123` | ASSET_ADMIN |
| `aarav.sharma` (holds a MacBook Pro and a monitor, has asked to return a headset, once returned a Dell laptop) | `employee123` | EMPLOYEE |
| `diya.patel`, `rohan.mehta`, … (30 employees) | `employee123` | EMPLOYEE |

## Features

- **Asset admin:** a register of assets (tag, serial number, category, brand, model, purchase date and price, condition), search by serial number, tag, name or holder with status and category filters, assign an available asset to an employee (optional due date), a return queue with check-in and the condition it came back in, the full assignment history of every asset, sending assets to repair and retiring them, and a dashboard (counts, overdue, poor condition, value, breakdowns, oldest requests).
- **Employee:** the equipment they hold with due dates, a return request with a note, and a history of what they handed back.
- Asset search by serial number and condition tracking (the case's bonus features).

## Business rules

| Rule | Result |
|---|---|
| Only AVAILABLE assets can be assigned; an asset cannot be held by two employees (the message says who has it) | `409` |
| An accepted return makes the asset AVAILABLE again, with the condition recorded at check-in | status change |
| The asset tag (like LT-1001) and the serial number are unique, whatever the letter case | `400` field errors |
| Assets go only to active employees; one employee holds at most 5 assets; the due date must be after today | `400` |
| Only the holder raises a return request, once; only an admin accepts it, and only after it was raised | `403` / `409` |
| AVAILABLE and IN_REPAIR can be swapped; either can be retired for good; held assets must come back first | `400` / `409` |
| A retired asset is read-only; the purchase date cannot be in the future; the price cannot be negative | `400` |

**Concurrency:** assigning, requesting a return, accepting it and every status change take a row lock on the asset (under `READ_COMMITTED`, and before the assignment is loaded), assigning also locks the employee, so five admins handing one laptop to five people make one assignment, seven assets assigned to one employee at once stop at five, and a double-clicked return request or a double-clicked check-in is recorded once. The tests check this with parallel threads.

Photos are looked up by category (`/assets/<category>.jpg`); without one a line icon is shown on a stone tile.
Accepting a return always makes the asset available, even in POOR condition; the admin can then send it to repair.

## API

All endpoints except login and register need `Authorization: Bearer <token>`.

| Method | URL | Role | Purpose |
|---|---|---|---|
| POST | /api/auth/login | public | Log in, returns a token |
| POST | /api/auth/register | public | Create an account (always the lowest role) |
| GET | /api/auth/me | any | Current user |
| GET | /api/assets/meta | any signed-in user | Category and condition values for forms |
| GET | /api/my/assets | EMPLOYEE | Equipment held now |
| GET | /api/my/history | EMPLOYEE | Equipment handed back |
| POST | /api/my/assignments/{id}/return | EMPLOYEE | Raise a return request `{note?}` |
| GET | /api/admin/assets?status=&category=&search= | ASSET_ADMIN | The register |
| POST | /api/admin/assets | ASSET_ADMIN | Register an asset |
| PUT | /api/admin/assets/{id} | ASSET_ADMIN | Edit an asset |
| PUT | /api/admin/assets/{id}/status | ASSET_ADMIN | Repair, repaired or retire `{status}` |
| POST | /api/admin/assets/{id}/assign | ASSET_ADMIN | Assign `{employeeUsername, dueDate?, note?}` |
| GET | /api/admin/assets/{id}/history | ASSET_ADMIN | Every assignment of the asset |
| GET | /api/admin/assignments?status= | ASSET_ADMIN | Return requests (default) or assets out with staff |
| PUT | /api/admin/assignments/{id}/accept | ASSET_ADMIN | Check in `{condition, note?}` |
| GET | /api/admin/employees?search= | ASSET_ADMIN | Employees with the number of assets held |
| GET | /api/admin/dashboard | ASSET_ADMIN | Counts, value, breakdowns, oldest requests |

Errors share one shape, and validation errors list each bad field so the UI can mark the right input:

```json
{ "status": 400, "error": "Bad Request", "message": "...", "fieldErrors": { "field": "..." }, "timestamp": "..." }
```

## Architecture

```
React (5115) / Postman  ->  Controller  ->  Service  ->  Repository  ->  MySQL
```

```
backend/src/main/java/com/hackathon/asset
├── config        SecurityConfig (JWT filter chain, CORS), DataInitializer (demo data)
├── security      JwtService, JwtAuthenticationFilter, JSON 401/403 handlers
├── controller    AuthController, EmployeeController, AdminController
├── service       AssetService (register, assigning, returns, history, dashboard), AuthService, LoginAttemptService
├── repo          UserRepository (row lock), AssetRepository (row lock), AssignmentRepository
├── entity        User, Asset, Assignment, Role, AssetCategory, AssetCondition, AssetStatus, AssignmentStatus
├── dto           requests and responses, ErrorResponse
└── exception     GlobalExceptionHandler and the business exceptions
frontend/src      api (typed client) · auth (session, route guard) · components · pages · config.ts (branding, nav, demo accounts)
```

Controllers handle HTTP only, services hold the business rules, repositories only query.
Data processing in the services uses Java Streams. Tables are created by Hibernate: `users`, `assets` (unique tag and serial, condition, status) and `assignments` (asset and employee FKs, due date, condition out and in, return request, who handed over and who received).

## Tests

```bash
cd backend
./mvnw test        # 29 tests on in-memory H2 (no MySQL needed): register rules, search, assigning, the five-asset limit, return flow, history, repair and retirement, dashboard, roles, parallel assignments and returns
```

API walkthrough with the Postman CLI (backend must be running; the run is repeatable):

```bash
postman collection run docs/postman/Asset.postman_collection.json
```

It runs 62 requests (99 assertions). It registers a fresh employee and assets with a unique tag and serial each run, so it can be run many times. You can also import the file into the Postman app.

## Configuration

Environment variables override the defaults in `application.properties`:
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` (**set your own, 32+ characters, outside local use**), `CORS_ORIGINS`.

## Images

The login-page photos in `frontend/public/hero` come from [Pexels](https://www.pexels.com) (free licence)
and were downloaded with `../tools/fetch_images.py` using `images.json` (needs `PEXELS_API_KEY`).

## Known limitations

- No bulk import, barcode scanning or depreciation.
- The register is filtered in memory (Java Streams), which suits a company of this size but would need paging for tens of thousands of assets.
- Login throttling is in memory, so it resets on restart.
- The JWT is kept in `sessionStorage`; a production system would use an httpOnly cookie.
