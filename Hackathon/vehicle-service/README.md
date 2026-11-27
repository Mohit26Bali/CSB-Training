# Vehicle Service Booking System

Customers register their cars and bikes, pick a service and a day when a workshop bay is free, and follow the work from booked to completed. The service center sees the schedule, moves jobs through their statuses, searches vehicles and sees a dashboard. Spring Boot REST API with JWT security, plus a React front end.

## Quick start

You need Java 17 (`JAVA_HOME` must point at it), Node 20+, and MySQL 8 on `localhost:3306`.
The database `vehicle_service` is created automatically (user `root`, password `mohit123` by default).

```bash
# terminal 1: backend  (http://localhost:8102)
cd backend
./mvnw spring-boot:run          # PowerShell: .\mvnw spring-boot:run

# terminal 2: front end  (http://localhost:5102)
cd frontend
npm install                     # first time only
npm run dev
```

Open <http://localhost:5102>. The sun/moon button switches between light and dark mode.
On first start the backend seeds demo users and sample data.

### Demo logins

| Username | Password | Role |
|---|---|---|
| `serviceadmin` | `admin123` | SERVICE_ADMIN |
| `garage` | `admin123` | SERVICE_ADMIN |
| `aarav.sharma` (a Swift and a Royal Enfield: one upcoming booking, two finished services, one cancelled) | `customer123` | CUSTOMER |
| `diya.patel`, `rohan.mehta`, … (30 customers, 40 vehicles) | `customer123` | CUSTOMER |

## Features

