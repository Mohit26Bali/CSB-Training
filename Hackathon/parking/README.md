# Digital Parking Slot Management System

Admins manage the slots and allocate them; employees see free slots on a floor map, request one with their vehicle number and follow the request until they have a slot. Spring Boot REST API with JWT security, plus a React front end.

## Quick start

You need Java 17 (`JAVA_HOME` must point at it), Node 20+, and MySQL 8 on `localhost:3306`.
The database `parking_slots` is created automatically (user `root`, password `mohit123` by default).

```bash
# terminal 1: backend  (http://localhost:8106)
cd backend
./mvnw spring-boot:run          # PowerShell: .\mvnw spring-boot:run

# terminal 2: front end  (http://localhost:5106)
cd frontend
npm install                     # first time only
npm run dev
```

Open <http://localhost:5106>. The sun/moon button switches between light and dark mode.
On first start the backend seeds demo users and sample data.

### Demo logins

| Username | Password | Role |
|---|---|---|
| `parkadmin` | `admin123` | PARKING_ADMIN |
| `lata.deshmukh` | `admin123` | PARKING_ADMIN |
| `aarav.sharma` (no slot, no request: try the whole flow) | `employee123` | EMPLOYEE |
| `diya.patel` (has a slot), `rohan.mehta` (pending request), … (30 employees) | `employee123` | EMPLOYEE |

## Features

