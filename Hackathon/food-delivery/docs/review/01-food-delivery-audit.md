# Project 01: Food Delivery ("Spice Route") — audit and redesign proposal

Status: **Stages 1 to 4 complete. Waiting for approval (Stage 5). No source code has been changed.**
Reviewed: 2026-11-28. Folder: `Hackathon/food-delivery`. Source case: `cases/All_Cases.docx`, use case 1.

> "Project 01" is read as **use case #1, Food Delivery**. The Hackathon README lists projects by a different order (enrollment first), but
> every project carries its case number and #1 is `food-delivery`. If you meant a different project, say so before approving.

Legend. **Confirmed** = seen in the code, a test run or a measurement in a browser. **Assumption** = likely but not proven here.
**Question** = needs a business decision; no rule has been invented. Priorities: Critical, High, Medium, Low.

---

## 1. Executive summary

The project is a **well-built core with a template-grade surface**. The backend does what the use case asks and more: JWT security,
role checks at two layers, price calculation on the server, price snapshots on order lines, a one-step-at-a-time status machine,
row-locking so double clicks and simultaneous staff actions cannot corrupt an order, and 24 passing tests. The frontend compiles cleanly
and covers every workflow.

What holds it back, in order of importance:

1. **Deployment safety (High).** A default database password, a default JWT signing key and a default "seed demo accounts with known passwords"
   switch are all in `application.properties`. Running it anywhere shared, without setting environment variables, is unsafe.
2. **The kitchen board downloads the entire order history every 10 seconds (High).** Measured: 158 orders, 135 KB per poll, filtered in the browser. It will
   not survive a real restaurant's first month.
3. **The phone experience is broken for ordering (High).** On a 390 px screen the cart panel sits **8,345 px down an 8,793 px page**, below all 28 dishes,
   there is no cart indicator in the header, and the menu page scrolls sideways.
4. **Several business rules are missing or unspecified** (opening hours, delivery area, ETA, staff accounts, what happens to a READY order that is never
   delivered). Section 7 lists the questions; none of these are guessed.
5. **The interface is clean but generic.** It is the shared generator's look (top bar, photo banner, cards). A 160 px photo banner pushes the
   dishes below the fold on the menu, and a 250 px banner pushes the kitchen columns down on the one screen that staff use all day.

Nothing here requires a rewrite. Every improvement can be made on the existing API and schema; the few contract changes are called out.

---

## 2. Repository discovery

`Hackathon/` holds **20 independent projects**, one per use case in `cases/All_Cases.docx`, all with the same shape: a Spring Boot 4 backend
(JPA, MySQL, JWT), a React 19 + TypeScript + Vite + Tailwind 4 frontend, a Postman collection, a README and an `images.json`.

| Aspect | Finding |
| --- | --- |
| Relationship between projects | **No runtime coupling.** Each has its own ports (backend 81xx, frontend 51xx) and database; they only share generated code and `tools/` |
| Shared tooling | `tools/new_project.py` (starter), `apply_look.py` (palette, fonts, login layout, nav style, banners), `fetch_images.py`, Postman and README generators |
| Project 01 | `food-delivery`: backend 8101, frontend 5101, database `food_delivery` |
| Version control | **The whole `Hackathon/` folder is untracked in git** (`git status` shows `?? ./`; 0 tracked files under `food-delivery`) |

Implication for later projects: the generator makes 20 apps look alike. Any hand-made redesign of Project 01 would be overwritten if `apply_look.py` were
re-run for it (risk R1, section 10).

---

## 3. Project 01: purpose, users and architecture

**Purpose (from `docs/USECASE.md`).** Customers order from a restaurant menu; restaurant staff accept orders and move them through
PLACED, ACCEPTED, PREPARING, READY, DELIVERED. Bonus: category filter, total calculation, sales dashboard.

**Users and roles.** `CUSTOMER` (self-registers, or demo logins) and `RESTAURANT_ADMIN` (two seeded accounts; no way to create more from the app).
Stakeholders implied: the restaurant owner (menu, sales), kitchen staff (the board), customers. There is no delivery-partner role.

