# Exercise 2 · Employee Details Using URL Parameters

**Objective:** dynamic routing with route parameters.

## Routes

| Path | Page |
|---|---|
| `/` | redirects to `/employees` |
| `/employees` | list of six employees; each one is a link |
| `/employees/:employeeId` | details of the employee whose ID is in the URL |
| `/employees/:employeeId/edit` | edit form for that employee (challenge) |
| `*` | 404 Page Not Found |

## Implementation

- **The list:** each employee is a `<Link to={`/employees/${id}`}>`, so clicking navigates without a page reload.
- **Reading the URL:** the details page reads the parameter with `useParams()`. Parameters are always text, so `findEmployee()` accepts only digits and then compares the number. That makes `999`, `abc`, `101abc` and `-1` all find nothing.
- **Invalid IDs** are handled gracefully: an "Employee not found" card shows the requested ID and a link back to the list. Unknown paths get the 404 page.
- **Challenge, Edit:** the details page has an Edit button linking to `/employees/:employeeId/edit`.
  - The edit form is a controlled form that starts from the employee's data.
  - Save is disabled while any field is empty.
  - Saving calls `onSave`, which updates the `employees` state in `App`, then `navigate()`s back to the details page, where the change is visible.
- **Shared state:** the employees are held in `App` state and passed to the pages as props, so the list, details and edit pages all see the same data.

## Run

From the `React` folder, run `npm install` once. Then, in this folder:

```bash
npm run dev     # http://localhost:5202
npm test
```

**Tests:**
- `/` redirects to the list, which has at least five employees
- clicking an employee opens the right details page
- the details come from the URL ID
- the invalid IDs `999`, `abc`, `101abc` and `-1` show "Employee not found"
- Edit → change the role → Save shows the new role on the details page
- an empty field disables Save
- editing an unknown ID shows "not found"
- unknown paths show the 404 page
