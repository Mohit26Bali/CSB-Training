# Exercise 3 · Department Dashboard with Nested Routes

**Objective:** nested routing for a dashboard-style application.

## Routes

| Path | Page |
|---|---|
| `/` | redirects to `/departments` |
| `/departments` | list of departments |
| `/departments/:departmentId` | **parent**: the dashboard (redirects to `overview`) |
| `/departments/:departmentId/overview` | child: counts and budget |
| `/departments/:departmentId/employees` | child: the department's employees |
| `/departments/:departmentId/projects` | child: projects ("No projects" when empty) |
| `/departments/:departmentId/reports` | child: reports |
| `*` | 404 Page Not Found |

## Implementation

- **Nested routes:** the four sections are **child `<Route>`s** inside the dashboard route in `App.jsx`. Their paths are relative (`overview`, `employees`, …), so they build on the parent's URL.
- **The parent stays put:** `DepartmentDashboard` renders the department heading and the section navigation, then an `<Outlet />`. Only the content inside the Outlet changes when a section is chosen. A test checks that the heading is the *same* element after switching sections.
- **Navigation inside the dashboard:** `NavLink`s with relative paths, with the active section underlined.
- **Challenge, dynamic department:** `:departmentId` is read with `useParams()`.
  - The department object is passed to the children with `<Outlet context={department} />`, and each section reads it with `useOutletContext()`, so they don't look it up again.
  - An unknown department shows "Department not found". An unknown section shows a message inside the dashboard.

## Run

From the `React` folder, run `npm install` once. Then, in this folder:

```bash
npm run dev     # http://localhost:5203
npm test
```

**Tests:**
- the dashboard defaults to Overview
- switching to Employees, Projects and Reports keeps the same dashboard heading and moves the active tab
- the department parameter selects the data
- the empty projects case
- an unknown department, and an unknown section
- list → dashboard navigation