**Stack.** Spring Boot 4.1.1, Java 17, Spring Data JPA, Spring Security with a hand-written JWT filter (jjwt), Bean Validation, MySQL 8
(H2 in tests). React 19, react-router 7, TanStack Query 5, axios, Tailwind 4, Vite 8.

**Backend layout** (`com.hackathon.food`): `controller` (3), `service` (`FoodService` 320 lines, `AuthService`, `LoginAttemptService`),
`repo` (3, with pessimistic locks), `entity` (7), `dto` (17), `exception` (one handler plus 8 exception types), `security` (JWT filter and JSON 401/403).

**Database** (created by Hibernate `ddl-auto=update`, no migration tool):

```
users(id, username UNIQUE, password, fullname, role, enabled)
menu_items(id, name UNIQUE, description, category, price, vegetarian, active)
food_orders(id, customer_id FK, status, subtotal, tax, delivery_fee, total, address, phone, notes,
            created_at, accepted_at/by, preparing_at, ready_at, delivered_at, cancelled_at/by, cancel_reason)
order_items(id, order_id FK, menu_item_id FK, item_name, unit_price, quantity)   -- name and price copied at order time
```

**API** (all JSON; Bearer token except login/register):

| Area | Endpoints |
| --- | --- |
| Auth | `POST /api/auth/login`, `POST /api/auth/register`, `GET /api/auth/me` |
| Customer | `GET /api/menu`, `GET /api/menu/categories`, `POST /api/orders`, `GET /api/orders/my?scope=`, `GET /api/orders/{id}`, `DELETE /api/orders/{id}?reason=` |
| Restaurant | `GET /api/admin/orders`, `PUT /api/admin/orders/{id}/status`, `PUT /api/admin/orders/{id}/cancel`, `GET/POST/PUT /api/admin/menu`, `GET /api/admin/dashboard` |

**Frontend routes.** `/login`, `/register`; customer `/menu`, `/orders`; restaurant `/admin` (board), `/admin/menu`, `/admin/sales`.
State: TanStack Query for server data (polling 10 s board, 15 s orders), a localStorage cart per user, sessionStorage for the token.

**Security model.** Stateless JWT (60 min), BCrypt, role rules in the filter chain **and** `@PreAuthorize` on the service methods, user re-loaded from the
database on every request (so a disabled account takes effect at once), JSON error shape with per-field errors, CORS allow-list from an environment variable.

---

## 4. Feature inventory

**Customer.** Register and sign in. Menu of 28 dishes with photo, veg/non-veg mark, price; filters (category, vegetarian only, search, debounced).
Cart kept in the browser; quantity stepper (1 to 20); live totals (items, 5% tax, delivery). Checkout in a modal (address, mobile, note).
My orders (active / past), five-step progress tracker with times, cancel while PLACED with an optional reason, auto-refresh every 15 s.

**Restaurant.** Live board in four columns (New, Accepted, Preparing, Ready) with a one-click next step, reject with a mandatory reason (until READY), 10 s refresh,
search by order number, customer or phone, delivered and cancelled history tabs. Menu management: add, edit, switch off ("Sold out"). Sales page: orders and
revenue today, open orders, average order, 7-day revenue chart, best sellers, orders by status.

**System.** Light and dark theme; demo data seeder (30 customers, 28 dishes, about 150 orders); Postman collection (64 requests, 106 assertions).

---

## 5. Business rules inventory

All of these are **confirmed** in `FoodService` and the DTOs, and covered by the 24 tests unless marked.

