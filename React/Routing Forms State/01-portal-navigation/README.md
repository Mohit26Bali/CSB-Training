# Exercise 1 · Employee Portal Navigation

**Objective:** a multi-page employee portal using React Router.

## Routes

| Path | Page |
|---|---|
| `/` | Home, with links to Employees and Departments |
| `/employees` | Employees |
| `/departments` | Departments |
| `/about` | About |
| `*` (anything else) | 404 Page Not Found |

## Implementation

- **Routing:** `App.jsx` declares the routes with `<Routes>` / `<Route>`, and `main.jsx` wraps the app in `<BrowserRouter>`.
- **Navigation bar:** `NavBar` uses `NavLink`. Its `className` callback receives `isActive`, so the current page's link gets `navbar__link--active` (highlighted). `end` on Home stops it matching every path.
- **404:** the wildcard route `path="*"` catches unknown URLs and shows which path was requested (`useLocation`).
- **Challenge:** a reusable `Layout` component holds the navigation bar and an `<Outlet />`. All pages, including the 404, are child routes rendered into the Outlet, so the navigation bar is always shown.
- **Navigation without reloads:** only `Link` and `NavLink` are used, never `window.location`.

## Run

From the `React` folder, run `npm install` once. Then, in this folder:

```bash
npm run dev     # http://localhost:5201
npm test
```

**Tests:**
- each route shows its page with exactly one highlighted link
- an unknown path shows the 404 inside the layout, with no highlighted link
- the Home links lead to Employees and Departments
