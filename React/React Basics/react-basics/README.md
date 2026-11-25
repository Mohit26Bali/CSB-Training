# React Basics

Eight React exercises in one Vite application. A home page lists them, and **React Router** gives each exercise its own page, so all eight share a single `node_modules`.

## Run

```bash
npm install
npm run dev      # http://localhost:5173
npm test         # Vitest + React Testing Library
npm run build    # production build in dist/
```

## Structure

```text
src/
├── main.jsx, App.jsx, Home.jsx      router, layout, list of exercises
├── exercises/index.js               one entry per exercise (path, title, component)
└── exercises/NN-name/               the exercise's components, data, CSS and test
```

## Exercises

### 1. Employee Profile Card
- **Objective:** show employee profiles (name, ID, department, role, location, summary) with a reusable component.
- **Approach:**
  - `EmployeeCard` renders one employee from its `employee` prop.
  - `EmployeeList` maps the array from `employees.js` to cards.
  - `EmployeeProfileApp` only composes the two.
  - The CSS uses meaningful BEM-style classes (`employee-card__name`).
  - Challenges: four employees, rendered from an array with `map()`.
- **Tests:** every field of one card is shown, and there is one card per array entry.

### 2. Product Catalog Using Props
- **Objective:** an online-store catalog where every product card is driven entirely by props.
- **Approach:**
  - `ProductCard` receives `name`, `category`, `price`, `rating`, `available` and `image` as props. Nothing product-specific is written inside it.
  - It shows **Available** or **Out of Stock** from the `available` value, with a green or red badge.
  - `ProductList` maps the six products in `products.js` to cards.
  - Challenges: an image prop (local SVGs in `public/products/`) and a separate `ProductList` component.
- **Tests:** the card shows exactly the props it receives; the stock label follows `available`; at least five products are listed, with the right number of Out of Stock labels.

### 3. Attendance Counter
- **Objective:** count attendance with Increment, Decrement and Reset. The count starts at 0 and never goes negative.
- **Approach:**
  - `count` is held in `useState(0)` in `AttendanceCounterApp`.
  - Every button calls one handler, `changeBy(amount)`. It uses the functional update `setCount(current => ...)` and clamps the result between 0 and the maximum, so no button can push the count out of range.
  - `CounterDisplay` shows the count. `CounterControls` holds the buttons and receives the handlers as props.
  - Buttons that can't act are disabled: Decrement at 0, Increment at the maximum.
  - Challenges: **+5 / −5** buttons, and a **maximum of 50** with a warning (conditional rendering) when it is reached.
- **Tests:**
  - **normal:** up and down.
  - **boundary:** −5 from 3 gives 0; Decrement is disabled at 0; +5 from 48 stops at 50 and shows the warning.
  - **Reset** returns to 0.

### 4. Employee Search
- **Objective:** search ten employees (name, ID, department, role) with a case-insensitive search box, and show "No employees found" when nothing matches.
- **Approach:**
  - `search` and `department` are held in `useState`. `SearchBar` is a **controlled input**: its `value` comes from state, and `onChange` updates it.
  - The visible list is never stored. It is **derived** on every render by `filterEmployees(employees, search, department)`. That pure function lower-cases both sides, so the search ignores case, and it trims the search text.
  - `EmployeeTable` renders the matches.
  - Challenges: search by **name or employee ID**, a **department filter** (options built from the data with a `Set`), and the **number of matches** ("3 of 10 employees").
- **Tests:**
  - **normal:** all ten shown at first; "ASHA" finds Asha Verma; an ID search works; department + search combine.
  - **invalid:** "zzz" shows "No employees found", 0 of 10, and no table.
  - **boundary:** a blank search shows everyone.

### 5. To-Do List
- **Objective:** a team to-do list where tasks (ID, description, completed) can be added, completed and deleted.
- **Approach:**
  - `tasks` is an array in `useState`, and it is **never changed in place**:
    - **add:** `[...current, newTask]`
    - **complete and edit:** `map()` returns a new array, with a new object for the changed task
    - **delete:** `filter()`
  - Each update uses the functional form `setTasks(current => ...)`.
  - `TaskForm` (a controlled input) ignores blank text.
  - `TaskList` maps the tasks to `TaskItem`s. Each `TaskItem` has its own edit mode.
  - Challenges: **All / Active / Completed** filters (`TaskFilters`), **editing** a task, and a **pending-task count**. Both the count and the visible list are derived from `tasks`, never stored.