| # | Rule | Enforced by |
| --- | --- | --- |
| R1 | Only active dishes can be ordered; the message names the sold-out dish | service (field error on `items`) |
| R2 | Quantity 1 to 20; an order has 1 to 30 distinct dishes; the same dish cannot repeat | DTO and service |
| R3 | Address 10 to 200 characters, mobile exactly 10 digits, notes up to 200 | DTO |
| R4 | Prices come from the menu; total = items + 5% tax (on items) + 40 delivery, free from 500 of items | service |
| R5 | Minimum order 100 (on items, before tax) | service |
| R6 | Order lines keep the dish name and price from the moment of ordering | entity |
| R7 | Status moves one step at a time, only the restaurant changes it; no skipping or going back | service (`409`) |
| R8 | Customer cancels only their own order and only while PLACED | service (`403`/`409`) |
| R9 | The restaurant rejects until READY and must give a reason | service |
| R10 | A same-dishes-same-address order within 30 s of the last one is refused as a double click | service (`409`) |
| R11 | Order numbers are `FD-` plus 1000 plus the id | service/DTO |
| R12 | Dish price 10 to 5000; names unique, case-insensitively | DTO and service |
| R13 | Self-registered users are always CUSTOMER; 5 failed logins lock a username for 15 minutes | service |
| R14 | Cancelled orders do not count as sales | dashboard |

---

## 6. Findings

Columns: **What** / **Why it matters (business impact)** / **Recommendation**. Status in brackets.

### 6.1 Security

| ID | Priority | Finding |
| --- | --- | --- |
| S1 | **High** [Confirmed] | **A default MySQL password is committed** in `application.properties` (line 6) and repeated in the READMEs, and the app connects as the MySQL `root` user. *Impact:* anyone who can read the repo (or a pushed copy) holds the root credential of the developer's database, and the same value is the default for every project. *Fix:* no default (require `DB_PASSWORD`), rotate the root password, create a least-privilege `food` database user, remove the value from every README. |
| S2 | **High** [Confirmed] | **A default JWT signing key** is used when `JWT_SECRET` is not set. *Impact:* a deployment that forgets the variable lets anyone who has read the repo forge an admin token. *Fix:* fail at startup unless a secret is supplied, except under an explicit `dev` profile. |
| S3 | **High** [Confirmed] | `app.seed.enabled=true` is the default, so every environment gets two admin accounts and 30 customers with published passwords. *Fix:* default `false`; enable only in a `dev` profile and say so in the README. |
| S4 | Medium [Confirmed] | Login lockout is keyed by **username only** and kept in memory. Five wrong guesses lock the `restaurant` admin out for 15 minutes (a cheap denial of service), and a restart clears it. *Fix:* key by username **and** IP, add exponential delay, rate-limit login. |
| S5 | Medium [Confirmed] | Open self-registration with no abuse control (no rate limit, e-mail or phone verification). *Impact:* spam accounts and junk orders. *Fix:* rate limit now; verification once a notification channel exists (Q9). |
| S6 | Medium [Confirmed] | The token is in `sessionStorage` with CSRF disabled (documented in the README). Fixed 60-minute life, no refresh, no server-side logout. *Fix:* httpOnly cookie plus CSRF token, or short access token plus refresh. |
| S7 | Medium [Confirmed] | `ddl-auto=update` with no migrations. *Impact:* schema drift and risky changes in production. *Fix:* Flyway with a baseline of the current schema. |
| S8 | Low [Confirmed] | The cancel reason travels in the **query string** of a `DELETE`, so it can end up in access logs. *Fix:* accept it in the body (keep the query form working for one release). |
| S9 | Low [Confirmed] | Fonts come from Google at runtime (privacy, and a kitchen on a flaky connection falls back to default fonts). *Fix:* self-host. |
| S10 | Low [Confirmed] | `RegisterRequest` documents "always EMPLOYEE" but assigns CUSTOMER (template leftover). *Fix:* correct the comment. |

### 6.2 Business logic and rules