- **Customer:** register vehicles (number, kind, make, model, year), pick a service with its estimated cost for that kind of vehicle, choose a day from a strip that shows free bays, book, follow upcoming and previous bookings with a progress tracker, cancel while still booked, and see each vehicle's service history.
- **Service center:** a schedule with filters (status, day, text) and one-click Start, Complete (with the final bill and work notes) and Cancel (with a reason); vehicle search by number, make, model or owner with history; management of services and the number of bays; a dashboard with bays in use, arrivals, revenue, a 14-day load chart and the most booked services.
- Service history, estimated cost and vehicle search (the case's bonus features).

## Business rules

| Rule | Result |
|---|---|
| The vehicle number is unique (MH12AB1234; spaces, dashes and case are ignored) and must be in a valid format | `400` field error on `number` |
| The service date cannot be in the past and at most 90 days ahead | `400` field error on `serviceDate` |
| A vehicle cannot have overlapping bookings (a two-day service blocks both days) | `409` |
| Each day has a limited number of bays; a full day cannot be booked | `409` |
| Only the owner books or cancels for a vehicle; only the owner or the garage reads its history | `403` |
| Only the service admin changes status: BOOKED to IN_SERVICE (not before the service day) to COMPLETED | `403` / `400` / `409` |
| A customer can cancel only while BOOKED; the garage must give a reason when it cancels | `409` / `400` |
| Final cost cannot be negative; when left empty the estimate is billed | `400` |
| A vehicle with bookings cannot be removed; a switched-off service cannot be booked; bays cannot drop below what is already booked | `400` |

**Concurrency:** every booking, start, completion, cancellation and service edit first takes a row lock on the service center (under `READ_COMMITTED`, before the booking is loaded), so two customers cannot take the last bay, a double click makes one booking, and a customer cancelling while the garage starts the job ends in exactly one consistent state. The tests check all three with parallel threads.

The estimate is the service's price for a car times a factor for the vehicle kind (bike 0.6, car 1.0, SUV 1.3, van 1.5).
Vehicle photos are looked up by kind (`/vehicles/<kind>-1.jpg`); a vehicle without one falls back to a coloured tile.

## API

All endpoints except login and register need `Authorization: Bearer <token>`.

| Method | URL | Role | Purpose |
|---|---|---|---|
| POST | /api/auth/login | public | Log in, returns a token |
| POST | /api/auth/register | public | Create an account (always the lowest role) |
| GET | /api/auth/me | any | Current user |
| GET | /api/catalog/services?vehicleType= | any signed-in user | Active services with the estimate for that kind of vehicle |
| GET | /api/catalog/availability?from=&days= | any signed-in user | Booked and free bays per day |
| POST | /api/vehicles | CUSTOMER | Register `{number, type, make, model, year}` |
| GET | /api/vehicles | CUSTOMER | Own vehicles |
| DELETE | /api/vehicles/{id} | CUSTOMER | Remove a vehicle without bookings |
| GET | /api/vehicles/{id}/history | CUSTOMER | Finished services of an own vehicle |
| POST | /api/bookings | CUSTOMER | Book `{vehicleId, serviceTypeId, serviceDate, notes?}` |
| GET | /api/bookings/my?scope=upcoming|previous | CUSTOMER | Own bookings |
| DELETE | /api/bookings/{id}?reason= | CUSTOMER | Cancel while BOOKED |
| GET | /api/admin/bookings?status=&date=&search= | SERVICE_ADMIN | The schedule |
| PUT | /api/admin/bookings/{id}/start | SERVICE_ADMIN | BOOKED to IN_SERVICE |
| PUT | /api/admin/bookings/{id}/complete | SERVICE_ADMIN | IN_SERVICE to COMPLETED `{finalCost?, workNotes?}` |
| PUT | /api/admin/bookings/{id}/cancel | SERVICE_ADMIN | Cancel with a reason `{reason}` |
| GET | /api/admin/vehicles?search= | SERVICE_ADMIN | Search all vehicles |
| GET | /api/admin/vehicles/{id}/history | SERVICE_ADMIN | Finished services of any vehicle |
| GET/POST/PUT | /api/admin/services | SERVICE_ADMIN | Manage the service catalogue |
| GET/PUT | /api/admin/bays | SERVICE_ADMIN | Read or change the number of bays |
| GET | /api/admin/dashboard | SERVICE_ADMIN | Bays, arrivals, revenue, load and top services |

Errors share one shape, and validation errors list each bad field so the UI can mark the right input:

```json
{ "status": 400, "error": "Bad Request", "message": "...", "fieldErrors": { "field": "..." }, "timestamp": "..." }
```

## Architecture

```
React (5102) / Postman  ->  Controller  ->  Service  ->  Repository  ->  MySQL
```

```
backend/src/main/java/com/hackathon/vehicle
├── config        SecurityConfig (JWT filter chain, CORS), DataInitializer (demo data)
├── security      JwtService, JwtAuthenticationFilter, JSON 401/403 handlers
├── controller    AuthController, CustomerController, AdminController
├── service       GarageService (vehicles, catalogue, availability, bookings, status changes, dashboard), AuthService, LoginAttemptService
├── repo          UserRepository, VehicleRepository, ServiceTypeRepository, ServiceBookingRepository, CenterRepository (row lock)
├── entity        User, Vehicle, ServiceType, ServiceBooking, Center, Role, VehicleType, BookingStatus
├── dto           requests and responses, ErrorResponse
└── exception     GlobalExceptionHandler and the business exceptions
frontend/src      api (typed client) · auth (session, route guard) · components · pages · config.ts (branding, nav, demo accounts)
```

Controllers handle HTTP only, services hold the business rules, repositories only query.
Data processing in the services uses Java Streams. Tables are created by Hibernate: `users`, `vehicles` (owner FK, unique number), `service_types`, `service_bookings` (vehicle, customer and service FKs, start and end date, status, estimated and final cost, who did what and when) and `service_center` (one row: the number of bays).

## Tests

```bash
cd backend
./mvnw test        # 35 tests on in-memory H2 (no MySQL needed): vehicle rules, catalogue pricing, date rules, overlaps, full days, status flow, cancellations, history, search, service and bay management, dashboard, roles, parallel bookings
```

API walkthrough with the Postman CLI (backend must be running; the run is repeatable):

```bash
postman collection run docs/postman/Vehicle.postman_collection.json
```

It runs 71 requests (112 assertions). It registers a fresh customer with random vehicle numbers and books days about two months ahead, so it can be run many times. You can also import the file into the Postman app.

## Configuration

Environment variables override the defaults in `application.properties`:
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` (**set your own, 32+ characters, outside local use**), `CORS_ORIGINS`.

## Images

The login-page photos in `frontend/public/hero` come from [Pexels](https://www.pexels.com) (free licence)
and were downloaded with `../tools/fetch_images.py` using `images.json` (needs `PEXELS_API_KEY`).

## Known limitations

- No time slots inside a day: a booking takes whole days from a shared pool of bays.
- No payments or invoices; the final bill is a number entered by the garage.
- The schedule and vehicle search are filtered in memory (Java Streams), which suits one workshop but would need paging for a chain.
- Login throttling is in memory, so it resets on restart.
- The JWT is kept in `sessionStorage`; a production system would use an httpOnly cookie.