- **Employee:** see the free slots as a colour-coded floor map (filter by floor); request a slot with a vehicle number, an optional preferred floor and a note; see the position in the queue; cancel a pending request; see the allocated slot and vehicle, or the reason a request was rejected.
- **Parking admin:** create and edit slots (code, floor, available / reserved / closed); the whole lot as a clickable map; review requests (pending ones are the queue, oldest first) and allocate a slot by clicking it on the map, or reject with a reason; release a slot when the allocation ends; a floor-wise occupancy dashboard.
- Vehicle number registration, floor-wise filtering and occupancy dashboard (the case's bonus features).

## Business rules

| Rule | Result |
|---|---|
| A slot has at most one active allocation | `409` |
| An employee has at most one active slot, and one pending request at a time | `409` |
| Only AVAILABLE slots can be allocated (reserved and closed slots cannot) | `400` |
| A slot that is in use cannot be marked reserved or closed until it is released | `400` |
| A vehicle number is tidied up (`mh 12-ab 1234` becomes `MH12AB1234`), must be 6 to 12 letters or digits and cannot be parked in two slots | `400` field error / `409` |
| Slot codes are unique (case and spaces ignored) | `400` field error |
| A decided request cannot be decided again; only a pending request can be cancelled, and only by its owner | `409` / `403` |
| Rejecting needs a reason, which the employee sees | `400` field error |
| Only admins manage slots and decide; only employees request | `403` |

**Concurrency:** approving locks the slot row, then the employee row, and only then loads the request (under `READ_COMMITTED`), so two admins cannot give out the same slot, one request cannot be approved into two slots, and a double-clicked request creates one request. The tests check all three with parallel threads.

Employees only see which slots are free, never who holds the others. The admin map shows occupant and vehicle on hover.
Whether a slot is occupied is derived from the allocations table; the slot's own status is only AVAILABLE, RESERVED or MAINTENANCE.

## API

All endpoints except login and register need `Authorization: Bearer <token>`.

| Method | URL | Role | Purpose |
|---|---|---|---|
| POST | /api/auth/login | public | Log in, returns a token |
| POST | /api/auth/register | public | Create an account (always the lowest role) |
| GET | /api/auth/me | any | Current user |
| GET | /api/slots?floor= | any | Free slots (available and not held) |
| GET | /api/slots/floors | any | Floors that have slots |
| POST | /api/parking/requests | EMPLOYEE | Ask `{vehicleNumber, preferredFloor?, note?}` |
| GET | /api/parking/requests/my | EMPLOYEE | Own requests with queue position |
| DELETE | /api/parking/requests/{id} | EMPLOYEE | Cancel a pending request |
| GET | /api/parking/allocation/my | EMPLOYEE | Current slot (`204` when none) |
| GET | /api/admin/slots?floor= | PARKING_ADMIN | All slots with occupants |
| POST | /api/admin/slots | PARKING_ADMIN | Create a slot `{code, floor, status?}` |
| PUT | /api/admin/slots/{id} | PARKING_ADMIN | Edit a slot or change its status |
| GET | /api/admin/requests?status= | PARKING_ADMIN | Requests (pending = queue) |
| PUT | /api/admin/requests/{id}/approve | PARKING_ADMIN | Allocate `{slotId}` |
| PUT | /api/admin/requests/{id}/reject | PARKING_ADMIN | Reject `{reason}` |
| GET | /api/admin/allocations?activeOnly= | PARKING_ADMIN | Allocations (current or all) |
| PUT | /api/admin/allocations/{id}/release | PARKING_ADMIN | End an allocation |
| GET | /api/admin/dashboard | PARKING_ADMIN | Occupancy totals and per floor |

Errors share one shape, and validation errors list each bad field so the UI can mark the right input:

```json
{ "status": 400, "error": "Bad Request", "message": "...", "fieldErrors": { "field": "..." }, "timestamp": "..." }
```

## Architecture

```
React (5106) / Postman  ->  Controller  ->  Service  ->  Repository  ->  MySQL
```

```
backend/src/main/java/com/hackathon/parking
├── config        SecurityConfig (JWT filter chain, CORS), DataInitializer (demo data)
├── security      JwtService, JwtAuthenticationFilter, JSON 401/403 handlers
├── controller    AuthController, ParkingController (employee), AdminController
├── service       ParkingService (slots, requests, allocation, locking, dashboard), AuthService, LoginAttemptService
├── repo          UserRepository (row lock), SlotRepository (row lock), ParkingRequestRepository, ParkingAllocationRepository
├── entity        User, ParkingSlot, ParkingRequest, ParkingAllocation, Role, SlotStatus, RequestStatus
├── dto           requests and responses, ErrorResponse
└── exception     GlobalExceptionHandler and the business exceptions
frontend/src      api (typed client) · auth (session, route guard) · components · pages · config.ts (branding, nav, demo accounts)
```

Controllers handle HTTP only, services hold the business rules, repositories only query.
Data processing in the services uses Java Streams. Tables are created by Hibernate: `users`, `parking_slots` (unique code), `parking_requests` (employee, vehicle, status, decision, slot) and `parking_allocations` (slot, employee, vehicle, released-at).

## Tests

```bash
cd backend
./mvnw test        # 24 tests on in-memory H2 (no MySQL needed): vehicle numbers, queue, allocation rules, release, slot rules, dashboard, roles, parallel approvals
```

API walkthrough with the Postman CLI (backend must be running; the run is repeatable):

```bash
postman collection run docs/postman/Parking.postman_collection.json
```

It runs 54 requests (84 assertions). It registers fresh employees and creates uniquely coded slots on floor 7 each run, so it can be run many times. You can also import the file into the Postman app.

## Configuration

Environment variables override the defaults in `application.properties`:
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` (**set your own, 32+ characters, outside local use**), `CORS_ORIGINS`.

## Images

The login-page photos in `frontend/public/hero` come from [Pexels](https://www.pexels.com) (free licence)
and were downloaded with `../tools/fetch_images.py` using `images.json` (needs `PEXELS_API_KEY`).

## Known limitations

- One vehicle per employee; changing slots means releasing first.
- No time-limited or hourly bookings, only long-term allocations.
- Filtering and the queue are computed in memory (Java Streams), which is fine for one office car park.
- Login throttling is in memory, so it resets on restart.
- The JWT is kept in `sessionStorage`; a production system would use an httpOnly cookie.
