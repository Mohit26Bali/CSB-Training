# Exercise 4 · Protected Admin Route

**Objective:** route protection based on application state.

## Routes

| Path | Access | Page |
|---|---|---|
| `/` | public | Home |
| `/login` | public | Login (demo account `admin` / `admin123`) |
| `/admin` | **protected** | Admin Dashboard |
| `/admin/reports` | **protected** | Admin Reports |
| `*` | public | 404 Page Not Found |

## Implementation

- **State:** `App` holds a simple `loggedIn` boolean in `useState`. `login()` checks the demo credentials and sets it to true; `logout()` sets it to false and navigates to `/login`.
- **Protection:** the admin routes are children of a `RequireAuth` route.
  - When `loggedIn` is true, `RequireAuth` renders `<Outlet />`, i.e. the requested page.
  - When it's false, it renders `<Navigate to="/login" replace state={{ from: location.pathname }} />`. The redirect replaces the history entry, so Back doesn't loop.
- **After login:** `LoginPage` calls `navigate(from, { replace: true })`. `from` defaults to `/admin`. Opening `/login` while already logged in redirects as well.
- **Challenge, return to the requested route:** the login page shows "Please log in to open `/admin/reports`" (read from `location.state.from`), and after a successful login sends the user to that page instead of the dashboard.
- **Logout** is a button in the header, shown only when logged in.
- **Validation:** empty fields give "Enter your username and password"; wrong credentials give "Invalid username or password". Both keep the user on Login.

## Run

From the `React` folder, run `npm install` once. Then, in this folder:

```bash
npm run dev     # http://localhost:5204
npm test
```

**Tests:**
- Home is public
- `/admin` and `/admin/reports` redirect to Login, which shows the requested path
- login from `/login` lands on the dashboard
- login from a redirect returns to `/admin/reports`
- wrong and empty credentials are rejected
- after Logout the admin pages are protected again