| ID | Priority | Finding |
| --- | --- | --- |
| B1 | **High** [Confirmed absent; Question Q1] | **No opening hours and no "not accepting orders" switch.** Orders are accepted around the clock, including when the kitchen is closed or overwhelmed. *Fix:* a restaurant-level setting (open, closed, paused) checked in `place`, shown on the menu. |
| B2 | Medium [Confirmed] | **Cart prices go stale.** The cart stores the price at the moment of adding and is kept in localStorage indefinitely; the server recalculates silently, so the customer sees one total and is charged another, and the success toast is the first time they find out. *Fix:* reconcile the cart against `/api/menu` on load and before checkout; show "Price changed from 280 to 300" and remove or flag dishes that were removed. |
| B3 | Medium [Confirmed] | A sold-out dish stays in the cart and is only rejected at submit. *Fix:* same reconciliation; mark the line "sold out" and disable checkout until removed. |
| B4 | Medium [Confirmed] | The double-click guard is a heuristic: only the **last** order, only **30 s**, same dishes and address. A real retry after a timeout minutes later, or two legitimate identical orders within 30 s, are handled wrongly. *Fix:* a client-generated idempotency key on `POST /api/orders` (additive, optional header), keep the heuristic as a fallback. |
| B5 | Medium [Latent; Assumption] | **Time zones.** `LocalDateTime` is stored without a zone; "today" on the dashboard uses the server's date; "x min ago" on the board parses a zone-less string in the browser. It works while server and browser share a zone and is wrong otherwise. *Fix:* store UTC `Instant`, configure the restaurant's zone for "today". |
| B6 | Low [Confirmed] | Audit trail is partial: who ACCEPTED an order and who cancelled it are stored, but **not who** moved it to PREPARING, READY or DELIVERED. *Fix:* an `order_events` table (status, actor, time); also gives an ETA history. |
| B7 | Medium [Question Q4] | **Dashboard definitions are unstated.** "Revenue" is gross (items + tax + delivery); "Average order" is over all time, not the chosen period; "Open orders" counts all open orders while the other tiles say "today". *Fix:* define each figure, label it, add a date range. |
| B8 | Low [Confirmed] | Menu categories are a code enum (the owner cannot add "Combos"); no stock count (on/off only), no availability schedule (breakfast only), no variants or add-ons (portion, spice level). Optional, depends on the restaurant. |

### 6.3 Functional gaps (rules that are missing, not wrong)

| ID | Priority | Finding |
| --- | --- | --- |
| F1 | **High** [Confirmed absent; Q2] | **No staff management.** The registration page says "Restaurant staff are set up by the owner" but no screen or endpoint creates, disables or resets a staff account; `User.enabled` has no way to change. *Fix:* an owner-only Staff page (create, disable, reset password). Needs a decision on whether there is an OWNER role above RESTAURANT_ADMIN. |
| F2 | Medium [Confirmed absent; Q3] | **READY is a dead end.** After READY neither side can cancel, and nothing handles a customer who is unreachable or an order never delivered; there is no "delivery failed" outcome and no stale-order handling (a PLACED order can sit unaccepted for hours). |
| F3 | Medium [Confirmed absent; Q5] | No **ETA or promised time**, no delivery area or minimum-distance check (any 10-character address is accepted), no delivery partner role or assignment ("Delivered" is clicked by the kitchen). |
| F4 | Medium [Confirmed absent; Q6] | No **notifications** (customer is told nothing when status changes unless the page is open; the kitchen gets no sound or alert for a new order, only a 10 s poll). |
| F5 | Low [Confirmed absent] | Customer convenience: saved addresses, re-order, order rating, contact the restaurant. Optional extras beyond the case. |
| F6 | Low [Confirmed absent] | Kitchen convenience: kitchen order ticket / print view, quick-pick reject reasons, order age highlighting, per-item prep totals. |

### 6.4 Backend, database and performance

| ID | Priority | Finding |
| --- | --- | --- |
| P1 | **High** [Confirmed, measured] | The **live board requests every order ever placed** (`GET /api/admin/orders` with no status) and filters four columns in the browser. Measured on the seed data: **158 orders, 135 KB per request, every 10 seconds** per open board, with every order's items joined. *Fix:* server-side filter (`status in (open)`) plus a recent-window query for delivered and cancelled; keep the response shape. |
| P2 | Medium [Confirmed] | The sales dashboard loads all orders into memory and aggregates with streams (documented in the README). *Fix:* SQL aggregates by day and status; the response shape does not change. |
| P3 | Medium [Confirmed] | **No pagination** on My orders, delivered/cancelled history or menu admin. *Fix:* page and size parameters with defaults that keep today's behaviour. |
| P4 | Medium [Assumption] | Hibernate creates only primary keys, foreign keys and the unique constraints; queries by status, date and customer likely need indexes (`food_orders(status, created_at)`, `(customer_id, id)`). Verify with `SHOW INDEX` on MySQL; not checked here because the audit ran on in-memory H2. |
| P5 | Low [Confirmed] | No health endpoint, request correlation id or structured logs. *Fix:* Actuator health, an id in the log pattern. |
| P6 | Positive | Concurrency is handled well: row locks on the customer (double click) and on the order (status changes, cancellation), covered by parallel-thread tests. |

