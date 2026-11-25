# Exercises 5–12 · Forms and State Management

One app with a menu page. Each exercise has its own route and its own folder under `src/exercises/`.

## Run

From the `React` folder, run `npm install` once. Then, in this folder:

```bash
npm run dev     # http://localhost:5205, then pick an exercise from the menu
npm test
```

## Exercises

### 5. Employee Registration Form · `/05-employee-registration`
- **Objective:** a controlled form for registering employees.
- **Approach:**
  - **Controlled fields:** name, email, department (select), designation and salary. Each one gets its `value` from one `form` state object, and a single `handleChange` updates it with `setForm({ ...form, [e.target.name]: e.target.value })`.
  - **Submit:** `handleSubmit` calls `event.preventDefault()`, so the page doesn't reload. Every field must be filled and the salary must be positive. Otherwise a message is shown and the typed values stay.
  - **After a successful submit:** the employee is added to an `employees` array in the parent (`EmployeeRegistrationApp`) and shown in a table below the form (`SubmittedEmployees`). The form then resets to empty.
  - **Challenge:** the name has `maxLength={50}` and a live "N characters remaining" counter, which turns red at 10 or fewer.
- **Tests:**
  - **controlled input:** what's typed shows in the field.
  - **successful submit:** the row appears in the table and the form clears.
  - **invalid:** missing fields and a salary of 0 are rejected, and the values are kept.
  - **boundary:** the counter goes 50 → 46 → 0, and typing stops at 50 characters.

### 6. Employee Form with Validation · `/06-validated-form`
- **Objective:** client-side validation with a message beside each field.
- **Approach:**
  - **Reusable checks:** `src/shared/validators.js` holds small checks (`required`, `email`, `mobile`, `minLength`, …), each returning a message or `''`. Exercises 7 and 8 reuse them too.
  - **Rules:** `validateEmployee(form)` combines the checks and returns only the failing fields:
    - all five fields are required
    - the email needs `text@text.domain`
    - the mobile number is 10 digits starting with 6–9
    - the password has at least 8 characters
    - the confirmation must match the password
  - **Errors are derived** on every render, never stored.
  - **Messages:** each field's message appears **next to it** (`FormField`), once the field has been left (`touched` state) or after a submit attempt. The message is linked to the input with `aria-describedby`.
  - **Submission:** only possible when no errors remain. **Challenge:** the Submit button stays disabled until then.
- **Tests:**
  - **normal:** a valid form passes.
  - **required:** every empty field is reported.
  - **invalid input:** a bad email; a 9-digit, 11-digit, wrong-start or letter mobile.
  - **boundary:** 7 vs 8 password characters.
  - **mismatch:** the passwords don't match.
  - **in the UI:** a message appears beside the field after leaving it; Submit stays disabled until everything passes, and a valid submit shows the success message and clears the form.

### 7. Dynamic Bank Account Form · `/07-bank-account-form`
- **Objective:** a form whose fields change with its own state, plus a growing list of nominees.
- **Approach:**
  - **State:** one `form` object holds customer name, account type, initial deposit, the type-specific fields, and a `nominees` array of `{ id, name, relationship }`.
  - **Conditional fields:** `AccountTypeFields` renders **Savings** fields (minimum-balance preference) or **Current** fields (business name), depending on `form.accountType`. `toSubmission()` keeps only the fields of the chosen type, so a value typed for Savings is not submitted for a Current account.
  - **Dynamic nominees:**
    - **add:** `[...nominees, newNominee()]`
    - **remove:** `filter()`
    - **edit:** `map()`

    Each nominee has a stable `id`, used as the React `key` and the field id, so values stay with the right row after a removal.
  - **Validation:** `validateBankForm()` reuses the shared checks:
    - all fields are required
    - minimum deposit: ₹1,000 for Savings, ₹5,000 for Current
    - each nominee needs a name and a relationship
    - at least one nominee

    Errors appear after the first submit attempt.
  - **Challenge:** a **live summary** panel shows `toSubmission(form)` as JSON, updating on every keystroke.
- **Tests:**
  - **boundary:** ₹999 / ₹1,000 for Savings; ₹4,999 for Current.
  - **only the chosen type's fields** are submitted.
  - **switching type** swaps the fields.
  - **nominees:** adding and removing renumbers the rows while keeping their values, and the summary follows.
  - **invalid submit** shows the errors; a **valid submit** registers the customer and clears the form.
  - **removing every nominee** is reported.

### 8. Multi-Step Loan Application · `/08-loan-application`
- **Objective:** a four-step form that keeps every answer while the user moves between steps.
- **Approach:**
  - **Steps:** 1 Personal, 2 Employment, 3 Loan, 4 Review and Submit.
  - **Shared state:** all steps share **one** `application` state object in `LoanApplicationApp`. A step only shows some of its fields, so going Back and Next never loses data.
  - **Steps as data:** `loanSteps.js` describes each step (title, fields, and a `validate` function for that step only). `StepFields` renders any step from that description.
  - **Next** runs the current step's validator and stays on the step, showing the messages, until it passes.
  - **Rules:**
    - age 21–65
    - mobile and email format
    - income > 0
    - years 0–50
    - amount ₹10,000 – ₹50,00,000
    - tenure 6–360 months
  - **Progress:** "Step N of 4: title" with a progress bar (`role="progressbar"`).
  - **Challenge:** each section on the Review page has an **Edit** button. It jumps to that step with an `editingFromReview` flag, so Next becomes **"Save and return to review"** and goes straight back to step 4, keeping all other data.
- **Tests:**
  - **boundaries:** age 20/21, amount ₹9,999/₹10,000, tenure 361.
  - **step validation:** Next is blocked while step 1 is invalid.
  - **data kept:** going Back keeps the entered data.
  - **full path:** steps 1 → 4, edit Employment from Review and return directly with the other data intact, then submit.

