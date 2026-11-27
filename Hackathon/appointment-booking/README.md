# Appointment Booking System

Providers (doctors, a dentist, a physiotherapist, a counsellor, a nutritionist) publish slots; customers browse providers, pick an open time on a calendar and book; the provider confirms or cancels with a reason. Spring Boot REST API with JWT security, plus a React front end.

## Quick start

You need Java 17 (`JAVA_HOME` must point at it), Node 20+, and MySQL 8 on `localhost:3306`.
The database `appointment_booking` is created automatically (user `root`, password `mohit123` by default).

```bash
# terminal 1: backend  (http://localhost:8120)
cd backend
./mvnw spring-boot:run          # PowerShell: .\mvnw spring-boot:run

# terminal 2: front end  (http://localhost:5120)
cd frontend
npm install                     # first time only
npm run dev
```

Open <http://localhost:5120>. The sun/moon button switches between light and dark mode.
On first start the backend seeds demo users and sample data.

### Demo logins

| Username | Password | Role |
|---|---|---|
| `dr.mehta` (General physician; also `dr.rao`, `kavita.joshi`, `sanjay.kulkarni`, `ritu.bansal`) | `provider123` | SERVICE_PROVIDER |
| `aarav.sharma` (an upcoming confirmed appointment, a pending one, a past one and a cancelled one) | `customer123` | CUSTOMER |
| `diya.patel`, `rohan.mehta`, … (30 customers) | `customer123` | CUSTOMER |

## Features

