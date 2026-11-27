# Hostel Room Allocation System

Wardens manage rooms and allocate beds; students browse free rooms, send a request and follow it from the waiting list to their room. Spring Boot REST API with JWT security, plus a React front end.

## Quick start

You need Java 17 (`JAVA_HOME` must point at it), Node 20+, and MySQL 8 on `localhost:3306`.
The database `hostel_allocation` is created automatically (user `root`, password `mohit123` by default).

```bash
# terminal 1: backend  (http://localhost:8103)
cd backend
./mvnw spring-boot:run          # PowerShell: .\mvnw spring-boot:run

# terminal 2: front end  (http://localhost:5103)
cd frontend
npm install                     # first time only
npm run dev
```

Open <http://localhost:5103>. The sun/moon button switches between light and dark mode.
On first start the backend seeds demo users and sample data.

### Demo logins

| Username | Password | Role |
|---|---|---|
| `warden` | `admin123` | HOSTEL_ADMIN |
| `mahesh.kulkarni` | `admin123` | HOSTEL_ADMIN |
| `aarav.sharma` (no room, no request: try the whole flow) | `student123` | STUDENT |
| `diya.patel` (has a room), `rohan.mehta` (pending request), … (30 students) | `student123` | STUDENT |

## Features

- **Student:** see rooms with a free bed (filter by type, with a photo and beds left); request a room with an optional preferred type and note; see the position on the waiting list; cancel a pending request; see the current room allocation and the reason for any rejection.
- **Warden:** create and edit rooms (number, floor, type, capacity, open or closed for maintenance); see every room with its occupants; review requests (pending ones are the waiting list, oldest first); allocate a room of choice or reject with a reason; vacate a room when a student leaves; an occupancy dashboard by room type.
- Room type filtering, occupancy dashboard and waiting list (the case's bonus features).

## Business rules

| Rule | Result |
|---|---|
| Room numbers are unique (case and spaces ignored) | `400` field error |
| Capacity is 1 to 12 beds and can never be lowered below the number of residents | `400` field error |
| A student can have only one pending request, and none while they already have a room | `409` |
| A room can only be given while it is open and has a free bed | `400` (closed) / `409` (full) |
| A student can only ever live in one room at a time | `409` |
| A decided request cannot be decided again; only a pending request can be cancelled, and only by its owner | `409` / `403` |
| Rejecting needs a reason, which the student sees | `400` field error |
| Only wardens manage rooms and decide; only students request | `403` |

**Concurrency:** approving locks the room row, then the student row, and only then loads the request (under `READ_COMMITTED`), so two wardens cannot fill the last bed twice, one request cannot be approved into two rooms, and a double-clicked request creates one request. The tests check all three with parallel threads.

Students only see how many beds are free in a room, never who lives there. Only the warden sees occupants.
Rooms under maintenance (closed) are left out of the free-bed list and of the occupancy percentage.

## API

All endpoints except login and register need `Authorization: Bearer <token>`.

| Method | URL | Role | Purpose |
|---|---|---|---|
| POST | /api/auth/login | public | Log in, returns a token |
| POST | /api/auth/register | public | Create an account (always the lowest role) |
| GET | /api/auth/me | any | Current user |
| GET | /api/rooms?type= | any | Active rooms with a free bed |
| GET | /api/rooms/types | any | Room types |
| POST | /api/requests | STUDENT | Ask for a room `{preferredType?, note?}` |
| GET | /api/requests/my | STUDENT | Own requests with waiting-list position |
| DELETE | /api/requests/{id} | STUDENT | Cancel a pending request |
| GET | /api/allocation/my | STUDENT | Current room (`204` when none) |
| GET | /api/admin/rooms?type= | HOSTEL_ADMIN | All rooms with occupants |
| POST | /api/admin/rooms | HOSTEL_ADMIN | Create a room `{number, floor, type, capacity, active?}` |
| PUT | /api/admin/rooms/{id} | HOSTEL_ADMIN | Edit a room |
| GET | /api/admin/requests?status= | HOSTEL_ADMIN | Requests (pending = waiting list) |
| PUT | /api/admin/requests/{id}/approve | HOSTEL_ADMIN | Allocate `{roomId}` |
| PUT | /api/admin/requests/{id}/reject | HOSTEL_ADMIN | Reject `{reason}` |
| GET | /api/admin/allocations?activeOnly= | HOSTEL_ADMIN | Residents (current or all) |
| PUT | /api/admin/allocations/{id}/vacate | HOSTEL_ADMIN | The student leaves the room |
| GET | /api/admin/dashboard | HOSTEL_ADMIN | Occupancy totals and by room type |

Errors share one shape, and validation errors list each bad field so the UI can mark the right input:

```json
{ "status": 400, "error": "Bad Request", "message": "...", "fieldErrors": { "field": "..." }, "timestamp": "..." }
```

## Architecture

```
React (5103) / Postman  ->  Controller  ->  Service  ->  Repository  ->  MySQL
```

```
backend/src/main/java/com/hackathon/hostel
├── config        SecurityConfig (JWT filter chain, CORS), DataInitializer (demo data)
├── security      JwtService, JwtAuthenticationFilter, JSON 401/403 handlers
├── controller    AuthController, RoomController (student), AdminController
├── service       RoomService (rooms), HostelService (requests, allocation, locking, dashboard), AuthService, LoginAttemptService
├── repo          UserRepository (row lock), RoomRepository (row lock), RoomRequestRepository, RoomAllocationRepository
├── entity        User, Room, RoomRequest, RoomAllocation, Role, RoomType, RequestStatus
├── dto           requests and responses, ErrorResponse
└── exception     GlobalExceptionHandler and the business exceptions
frontend/src      api (typed client) · auth (session, route guard) · components · pages · config.ts (branding, nav, demo accounts)
```

Controllers handle HTTP only, services hold the business rules, repositories only query.
Data processing in the services uses Java Streams. Tables are created by Hibernate: `users`, `rooms` (unique number), `room_requests` (student, status, decision, room) and `room_allocations` (room, student, vacated-at).

## Tests

```bash
cd backend
./mvnw test        # 25 tests on in-memory H2 (no MySQL needed): room rules, waiting list, approval, vacating, dashboard, roles, parallel approvals
```

API walkthrough with the Postman CLI (backend must be running; the run is repeatable):

```bash
postman collection run docs/postman/Hostel.postman_collection.json
```

It runs 55 requests (84 assertions). It registers fresh students and creates uniquely numbered rooms each run, so it can be run many times. You can also import the file into the Postman app.

## Configuration

Environment variables override the defaults in `application.properties`:
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` (**set your own, 32+ characters, outside local use**), `CORS_ORIGINS`.

## Images

The login-page photos in `frontend/public/hero` come from [Pexels](https://www.pexels.com) (free licence)
and were downloaded with `../tools/fetch_images.py` using `images.json` (needs `PEXELS_API_KEY`).

## Known limitations

- A student cannot request a move once allocated; the warden vacates the room first.
- No gender-based blocks or fee tracking.
- Filtering and the waiting list are computed in memory (Java Streams), which is fine for one hostel.
- Login throttling is in memory, so it resets on restart.
- The JWT is kept in `sessionStorage`; a production system would use an httpOnly cookie.
