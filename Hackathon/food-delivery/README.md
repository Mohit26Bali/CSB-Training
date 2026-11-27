# Food Delivery System

Customers browse a restaurant menu with photos, fill a cart, place an order and follow it from placed to delivered; the restaurant works an order board, manages the menu and sees sales. Spring Boot REST API with JWT security, plus a React front end.

## Quick start

You need Java 17 (`JAVA_HOME` must point at it), Node 20+, and MySQL 8 on `localhost:3306`.
The database `food_delivery` is created automatically. **No credentials are stored in this repository**: you give the app your own
MySQL password through an environment variable (ideally for a dedicated MySQL user rather than `root`).

```bash
# terminal 1: backend  (http://localhost:8101)
cd backend
# PowerShell:  $env:DB_PASSWORD = "your MySQL password"        bash:  export DB_PASSWORD="your MySQL password"
./run-dev.sh                    # Windows: run-dev.cmd     (= ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev)

# terminal 2: front end  (http://localhost:5101)
cd frontend
npm install                     # first time only
npm run dev
```

Open <http://localhost:5101>. The sun/moon button switches between light and dark mode.
The `dev` profile uses a public development JWT key and **seeds demo users and sample data** (published passwords below), so use it on your own
machine only. Without the `dev` profile the app needs `DB_USERNAME`, `DB_PASSWORD` and `JWT_SECRET`, and seeds nothing unless `SEED_DEMO_DATA=true`.

### Demo logins

| Username | Password | Role |
|---|---|---|
| `restaurant` | `admin123` | RESTAURANT_ADMIN |
| `kitchen` | `admin123` | RESTAURANT_ADMIN |
| `aarav.sharma` (two delivered orders, a cancelled one and one being prepared now) | `customer123` | CUSTOMER |
| `diya.patel`, `rohan.mehta`, … (30 customers) | `customer123` | CUSTOMER |

## Features

