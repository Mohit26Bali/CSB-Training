# Hotel Room Booking System

Guests search rooms by dates, type and party size, see the price for the stay and request a booking; the hotel confirms or cancels, sees a month calendar of every room and a dashboard of occupancy and revenue. Spring Boot REST API with JWT security, plus a React front end with room photos.

## Quick start

You need Java 17 (`JAVA_HOME` must point at it), Node 20+, and MySQL 8 on `localhost:3306`.
The database `hotel_booking` is created automatically (user `root`, password `mohit123` by default).

```bash
# terminal 1: backend  (http://localhost:8108)
cd backend
./mvnw spring-boot:run          # PowerShell: .\mvnw spring-boot:run

# terminal 2: front end  (http://localhost:5108)
cd frontend
npm install                     # first time only
npm run dev
```

Open <http://localhost:5108>. The sun/moon button switches between light and dark mode.
On first start the backend seeds demo users and sample data.

### Demo logins

| Username | Password | Role |
|---|---|---|
| `hoteladmin` | `admin123` | HOTEL_ADMIN |
| `reception` | `admin123` | HOTEL_ADMIN |
| `aarav.sharma` (an upcoming confirmed stay in room 302, a past stay, a pending request and a cancelled one) | `customer123` | CUSTOMER |
| `diya.patel`, `rohan.mehta`, … (30 guests) | `customer123` | CUSTOMER |

## Features

- **Guest:** search available rooms for check-in/check-out dates with type, guests and price filters, see nights and total price, request a booking, see own bookings (upcoming, in stay, past) and cancel one before the check-in day.
- **Hotel:** a booking queue (pending first) with confirm and cancel-with-reason, a month calendar with a bar for every stay in every room, room management (add, edit, change price, close a room) and a dashboard with tonight's occupancy, arrivals, departures, revenue and a split by room type.
- Room photos by type, price calculation and occupancy statistics (the case's bonus features).

## Business rules

| Rule | Result |
|---|---|
| Check-in cannot be in the past and check-out must be after check-in; a stay is at most 30 nights | `400` field errors on `checkIn` / `checkOut` |
| The number of guests must fit the room | `400` field error on `guests` |
| A room cannot be booked for dates that overlap a confirmed stay (check-out day is free for the next guest) | `409` |
| A guest cannot hold two overlapping bookings of their own | `409` |
| Confirming a booking automatically releases other pending requests for the same room and overlapping dates, with a reason | `autoCancelled` count in the response |
| A guest can cancel only their own booking, and only before the check-in day; the hotel must give a reason | `403` / `409` / `400` |
| A booking can be confirmed or cancelled only while in the right status | `409` |
| Room numbers are unique; a room with upcoming or current bookings cannot be closed | `400` |
| Closed rooms are hidden from searches and cannot be booked | `400` |

**Concurrency:** booking, confirming and cancelling take a row lock on the room (under `READ_COMMITTED`, and before loading the booking so no stale copy is used), so two guests cannot both get the same room for the same night, and the hotel confirming while a guest cancels ends in one consistent state. The tests check this with parallel threads.

Room photos are looked up by room type (`/rooms/<type>-1.jpg`); a room without one falls back to a coloured panel.
No payment is taken: a confirmed booking is the end of the flow.

## API

All endpoints except login and register need `Authorization: Bearer <token>`.

| Method | URL | Role | Purpose |
|---|---|---|---|
| POST | /api/auth/login | public | Log in, returns a token |
| POST | /api/auth/register | public | Create an account (always the lowest role) |
| GET | /api/auth/me | any | Current user |
| GET | /api/rooms/available?checkIn=&checkOut=&type=&guests=&maxPrice= | any signed-in user | Rooms free for the stay with nights and total price |
| GET | /api/rooms/types | any signed-in user | Room types |
| POST | /api/bookings | CUSTOMER | Request a booking `{roomId, checkIn, checkOut, guests, specialRequests?}` |
| GET | /api/bookings/my | CUSTOMER | Own bookings |
| DELETE | /api/bookings/{id}?reason= | CUSTOMER | Cancel own booking before check-in day |
| GET | /api/admin/rooms | HOTEL_ADMIN | All rooms |
| POST | /api/admin/rooms | HOTEL_ADMIN | Add a room |
| PUT | /api/admin/rooms/{id} | HOTEL_ADMIN | Edit a room, change its price or close it |
| GET | /api/admin/bookings?status=&roomId= | HOTEL_ADMIN | Booking queue, pending first |
| PUT | /api/admin/bookings/{id}/confirm | HOTEL_ADMIN | Confirm and release rival requests |
| PUT | /api/admin/bookings/{id}/cancel | HOTEL_ADMIN | Cancel with a reason `{reason}` |
| GET | /api/admin/calendar?month=YYYY-MM | HOTEL_ADMIN | Stays per room for a month |
| GET | /api/admin/dashboard | HOTEL_ADMIN | Occupancy, arrivals, departures, revenue, by type |

Errors share one shape, and validation errors list each bad field so the UI can mark the right input:

```json
{ "status": 400, "error": "Bad Request", "message": "...", "fieldErrors": { "field": "..." }, "timestamp": "..." }
```

## Architecture

```
React (5108) / Postman  ->  Controller  ->  Service  ->  Repository  ->  MySQL
```

```
backend/src/main/java/com/hackathon/hotel
├── config        SecurityConfig (JWT filter chain, CORS), DataInitializer (demo data)
├── security      JwtService, JwtAuthenticationFilter, JSON 401/403 handlers
├── controller    AuthController, GuestController, AdminController
├── service       HotelService (search, booking, confirmation, cancellation, rooms, calendar, dashboard), AuthService, LoginAttemptService
├── repo          UserRepository, RoomRepository (row lock), BookingRepository (overlap queries)
├── entity        User, Room, Booking, Role, RoomType, BookingStatus
├── dto           requests and responses, ErrorResponse
└── exception     GlobalExceptionHandler and the business exceptions
frontend/src      api (typed client) · auth (session, route guard) · components · pages · config.ts (branding, nav, demo accounts)
```

Controllers handle HTTP only, services hold the business rules, repositories only query.
Data processing in the services uses Java Streams. Tables are created by Hibernate: `users`, `rooms` (unique number, type, price, capacity, amenities, active) and `bookings` (room and guest FKs, stay dates, nights, total, status, who confirmed or cancelled and why).

## Tests

```bash
cd backend
./mvnw test        # 30 tests on in-memory H2 (no MySQL needed): search and date rules, booking, overlaps, back-to-back stays, confirmation releasing rivals, cancellation rules, room management, calendar, dashboard, roles, parallel bookings
```

API walkthrough with the Postman CLI (backend must be running; the run is repeatable):

```bash
postman collection run docs/postman/Hotel.postman_collection.json
```

It runs 51 requests (85 assertions). It registers a fresh guest, creates a uniquely numbered suite and books dates about five months ahead, so it can be run many times. You can also import the file into the Postman app.

## Configuration

Environment variables override the defaults in `application.properties`:
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` (**set your own, 32+ characters, outside local use**), `CORS_ORIGINS`.

## Images

The login-page photos in `frontend/public/hero` come from [Pexels](https://www.pexels.com) (free licence)
and were downloaded with `../tools/fetch_images.py` using `images.json` (needs `PEXELS_API_KEY`).

## Known limitations

- No payments, taxes or rate plans; the price is nights times the room's nightly price.
- Search and calendar are built in memory (Java Streams), which suits one hotel but would need paging for a chain.
- Login throttling is in memory, so it resets on restart.
- The JWT is kept in `sessionStorage`; a production system would use an httpOnly cookie.