- **Tests:**
  - **normal:** starts empty; adding two tasks clears the input and gives "2 pending tasks"; editing and deleting work.
  - **invalid:** an empty or blank task is ignored.
  - Completing a task gives "1 pending task", and each filter shows the right tasks.

### 6. Student Registration Form
- **Objective:** a registration form (Name, Email, Age, Course, Mobile Number) with validation. The submitted data appears only after a successful submit.
- **Approach:**
  - **State:** one `form` object in `useState`. Every input is controlled and shares one handler: `setForm({ ...form, [e.target.name]: e.target.value })`.
  - **Validation:** a pure function, `validate(form)`, returns `{ field: message }`:
    - every field is required
    - age is a whole number from 18 to 60
    - a course must be selected
    - the email needs `text@text.domain`
    - the mobile number must be exactly 10 digits
  - The errors are derived on every render, never stored. A message shows only after the field has been left, or after a submit attempt (`touched` state).
  - `FormField` is a reusable label + input + message. `SubmittedDetails` shows the result.
  - Challenges: **Submit disabled while invalid**, a **Reset** button, and a **success message**.
- **Tests:**
  - **valid form:** passes, and submitting shows the data and the success message.
  - **required:** every empty field is reported.
  - **boundaries:** ages 18 and 60 accepted; 17, 61 and 20.5 rejected.
  - **invalid input:** a bad email and 9 digits or letters in the mobile number are rejected.
  - Submit stays disabled while invalid, and Reset clears the form.

### 7. Shopping Cart
- **Objective:** a cart for six products. Products can be added, quantities increased or decreased, and lines removed, with an item count, a total and an empty-cart message.
- **Approach:**
  - **Cart state:** the only state is the `cart` array (`{ id, name, price, quantity }`), updated without mutation:
    - **add:** an existing product gets `quantity + 1` via `map()`; otherwise a new line is added with spread
    - **decrease to 0:** `map` then `filter` drops the line
    - **remove:** `filter()`
  - **Derived values:** the **total is never stored**. `summarize(cart)` derives everything on each render with `reduce`: item count, subtotal, discount, tax and total.
  - **Components:** `ProductList`, `Cart` (with the empty-cart message), `CartItem` and `CheckoutSummary`.
  - Challenges: a **10% discount** when the subtotal is **above ₹5,000**, **18% GST** on the discounted amount, and a **checkout summary**.
- **Tests:**
  - **normal:** `summarize` derives all values.
  - **boundary:** exactly ₹5,000 gets no discount; ₹5,001 gets 10%.
  - **empty cart:** all zeros.
  - **through the UI:** adding the same product twice gives quantity 2; the count and total are correct; decreasing to 0 removes the line; removing the last item shows the empty-cart message.

### 8. Employee Dashboard
- **Objective:** a dashboard built from small components. It shows the total employees, the matching employees and the average salary for the selected department.
- **Approach:**
  - **Composition:** `EmployeeDashboardApp` holds the state and passes it down as props to `Header`, `DepartmentFilter`, `Statistics` (three reusable `StatisticsCard`s), `EmployeeSummary` and `EmployeeList`.
  - **State:** `department`, `salaryRange` and `sortBy`, changed through callback props.
  - **Derived data:** the matching list and the statistics are derived on every render, never stored.
    - `selectEmployees()` filters by department and salary range, then sorts a **new** array, so the original data is never reordered.
    - `averageSalary()` returns 0 for an empty list instead of dividing by zero.
  - Challenges: **sorting** by name or salary, a **salary-range filter**, and a reusable **StatisticsCard**.
- **Tests:**
  - **boundary:** the average of an empty list is 0, and when nothing matches the page shows "No employees match these filters." with a ₹0 average.
  - **no mutation:** sorting leaves the original array's order untouched.
  - **normal:** the starting statistics are right; the Engineering filter gives 3 matching and an average of ₹1,15,667; salary sorting and the salary range work.