- **Provider:** creates slots one at a time or a whole stretch at once (for example 09:00 to 12:00 in 30-minute pieces), sees a week calendar coloured by open, pending and confirmed, confirms or cancels appointments (a reason is mandatory), withdraws open slots, edits the public profile and sees a dashboard (today, to confirm, open slots, how full the week is).
- **Customer:** searches providers by name, speciality or place, sees their open slots day by day, books with an optional note, follows upcoming and previous appointments and cancels up to two hours before.
- Calendar UI, appointment search and cancellation reason (the case's bonus features).

## Business rules

| Rule | Result |
|---|---|
| A slot cannot be double-booked: one live (pending or confirmed) appointment per slot | `409` |
| Past slots cannot be booked; slots can only be created for the future, up to 60 days ahead, 15 to 180 minutes long and ending the same day | `400` field errors |
| A provider's slots cannot overlap (a slot may start exactly when another ends) | `409` |
| A customer cannot hold two live appointments at the same time, with any provider | `409` |
| Only the provider of a slot confirms or cancels its appointment, and must give a reason to cancel | `403` / `400` |
| Only a pending appointment can be confirmed, only a live one cancelled; a past appointment can be neither | `409` / `400` |
| A customer can cancel only their own appointment and only up to 2 hours before it starts | `403` / `400` |
| A slot holding a live appointment cannot be withdrawn | `409` |
| Self-registration creates customers only; providers are created by the practice | role is ignored |

**Concurrency:** booking, confirming and cancelling take a row lock on the slot (under `READ_COMMITTED`, and before the appointment is loaded), booking also locks the customer, and slot creation locks the provider, so five customers racing for one slot make one appointment, one customer clicking two overlapping slots gets only one, and two requests creating the same window make one set of slots. The tests check all of this with parallel threads.

Provider pictures are looked up by speciality (`/providers/<speciality>.jpg`); without one the provider's initials are shown on a coloured disc.
Slots are shown in the server's local time; there is no time-zone handling.

## API

All endpoints except login and register need `Authorization: Bearer <token>`.

| Method | URL | Role | Purpose |
|---|---|---|---|
| POST | /api/auth/login | public | Log in, returns a token |
| POST | /api/auth/register | public | Create an account (always the lowest role) |
| GET | /api/auth/me | any | Current user |
| GET | /api/providers?search= | any signed-in user | Providers with free slots in the next 14 days and the first free time |
| GET | /api/providers/{id}/slots?from=&days= | any signed-in user | Upcoming slots, each AVAILABLE or taken (no customer details) |
| POST | /api/appointments | CUSTOMER | Book `{slotId, reason?}` |
| GET | /api/appointments/my?scope=upcoming|previous | CUSTOMER | Own appointments |
| DELETE | /api/appointments/{id}?reason= | CUSTOMER | Cancel up to 2 hours before |
| GET | /api/provider/slots?from=&days= | SERVICE_PROVIDER | Own calendar with customers |
| POST | /api/provider/slots | SERVICE_PROVIDER | One slot `{date, startTime, minutes}` |
| POST | /api/provider/slots/bulk | SERVICE_PROVIDER | Many slots `{date, fromTime, toTime, minutes}` (all or nothing) |
| DELETE | /api/provider/slots/{id} | SERVICE_PROVIDER | Withdraw an unbooked slot |
| GET | /api/provider/appointments?status=&date=&search= | SERVICE_PROVIDER | Own appointments, pending first |
| PUT | /api/provider/appointments/{id}/confirm | SERVICE_PROVIDER | Confirm |
| PUT | /api/provider/appointments/{id}/cancel | SERVICE_PROVIDER | Cancel with a reason `{reason}` |
| GET/PUT | /api/provider/profile | SERVICE_PROVIDER | Read or edit the public profile |
| GET | /api/provider/dashboard | SERVICE_PROVIDER | Today, to confirm, open slots, utilisation, 7 days, next appointments |

Errors share one shape, and validation errors list each bad field so the UI can mark the right input:

```json
{ "status": 400, "error": "Bad Request", "message": "...", "fieldErrors": { "field": "..." }, "timestamp": "..." }
```

## Architecture

```
React (5120) / Postman  ->  Controller  ->  Service  ->  Repository  ->  MySQL
```

```
backend/src/main/java/com/hackathon/appointment
├── config        SecurityConfig (JWT filter chain, CORS), DataInitializer (demo data)
├── security      JwtService, JwtAuthenticationFilter, JSON 401/403 handlers
├── controller    AuthController, CustomerController, ProviderController
├── service       AppointmentService (providers, slots, booking, decisions, profile, dashboard), AuthService, LoginAttemptService
├── repo          UserRepository (row lock), ProviderRepository (row lock), SlotRepository (row lock), AppointmentRepository
├── entity        User, Provider, Slot, Appointment, Role, AppointmentStatus
├── dto           requests and responses, ErrorResponse
└── exception     GlobalExceptionHandler and the business exceptions
frontend/src      api (typed client) · auth (session, route guard) · components · pages · config.ts (branding, nav, demo accounts)
```

Controllers handle HTTP only, services hold the business rules, repositories only query.
Data processing in the services uses Java Streams. Tables are created by Hibernate: `users`, `providers` (profile of a provider account), `slots` (provider FK, start, end, closed) and `appointments` (slot and customer FKs, status, reason, who confirmed or cancelled and why).

## Tests

```bash
cd backend
./mvnw test        # 34 tests on in-memory H2 (no MySQL needed): slot rules, bulk creation, browsing, booking, overlaps, notice period, provider decisions, search, profile, dashboard, roles, parallel bookings and slot creation
```

API walkthrough with the Postman CLI (backend must be running; the run is repeatable):

```bash
postman collection run docs/postman/Appointment.postman_collection.json
```

It runs 69 requests (107 assertions). It registers a fresh customer and creates slots three to eight weeks ahead in the early morning, then restores the profile it edits, so it can be run many times. You can also import the file into the Postman app.

## Configuration

Environment variables override the defaults in `application.properties`:
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` (**set your own, 32+ characters, outside local use**), `CORS_ORIGINS`.

## Images

The login-page photos in `frontend/public/hero` come from [Pexels](https://www.pexels.com) (free licence)
and were downloaded with `../tools/fetch_images.py` using `images.json` (needs `PEXELS_API_KEY`).

## Known limitations

- No reminders by email or SMS, no recurring slots and no waiting list.
- A slot is open to everyone; a provider cannot restrict it to certain customers.
- Login throttling is in memory, so it resets on restart.
- The JWT is kept in `sessionStorage`; a production system would use an httpOnly cookie.