### 9. Shopping Cart with useState · `/09-shopping-cart`
- **Objective:** array-based state with values derived from it.
- **Approach:**
  - **State:** just two pieces, the `cart` array (`{ id, name, price, quantity }`) and the applied discount `code`.
  - **Updates** never mutate:
    - **add:** an existing product gets `quantity + 1` via `map()`, a new one is added with spread
    - **decrease:** `map` then `filter`, so 0 removes the line
    - **remove:** `filter()`
  - **Derived values:** `priceCart(cart, code)` computes the item count, **subtotal, discount, tax (18%) and grand total** on every render. None of these is stored.
  - **Empty cart:** shows "Your cart is empty."
  - **Challenge, discount codes** (`DiscountCodeForm`):
    - `SAVE10` is 10% off.
    - `FLAT500` is ₹500 off, only on a subtotal of ₹2,000 or more.
    - Codes are case-insensitive. An unknown code shows an error, and an applied code can be removed.
    - The discount is applied before tax.
- **Tests:**
  - **pricing:** derived totals; SAVE10.
  - **boundary:** FLAT500 at ₹1,999 vs ₹2,000.
  - **empty cart:** all zeros.
  - **through the UI:** six products; add, increase, decrease to 0 and remove back to the empty message; an unknown code is rejected and `save10` updates the grand total.

### 10. Lift State Up — Student Results · `/10-student-results`
- **Objective:** share state between two sibling components through their common parent.
- **Approach:**
  - **The parent** (`StudentResultsApp`) **owns** the `students` array. Neither sibling stores the list.
  - **`StudentForm`** collects the name and marks into its own small form state, validates them (name required; marks a whole number 0–100), then **sends the student up** through the `onAdd` callback prop and clears itself.
  - **`StudentList`** **receives the students through props** and only displays them.
  - **`ResultSummary`** derives the count, **average marks** and **number passed** (pass mark 40) from the same props on every render.
  - **Challenge:** **All / Passed / Failed** filters. The selected filter is also parent state, so the list gets the filtered array while the statistics always use all students.
- **Tests:**
  - **boundaries:** marks 0 and 100 accepted; −1, 101 and 55.5 rejected; empty fields reported.
  - **props:** `StudentList` renders exactly the props it receives.
  - **lifting state:** adding three students through the form updates the list and the statistics (average 53.7; 40 counts as passed).
  - **invalid:** marks of 120 add nothing.
  - **filters:** each one shows the right students.

### 11. Employee Preferences Using Context API · `/11-preferences-context`
- **Objective:** share state across several components without passing props down (prop drilling).
- **Approach:**
  - **The context:** `UserPreferencesContext.jsx` creates `UserPreferencesContext`. `UserPreferencesProvider` holds `{ theme, language, notifications }` in state and provides `{ preferences, updatePreference }`. A `usePreferences()` hook wraps `useContext` and throws a clear error if it's used outside the provider.
  - **Readers:** `Header`, `Dashboard` and `Profile` each call `usePreferences()` themselves; no props carry the preferences. They show the selected theme and language: greetings and headings are translated (English, Hindi, Tamil), and the theme switches the colour scheme of the whole area (light/dark CSS variables).
  - **Writer:** `Settings` is the only component that changes the preferences, through `updatePreference()`.
  - **Challenge, persistence:**
    - The provider's state is initialised lazily from `localStorage` (`useState(loadPreferences)`), so it is read once at start-up.
    - A `useEffect` saves every change.
    - `loadPreferences()` checks each value, so invalid or broken saved data falls back to the defaults.
    - Storage errors (e.g. private browsing) are caught.
- **Tests:**
  - **defaults:** the app starts with the default preferences.
  - **shared updates:** changing the theme, language and notifications in Settings updates Header, Dashboard and Profile, and the theme class.
  - **persistence:** values are saved, and restored after unmounting and rendering again.
  - **invalid storage:** broken or invalid saved data gives the defaults.
  - **outside the provider:** the hook fails with its clear message.

### 12. Bank Transaction Manager with useReducer · `/12-bank-reducer`
- **Objective:** manage more complex state transitions with `useReducer`.
- **Approach:**
  - **State:** `{ balances: { savings, current }, transactions: [], error }`, managed by `useReducer(bankReducer, initialBankState)`.
  - **The reducer is responsible for every transition.** Components only `dispatch` actions describing what happened:
    - `{ type: 'DEPOSIT', account, amount, timestamp }`
    - `WITHDRAW`
    - `TRANSFER` (challenge: `from`, `to`)
    - `RESET`
  - **Rejected actions:** a withdrawal or transfer larger than the balance, a non-positive amount, or a transfer to the same account. The reducer returns the **same balances and history** plus an `error` message, which the page shows.
  - **Successful transactions** are recorded with **type, amount and timestamp** (newest first), and clear the error.
  - **Pure reducer:** it never mutates the old state, and the timestamp comes from the action. So the same action on the same state always gives the same result, which makes it easy to test on its own.
  - **Challenge, TRANSFER:** there are two accounts (Savings, Current). A transfer debits the source and credits the destination in one step.
- **Tests:**
  - **DEPOSIT:** adds and records, without mutating the old state.
  - **boundary:** withdrawing exactly the balance works; ₹1 more is rejected with nothing changed.
  - **invalid amounts:** 0, negative and NaN are rejected.
  - **TRANSFER:** works between accounts; the same account and overdrafts are rejected.
  - **RESET:** returns the initial state, and a success clears the error.
  - **UI:** a deposit updates the balance and history, an overdraw shows the alert, Reset restores.
