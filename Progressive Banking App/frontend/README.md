# Crestline Bank - frontend

React 19 + TypeScript + Vite + Tailwind CSS 4, TanStack Query, React Router, Axios.

## Run

```bash
# terminal 1 - backend (http://localhost:8080), needs MySQL with the "bank" database
cd backend && ./mvnw spring-boot:run

# terminal 2 - frontend (http://localhost:5173)
cd frontend && npm install && npm run dev
```

The dev server proxies `/api` to the backend (vite.config.ts), so there is no CORS setup to worry about.
Demo logins (seeded when the backend starts with SEED_DEMO_DATA=true): `ravi` / `customer123`, `priya` / `customer123`, `admin` / `admin12345`.

Checks: `npm run build` (type-check + production build), `npm run lint`.

## How the code is organised

```
src/
  main.tsx, App.tsx     entry point (providers) and the route table
  api/                  client.ts (Axios + errors), endpoints.ts (one function per backend endpoint),
                        queries.ts (React Query hooks), types.ts (mirror of the backend DTOs)
  auth/                 AuthContext (session), RequireRole (route guards), session.ts (sessionStorage), homePath.ts
  components/           AppShell (sidebar layout), AuthShell (login/register layout), AccountCard,
                        TransactionList, Modal, Toast, ui.tsx (buttons, fields, badges, states), Icon, Brand
  pages/auth/           LoginPage, RegisterPage
  pages/customer/       Dashboard, Accounts, AccountDetail, Transfer, Transactions, MoneyModal, OpenAccountModal
  pages/admin/          Overview, AdminAccounts
  lib/                  format.ts (money/date formatting), validation.ts (mirrors backend rules)
  hooks.ts, theme.ts    debounce + idempotency key hook, light/dark theme
tools/fetch_images.py   downloads photos from Pexels (key via PEXELS_API_KEY env var, never stored in the repo)
public/images/          the curated photos used on the login pages and banners
```

Every file starts with a comment saying what it does and which other files it works with.

## Things worth knowing

- **Duplicate-safe money movement.** Deposit, withdraw and transfer send an `Idempotency-Key` (hooks.ts). The backend
  runs a request with a given key once and replays the answer for repeats, so a double click or a retry after a
  network error cannot move money twice. The key is renewed whenever the form is edited or after success.
- **Route guards are cosmetic.** `RequireRole` only improves navigation; the backend enforces all access rules.
- **Session.** The JWT lives in `sessionStorage` and expires after 30 minutes; the app signs out automatically.
- **Light / dark theme** follows the OS until the user picks one (saved in `localStorage`).