- **Customer:** a 28-dish menu with a photo, a veg / non-veg mark and a price, filters (category, vegetarian only, search), a cart that is kept in the browser, a checkout with address, mobile number and a note, a live total with tax and delivery, and My orders with a five-step progress tracker and cancel while still placed.
- **Restaurant:** a live order board (new, accepted, preparing, ready) with one-click next step and reject-with-reason, delivered and cancelled lists, search by order number, customer or phone, menu management (add, edit, switch a dish off when it sells out) and a sales page (today, 7 days, best sellers, orders by status).
- Category filtering, order total calculation and a simple sales dashboard (the case's bonus features).

## Business rules

| Rule | Result |
|---|---|
| Only active menu items can be ordered; the message names the dish that is sold out | `400` field error on `items` |
| Every quantity is between 1 and 20, the same dish cannot appear twice and an order needs at least one dish | `400` field errors |
| The address (10 to 200 characters) and a 10 digit mobile number are required; the minimum order is ₹100 | `400` |
| Prices always come from the menu, never from the browser; totals = items + 5% tax + ₹40 delivery (free from ₹500) | calculated by the server |
| Orders move one step at a time: PLACED, ACCEPTED, PREPARING, READY, DELIVERED; only the restaurant changes status | `403` / `409` |
| A customer can cancel only their own order and only while it is PLACED; the restaurant can reject until it is READY and must give a reason | `403` / `409` / `400` |
| A retried checkout is safe: send an `Idempotency-Key` header (the web app does) and a retry returns the order already made (`200`) instead of a second one. Without a key, the same dishes to the same address within 30 seconds of the last order are refused as a double click | `200` / `409` |
| The cart is checked against the live menu before an order is placed: a changed price or a sold-out dish stops the checkout and is shown in the cart, so nobody pays a price they were not shown | UI |

Every status change is also written to `order_events` (status, who, when). Order times are sent with the restaurant's UTC offset (`app.timezone`, default `Asia/Kolkata`).

**Concurrency:** placing an order locks the customer row (under `READ_COMMITTED`), so five simultaneous clicks on Place order make one order; every status change and cancellation locks the order row first, so two staff members accepting the same order accept it once, and a customer cancelling while the kitchen accepts ends in exactly one consistent state. The tests check this with parallel threads.

Dish photos are looked up by name (`/dishes/paneer-tikka.jpg`); without one a warm tile with an emoji is shown.
Order lines keep the dish name and price from the moment of ordering, so editing the menu later does not change past orders.
Payment is pay-on-delivery; there is no online payment.

## API

All endpoints except login and register need `Authorization: Bearer <token>`.

| Method | URL | Role | Purpose |
|---|---|---|---|
| POST | /api/auth/login | public | Log in, returns a token |
| POST | /api/auth/register | public | Create an account (always the lowest role) |
| GET | /api/auth/me | any | Current user |
| GET | /api/menu?category=&vegetarian=&search= | any signed-in user | Active dishes |
| GET | /api/menu/categories | any signed-in user | Categories in menu order |
| GET | /api/health | public | `{"status":"UP"}`. Every response carries an `X-Request-Id` that is also on the log lines |
| POST | /api/orders | CUSTOMER | Place `{items: [{menuItemId, quantity}], address, phone, notes?}`. Optional header `Idempotency-Key` (8 to 64 letters, digits, `-`, `_`): `201` when created, `200` with the same order on a retry |
| GET | /api/orders/my?scope=&page=&size= | CUSTOMER | Own orders, newest first. `scope` is `active`, `past` or `all`; `page` (from 0) and `size` (1 to 100) are optional, and without them every matching order is returned |
| GET | /api/orders/{id} | CUSTOMER | One own order |
| POST | /api/orders/{id}/cancel | CUSTOMER | Cancel while PLACED, `{reason?}` in the body |
| DELETE | /api/orders/{id}?reason= | CUSTOMER | The same, with the reason in the URL (kept for older clients) |
| GET | /api/admin/orders?status=&scope=&search=&page=&size= | RESTAURANT_ADMIN | The order board, open orders first. `scope=open` returns only orders that are not delivered or cancelled (the live board); `page`/`size` page the history. Filtering and paging happen in SQL |
| PUT | /api/admin/orders/{id}/status | RESTAURANT_ADMIN | Next step `{status}` |
| PUT | /api/admin/orders/{id}/cancel | RESTAURANT_ADMIN | Reject with a reason `{reason}` |
| GET/POST/PUT | /api/admin/menu | RESTAURANT_ADMIN | List all dishes, add one, edit one (including switching it off) |
| GET | /api/admin/dashboard | RESTAURANT_ADMIN | Orders and revenue, best sellers, status counts, last 7 days |

Errors share one shape, and validation errors list each bad field so the UI can mark the right input:

```json
{ "status": 400, "error": "Bad Request", "message": "...", "fieldErrors": { "field": "..." }, "timestamp": "..." }
```

## Architecture

```
React (5101) / Postman  ->  Controller  ->  Service  ->  Repository  ->  MySQL
```

```
backend/src/main/java/com/hackathon/food
├── config        SecurityConfig (JWT filter chain, CORS), DataInitializer (demo data)
├── security      JwtService, JwtAuthenticationFilter, JSON 401/403 handlers
├── controller    AuthController, CustomerController, AdminController
├── service       FoodService (menu, ordering, price rules, status flow, dashboard), AuthService, LoginAttemptService
├── repo          UserRepository (row lock), MenuItemRepository, FoodOrderRepository (row lock)
├── entity        User, MenuItem, FoodOrder, OrderItem, Role, MenuCategory, OrderStatus
├── dto           requests and responses, ErrorResponse
└── exception     GlobalExceptionHandler and the business exceptions
frontend/src      api (typed client) · auth (session, route guard) · components · pages · config.ts (branding, nav, demo accounts)
```

Controllers handle HTTP only, services hold the business rules, repositories only query.
Data processing in the services uses Java Streams. Tables are created by Hibernate: `users`, `menu_items` (unique name, category, price, vegetarian, active), `food_orders` (customer FK, status and the time of every step, totals, address, phone) and `order_items` (order and dish FKs, the name and price copied at order time, quantity).

## Tests

```bash
cd backend
./mvnw test        # 53 tests on in-memory H2 (no MySQL needed): menu filters and management, totals, price snapshots, validation, double-click guard and idempotency keys, order scopes and paging, cancellation rules, status flow, board, dashboard, roles, login throttling, order history, time zone, parallel orders and status changes

cd ../frontend
npm test           # 12 unit tests: cart reconciliation against the menu, pricing, checkout keys
```

API walkthrough with the Postman CLI (backend must be running; the run is repeatable):

```bash
postman collection run docs/postman/Food.postman_collection.json
```

It runs 64 requests (106 assertions). It registers a fresh customer and a uniquely named dish each run, so it can be run many times. You can also import the file into the Postman app.

## Configuration

Set these environment variables (see `application.properties`; the `dev` profile supplies local values for the first three):

| Variable | Needed | Meaning |
|---|---|---|
| `DB_USERNAME`, `DB_PASSWORD` | always (the dev profile defaults the user to `root`; you set the password) | MySQL account |
| `DB_URL` | optional | defaults to the local MySQL |
| `JWT_SECRET` | always outside the dev profile | 32+ characters; the app refuses to start without it |
| `CORS_ORIGINS` | optional | allowed browser origins, default `http://localhost:5101` |
| `APP_TIMEZONE` | optional | the restaurant's time zone, default `Asia/Kolkata` |
| `SEED_DEMO_DATA` | optional | `true` seeds the demo users and orders (the dev profile does) |

## Images

The login-page photos in `frontend/public/hero` come from [Pexels](https://www.pexels.com) (free licence)
and were downloaded with `../tools/fetch_images.py` using `images.json` (needs `PEXELS_API_KEY`).

## Known limitations

- No online payment, delivery tracking on a map, coupons or ratings.
- Login throttling is in memory, so it resets on restart (it limits per username and address, and per address overall).
- Behind a reverse proxy the throttle sees the proxy's address unless the proxy passes the client address on (`server.forward-headers-strategy`).
- The JWT is kept in `sessionStorage`; a production system would use an httpOnly cookie.
