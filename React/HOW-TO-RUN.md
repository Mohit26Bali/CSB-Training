# How to run the React exercises

```text
React/
├── package.json, node_modules/      one shared install for every app (npm workspace)
├── React Basics/
│   └── react-basics/                React Basics exercises 1–8 (one app)
├── Routing Forms State/
│   └── 01-portal-navigation/ ...    Routing, Forms & State exercises (one app each, 05–12 together)
└── REST API/
    └── employee-directory/          Employee Directory from https://dummyjson.com/users
```

## 1. Install (once)

You need Node.js 20 or newer (`node -v`). Open a terminal **in this `React` folder**:

```bash
npm install
```

That installs the libraries for every app. Run it again only after a new app is added.

## 2. Run all apps at once

From this `React` folder:

```bash
npm run dev:all
```

This starts all 10 apps together in one terminal (using `concurrently`). Each line of output is prefixed with the app's name (`[basics]`, `[01]` … `[15]`, `[api]`). Open any address from the table below. **Ctrl + C** stops them all.

## 2b. Or run one app

Go into the app's folder and start it:

```bash
cd "Routing Forms State/01-portal-navigation"
npm run dev
```

Open the address it prints (see the table). Stop it with **Ctrl + C**.

You can also start an app from this folder: `npm run dev -w 01-portal-navigation`. Each app has its own port, so several can run at once.

| App | Folder | Address |
|---|---|---|
| React Basics (exercises 1–8) | `React Basics/react-basics` | http://localhost:5173 |
| 1 · Employee Portal Navigation | `Routing Forms State/01-portal-navigation` | http://localhost:5201 |
| 2 · Employee Details (URL parameters) | `Routing Forms State/02-employee-details` | http://localhost:5202 |
| 3 · Department Dashboard (nested routes) | `Routing Forms State/03-department-dashboard` | http://localhost:5203 |
| 4 · Protected Admin Route (login `admin` / `admin123`) | `Routing Forms State/04-protected-admin` | http://localhost:5204 |
| 5–12 · Forms and State Management (menu of exercises) | `Routing Forms State/05-12-forms-and-state` | http://localhost:5205 |
| 13 · Employee Management SPA | `Routing Forms State/13-employee-spa` | http://localhost:5213 |
| 14 · Banking Customer Portal (login `priya` / `bank@123`) | `Routing Forms State/14-banking-portal` | http://localhost:5214 |
| 15 · Training Management Portal | `Routing Forms State/15-training-portal` | http://localhost:5215 |
| Employee Directory (REST API) | `REST API/employee-directory` | http://localhost:5401 |

## 3. Tests and builds

Inside an app folder:

```bash
npm test          # that app's tests
npm run build     # production build in dist/
npm run preview   # serve the build (port + 100, e.g. 5301)
```

From this folder, for all apps at once:

```bash
npm run test:all        # same as: npm test --workspaces
npm run build --workspaces
```

## Notes

- The logins in the table (`admin` / `admin123`, `priya` / `bank@123`) are **demo logins checked in the browser only**, to practise protected routes. They protect nothing real; a real application checks credentials on a server.
- Verified 25 Nov 2026: 165 tests pass across the 10 apps (`npm run test:all`), all 10 build, and the Employee Directory and the protected-admin app were checked in a real browser.

- In VS Code: **File → Open Folder → React**, then **Terminal → New Terminal**.
- If a port is busy, Vite says so. Stop the other server or close its terminal.
- Each app's `README.md` explains its routes, state and validation.