### 6.5 Frontend, UX and accessibility

| ID | Priority | Finding |
| --- | --- | --- |
| U1 | **High** [Confirmed, measured] | **Ordering on a phone does not work well.** At 390 px the menu page scrolls sideways, the cart panel starts **8,345 px down an 8,793 px page**, and the header has no cart indicator or count. A customer adds dishes and has no visible way to check out. *Fix:* a sticky cart bar and bottom sheet below 1024 px; find and remove the overflow. |
| U2 | **High** [Confirmed, screenshots] | **Banners cost the working area.** The menu opens with a 160 px photo banner ("Today's menu") followed by an h1 ("Menu") saying the same thing, so at 1440×900 only the first row of dishes shows. On the kitchen board a 160 px banner plus title pushes the columns to y≈435. *Fix:* no photo banners on operational pages; a compact title row. |
| U3 | Medium [Confirmed, measured] | Touch targets are small: nav links are 32 px high, the theme toggle 36 px, 39 controls on the menu page are under 40 px. *Impact:* mistakes on a kitchen tablet with wet or gloved hands. *Fix:* 44 px minimum for primary actions. |
| U4 | Medium [Confirmed; contrast to be measured] | Tiny low-contrast text: step timestamps are 10 px in `slate-400` (about 2.6:1 on white), board counts are tiny. On the login page the intro paragraph and the category chips sit on a busy photograph (see screenshot). *Fix:* contrast pass to AA with a calculator; a scrim or solid panel behind hero text. |
| U5 | Medium [Confirmed] | **Modal behaviour.** `aria-modal` dialogs do not trap focus (Tab leaves the dialog), the page behind can still scroll, and a click on the backdrop closes the **checkout and discards the typed address**. *Fix:* focus trap, scroll lock, no backdrop-dismiss on forms with input. |
| U6 | Medium [Confirmed] | `role="tab"` is used for filters and board tabs without tab panels or arrow-key support, which is the wrong pattern for screen readers. *Fix:* real tabs, or toggle buttons with `aria-pressed`. |
| U7 | Medium [Confirmed] | **Copy errors.** The customer login says "New employee? Create an account" (template leftover). Prices show `₹60.00`, with decimals nobody needs. "Vegetarian only" shows a checkbox **and** a veg symbol, which reads as two controls. |
| U8 | Medium [Confirmed] | **Dark mode is a set of overrides** (`.dark .bg-white`, an inverted slate scale) rather than semantic tokens. It works but is fragile and makes a redesign harder. *Fix:* semantic CSS variables. |
| U9 | Medium [Confirmed] | Checkout friction: the address and mobile number are typed every time; checkout is a modal, so it cannot hold a review step; no re-order. |
| U10 | Low [Confirmed] | Duplicated template pattern: every project uses the same top bar, banner and card layout, so this one has no identity beyond colour. See section 9. |
| U11 | Positive | Loading, empty and error states exist; errors point at the right field; toasts use `aria-live`; focus rings are visible; reduced-motion is respected on the login animations. |

### 6.6 Technical debt and engineering

| ID | Priority | Finding |
| --- | --- | --- |
| T1 | Medium [Confirmed] | **The project is not under version control** (untracked folder). No history, backup or review trail. *Fix:* commit locally (you push). |
| T2 | Medium [Confirmed] | **No frontend tests** (no unit or browser tests) and no CI. The backend has 24 tests. *Fix:* Vitest for logic (`pricing.ts`, cart), Playwright for the flows. |
| T3 | Low [Confirmed] | No Dockerfile or compose, no `.env.example`, no production profile or hosting notes. |
| T4 | Low [Confirmed] | `FoodService` (320 lines) mixes menu, ordering, kitchen and dashboard. Split when it grows, not now. |
| T5 | Low [Confirmed] | No OpenAPI document; the Postman collection is the only contract. Four `oxlint` fast-refresh warnings in `ui.tsx`. Money is a JS `number` in the cart (the server's `BigDecimal` is authoritative). |
| T6 | Positive | `tsc` is clean, the 24 backend tests pass, price logic lives only on the server, errors share one shape. |

---

## 7. Questions that need a business answer

I have not invented any of these rules.

| # | Question | Blocks |
| --- | --- | --- |
| Q1 | What are the opening hours, and should the owner be able to pause orders when the kitchen is overloaded? | B1 |
| Q2 | Who creates and removes kitchen accounts: is there an owner above RESTAURANT_ADMIN? | F1 |
| Q3 | What should happen to an order that is READY but not delivered, and to a PLACED order nobody accepts for N minutes (auto-cancel, alert, nothing)? | F2 |
| Q4 | Is "revenue" gross (with tax and delivery) or net? Which period should "average order" cover? | B7 |
| Q5 | Is there a delivery area, an ETA promise, or a delivery person? Or does "Delivered" stay a kitchen action? | F3 |
| Q6 | Should customers or the kitchen be notified (SMS, e-mail, sound)? Which channel is available? | F4, S5 |
| Q7 | Is 5% tax on items only (delivery untaxed) correct? Is the free-delivery threshold before or after tax? | B4 (rules R4, R5) |
| Q8 | Can a customer cancel after the kitchen has accepted if the kitchen agrees? | R8, F2 |
| Q9 | Is e-mail or phone verification expected at registration? | S5 |
| Q10 | Is the kitchen screen used on a tablet (touch-first, landscape) or a desktop? Decides density and target sizes. | U2, U3, design |
| Q11 | Is this one restaurant, or should the design allow several later? | schema |

---

## 8. Recommended improvements and roadmap

Principle: **preserve existing behaviour and API shapes**; every change below is additive unless marked.

### Phase 0: safe, quick (about half a day)

Fix S1 to S3 and S10, correct the U7 copy, commit the project to local git (T1). *Breaking:* running without `DB_PASSWORD` / `JWT_SECRET` will fail by design; the README and a `dev` profile cover local use.

### Phase 1: correctness and performance

P1 (server-side board query), P2 (SQL dashboard), P3 (paging, default unchanged), B2 and B3 (cart reconciliation), B4 (idempotency key), B5 (UTC), B6 (order events), S4 (login throttling), S8.
Tests added with each change; the existing 24 tests and the Postman collection stay green.

### Phase 2: UI/UX redesign (section 9)

U1 to U9: semantic tokens, mobile cart, checkout as a page with a review step, kitchen board redesign, accessibility pass, self-hosted fonts.

### Phase 3: features that wait for answers

B1 (open/paused), F1 (staff), F2, F3, F4, B7 (dashboard definitions), then F5 and F6 as wanted.

### Phase 4: quality

T2 (Vitest and Playwright), T3 (Docker, CI), S6 (cookie auth), S7 (Flyway), P4 (indexes after measuring on MySQL), P5.

| Area | Recommendations |
| --- | --- |
| Business logic | B1, B2, B3, B4, B7 |
| Functional | F1 to F6 |
| Backend and database | P1 to P5, B5, B6, S7 |
| Frontend and UX | U1 to U10 |
| Security | S1 to S10 |
| Performance | P1, P2, P3, P4 |

---

## 9. Proposed UI/UX redesign (using Taste Skill)

### 9.1 How the Taste Skill applies here (and where it does not)

The skill `design-taste-frontend` (v2.5.1) is **not installed** in this environment (the `/plugin` command is unavailable). I fetched its public repository into a scratch
folder, read `SKILL.md` and used it as design guidance only; I did not run its installer, which would change your Claude configuration.

Its description targets **marketing and company websites** ("one section per screen", a hero entrance, dealt "moments", award-site scenes). This project is **an ordering
app plus a kitchen tool**, so I am applying the skill by area and not forcing a landing page onto either:

| Area | Skill workflow that fits | What I will use | What I will skip |
| --- | --- | --- | --- |
| Login and register | Its **place** (restaurant) kind: photographs lead, flat colour, one hero | Photographic hero, one entrance, legible type, plain copy rules | Shader or scene effects (the skill reserves them for tech sites) |
| Customer menu, cart, orders | **Place** kind, applied as an app: real dish photography, one button style, one radius scale, hover feedback on every control, equal content edges, no scroll-reveal | Photography on dish cards, tokens, copy rules (headings name things, buttons are verb plus object), tap size, contrast | One-section-per-screen, hero entrance and "moments" on app screens |
| Kitchen board, menu admin, sales | Not a marketing page. Only the skill's **system rules**: one system, equal edges, 44 px targets, contrast, keyboard | Tokens and density; calm, fast, readable | Photo banners, hero, moments, decorative motion |

Its automatic checks (`gate.mjs`) are built for static pages and run from a `Stop` hook only when the skill is installed. At implementation time I would run its
relevant scripts (`copy-lint`, the layout checker at 1440, 1024, 820, 768 and 390) by hand against the built app, **if you approve running them**.

### 9.2 Visual direction: "a good restaurant's order desk"

Keep the identity (Spice Route, warm, real food photography) and remove the template feel.

* **Type.** One display serif for brand moments only (login headline, empty states) and one neutral humanist sans for the interface, both **self-hosted**.
  Tabular numerals for prices, order numbers and timers so columns line up.
* **Colour.** Semantic tokens (ground, surface, ink, muted, line, accent, and four status colours) in `@theme` variables, replacing the dark-mode overrides.
  A warm off-white ground and near-black warm ink; one deep chilli-red accent for primary actions; saffron for "needs attention"; green only for success and
  the veg mark. No violet. Status is never colour alone (label plus icon). Dark mode is a first-class kitchen theme and every pair is checked for AA contrast.
* **Space and shape.** 4/8 px scale, one radius scale (12 px cards, pills for filters), borders over shadows, one card style, one button pair (primary, quiet).
* **Photography.** Dish photos stay (they are the product). Banners go; no captions under images; alt text on meaningful images.
* **Motion.** Buttons step on hover and press; cards lift 2 px; the cart count ticks. Nothing moves on scroll; the login hero enters once. Reduced motion respected.

### 9.3 Navigation and journeys

**Customer** (mobile first):
`Menu` (sticky category bar, search, veg filter) → persistent **cart button with count** in the header → cart as a **bottom sheet** on phones and a right rail on
desktop → **checkout as its own page** (address, mobile, note, then an order review with the total) → **order tracking** (progress, times, cancel while allowed)
→ `My orders`. Saved address and re-order are optional (F5).

**Kitchen** (tablet landscape first, desktop second):
a **side rail** (Orders with a live count, Menu, Sales) and the board as the full-bleed default screen: sticky column headers, order cards with an **age timer that changes
colour** (thresholds from the business, Q3), 44 px actions, quick-pick reject reasons, an optional sound or flash for a new order (F4), dark mode on one tap.
Menu admin becomes a compact table with an inline on/off switch. Sales keeps its tiles but defines each figure (B7) and gains a date range.

### 9.4 Layout sketches

```
Customer, phone (390)                     Kitchen, tablet landscape (1024+)
+--------------------------+              +------+--------------------------------------------+
| Spice Route      [Cart 3]|              | Rail | New (3)  Accepted (1) Preparing (3) Ready (1)|
| [Search          ] [Veg] |              | Ord  | +------+ +------+  +------+  +------+       |
| All Starters Mains ...   |  <- sticky   | Menu | |FD1158| |FD1155|  |FD1154|  |FD1152|       |
| +----------------------+ |              | Sales| |6 min | |37 min|  |52 min|  |1h32  |       |
| | photo   Butter Naan  | |              |      | |[Acc.]| |[Prep]|  |[Rdy ]|  |[Deliv]|      |
| |         Rs 60  [Add] | |              |      | +------+ +------+  +------+  +------+       |
| +----------------------+ |              +------+--------------------------------------------+
| ...                      |
| [ View cart . Rs 230   ] |  <- sticky bar
+--------------------------+
```

### 9.5 Responsive and accessibility targets

Verified at 390, 768, 1024 and 1440 (and 320 for overflow). No horizontal scroll at any width. Targets 44 px for primary actions. Contrast AA for all text. Real tabs or toggles
with correct ARIA, modals that trap focus and restore it, no destructive backdrop dismiss, keyboard shortcuts on the board, `prefers-reduced-motion` honoured.

### 9.6 Implementation approach

Keep Tailwind and the existing components; introduce tokens first, then restyle component by component (`ui.tsx`, `Layout`, `MenuPage`, `OrdersPage`, `BoardPage`,
`MenuAdminPage`, `SalesPage`, `AuthShell`). No API change is needed for the redesign itself. Because `apply_look.py` would overwrite `look.ts` and `config.ts`, Project 01
would be **detached from that generator** (a note added to the README) so a later run cannot undo the work.

---

## 10. Risks and dependencies

| # | Risk or dependency | Mitigation |
| --- | --- | --- |
| R1 | Re-running `tools/apply_look.py` for this project would overwrite a redesign | Detach Project 01 from the generator and document it |
| R2 | Phase 0 makes `DB_PASSWORD`, `JWT_SECRET` mandatory outside a `dev` profile | A `dev` profile with safe local defaults; README and `.env.example` |
| R3 | Schema work on `ddl-auto=update` (new columns, indexes, `order_events`) can drift | Add Flyway first, or restrict to additive changes and test on a copy |
| R4 | Changing response shapes would break the Postman collection (64 requests, 106 assertions) | Additive fields only; run the collection after each phase |
| R5 | Several features depend on Q1 to Q11 | Phase 3 starts only after answers; Phases 0 to 2 do not depend on them |
| R6 | The whole folder is untracked, so there is no safety net | Commit before starting (T1) |
| R7 | A true MySQL run was not part of this audit (H2 in memory was used) | Verify indexes (P4) and the Postman run on MySQL at implementation |
| R8 | The Taste Skill's automatic gate is not installed | Run its scripts by hand with your approval, or install it yourself |

---

## 11. What I verified and what I did not

**Verified.** Read all backend source and the frontend pages and shared components. Ran the backend tests: **24 passed** (1 + 6 + 17). Ran `tsc -b`: no errors. Ran `oxlint`: 4 warnings.
Ran the app against **in-memory H2** (no MySQL, no real credentials) and drove it in Edge at 1440×900 and 390×844 as customer and as restaurant. Measured: horizontal overflow, cart position,
control sizes, and the size of the board request. No browser console errors. The servers were stopped afterwards.

**Not verified.** The MySQL path and indexes; the Postman collection run; real devices and screen readers; contrast ratios with a calculator (the findings above are from the
colour tokens and screenshots); production build size; the other 19 projects.

**Files created by this audit.** This document and four screenshots in `docs/review/evidence/`. The test and dev-server runs wrote only to ignored build folders
(`target/`, `node_modules/.vite`). No source file was changed.

Screenshots: `evidence/current-menu-desktop.png`, `evidence/current-kitchen-board-desktop.png`, `evidence/current-checkout-mobile.png`, `evidence/current-login-desktop.png`.

---

## 12. Decision needed (Stage 5)

Please reply with one of:

1. **Approve as written**, and I start with Phase 0, then Phase 1, then the Phase 2 redesign, testing and checking in a real browser at each step.
2. **Approve with changes** (for example: skip Phase 3, change the visual direction, or answer some of Q1 to Q11).
3. **Different project** if "Project 01" meant another one.
