# Registration Form

A registration page for **Northlight Dev Summit 2027**, a fictional two-day tech conference. The form uses HTML5 input types and validation attributes, with clear error messages, an error summary and a confirmation screen.

> **Demo:** the event is not real and nothing you enter is sent or saved. The page shows this note at the top.

## How to run

Open `index.html` in any modern browser (Chrome, Edge, Firefox). No server or install is needed. An internet connection loads the web font; without it the page uses system fonts.

## Files

| File | Purpose |
| --- | --- |
| index.html | Page structure and the form (4 sections, 21 fields) |
| css/styles.css | All styling: page grid, form grid, validation states, responsive rules |
| js/main.js | Error messages, error summary, password checks, progress, order summary, confirmation |
| images/summit-stage.webp | Event photo, 1200 x 902 WebP (74 KB) |
| images/favicon.svg | Browser tab icon |
| images/CREDITS.md | Photo credit |
| screenshots/ | Your captured screenshots |

## The form

| Section | Fields |
| --- | --- |
| 1. About you | First name, last name, email, mobile number, date of birth, country |
| 2. Your account | Username, password (with show/hide and a strength meter), confirm password |
| 3. Your ticket | Ticket type (3 cards), arrival date, years of experience, job role, workshops (up to 2) |
| 4. Final details | Portfolio link, T-shirt size, dietary needs, access needs, how you heard about us, newsletter, terms |

All fields are required except the ones marked "(optional)": workshops, portfolio link, access needs, how you heard about us and newsletter.

## HTML5 input types and validation attributes

This is the core of the exercise. Every rule below is checked by the browser itself.

| Field | Input type | Validation attributes | What is rejected |
| --- | --- | --- | --- |
| First / last name | `text` | `required`, `maxlength="40"` | Empty |
| Email | `email` | `required`, `pattern` (needs a dot after the @) | `amara`, `amara@mail` |
| Mobile number | `tel` | `required`, `pattern="[0-9 \+\(\)\-]{7,20}"` | Letters, fewer than 7 characters |
| Date of birth | `date` | `required`, `min="1920-01-01"`, `max="2011-04-15"` | Anyone under 16 on the first day of the event |
| Country | `select` | `required` (first option has `value=""`) | No country chosen |
| Username | `text` | `required`, `minlength="3"`, `maxlength="20"`, `pattern="[a-z0-9_]{3,20}"` | Capitals, spaces, symbols, too short |
| Password | `password` | `required`, `minlength="8"`, `maxlength="64"`, `pattern` (needs a letter and a number) | `abcdefgh`, `1234567` |
| Confirm password | `password` | `required`, plus a custom rule: must match | A different password |
| Ticket type | `radio` | `required` on the group | No ticket chosen |
| Arrival date | `date` | `required`, `min="2027-04-14"`, `max="2027-04-16"` | Dates outside the event |
| Years of experience | `number` | `required`, `min="0"`, `max="50"`, `step="1"` | `-1`, `51`, `2.5` |
| Job role | `text` with `<datalist>` | `required`, `maxlength="60"` | Empty |
| Workshops | `checkbox` group | Custom rule: at most 2 | 3 or more ticked |
| Portfolio link | `url` | `pattern="https?://.+"` (optional) | `github.com/me`, `ftp://...` |
| T-shirt, dietary needs | `select` | `required` | Nothing chosen |
| Access needs | `textarea` | `maxlength="300"` (with a live counter) | Typing stops at 300 |
| Terms | `checkbox` | `required` | Not ticked |

Other HTML5 features used:

- **`autocomplete` tokens** (`given-name`, `email`, `tel`, `bday`, `country`, `username`, `new-password`, `url`) so browsers and password managers can fill the form.
- **`inputmode="numeric"`** and the input types bring up the right phone keyboard (email, phone and number keys).
- **`<datalist>`** suggests job roles but still allows any text.
- **`<meter>`** shows password strength; **`<output>`** shows the chosen ticket and total.
- **`<fieldset>` and `<legend>`** group each section, the ticket radios and the workshop checkboxes.
- **`<details>` / `<summary>`** for the code of conduct, **`<data value>`** for prices, **`<time datetime>`** for event dates.

## How validation works

1. **The HTML attributes hold every rule.** JavaScript does not repeat them. It reads the browser's own `validity` result (for example `validity.rangeOverflow`) and shows a clear message for that rule.
2. **Two rules need JavaScript,** because HTML has no attribute for them:
   - "passwords match"
   - "no more than 2 workshops"

   Both use `setCustomValidity()`, so the browser treats them like any other rule.
3. **Errors appear when they help:**
   - A field is checked when you finish with it (the `change` event), not on every key press. You are not told off while still typing.
   - Once a field shows an error, it is re-checked as you type, so the error disappears as soon as it is fixed.
   - Pressing **Complete registration** checks everything.
4. **An error summary** at the top lists every problem, for example "There are 3 problems with your registration".
   - It receives focus, so screen readers read it out.
   - Each item is a link that jumps to its field.
   - The list updates as problems are fixed.
5. **Each error is shown under its field.** The field gets a red border and `aria-invalid="true"`, and the message is linked with `aria-describedby`.
6. **Without JavaScript the form still validates.**
   - JavaScript adds `novalidate` only when it loads.
   - Without it, the browser's built-in messages appear.
   - CSS `:user-invalid` still turns invalid fields red.
7. **Fields that pass get a green border and tick,** using CSS `:user-valid`. This only appears after the user has interacted with the field.

## Design decisions

### Layout (CSS Grid, container queries and subgrid)

- **Page:** a grid with one column on phones and tablets. From 960px it has two: `minmax(280px, 360px)` for the event panel and `minmax(0, 1fr)` for the form. The event panel is `sticky` on desktop, so the price and progress stay in view.
- **Form fields use a container query, not a media query.**
  - The form is a container (`container-type: inline-size`).
  - Fields go two per row when *the form itself* is at least 540px wide.
  - The form decides its own layout, so it would still work if placed in a narrower column.
- **Subgrid keeps side-by-side inputs level.**
  - Each field takes 5 rows of the field grid: label, hint, input, extra (strength meter or counter), error.
  - These rows use `grid-template-rows: subgrid`.
  - "Mobile number" has a two-line hint and "Date of birth" has a one-line hint, but their input boxes still line up.
- **Ticket cards** are a grid that changes from 1 column to 3 when the form is at least 640px wide. Each card uses `grid-template-areas`. On phones the radio, name and price sit in one line; in the 3-column layout the price moves to the top corner.
- **Selected ticket:** the card highlights using `:has(input:checked)`, with no JavaScript.

### Responsive design

| Width | Layout |
| --- | --- |
| Phone (under 640px) | One column. Event panel is a short banner (photo, dates, venue). Ticket cards stacked. Full-width submit button. |
| Tablet (640px and up) | Fields two per row, ticket cards three per row, more padding |
| Desktop (960px and up) | Sticky event panel on the left with order total and progress, form on the right |

### Visual design

- **Palette:** indigo brand colour (`#4338ca`) with a mint accent (`#5eead4`), the colours of the northern lights.
- **Font:** Plus Jakarta Sans.
- **Inputs:** at least 48px tall, with a 16px font size so phones do not zoom in.
- **Validation colours:** red for errors and green for fields that pass. Errors also have text and a red bar beside them, so colour is never the only signal.

### Accessibility

- Every input has a visible `<label>`. Placeholders are not used as labels.
- Hints and errors are linked to their field with `aria-describedby`.
- Each error starts with hidden text "Error:" for screen readers.
- Optional fields are marked with the word "(optional)" instead of a red asterisk on every required field. This is clearer, and the page explains it at the top.
- The error summary and the confirmation heading receive focus, so screen-reader users hear the result.
- The progress list has hidden text "(complete)" / "(not complete)", not just a tick.
- **Show password:** the button text changes between "Show" and "Hide", and has hidden text "password" so its purpose is clear.
- Focus outlines are visible everywhere. Checkbox and radio rows are at least 44px tall.
- `prefers-reduced-motion` turns off transitions.
- **No sticky header:** a fixed header could cover a field when the error summary jumps to it.

### User experience

- **Order summary:** choosing a ticket updates "Your ticket" and "Total" in the side panel.
- **Progress list:** each section gets a tick when all of its fields are valid.
- **Password strength meter** (Too short, Weak, Fair, Good, Strong) and a **Show/Hide** button.
- **Character counter** on the text area turns red with 30 characters left.
- **Confirmation screen:**
  - shows the name, a booking reference (for example `NDS27-6L533`), the ticket, arrival day, workshops, username and total
  - focus moves to it
  - the form, including the password, is cleared at once
  - **Register someone else** starts a new form
- **No reset button,** because it is too easy to clear a long form by accident.

### Privacy and safety

- Nothing is sent anywhere: the submit is handled on the page.
- The password is never shown on the confirmation screen and is cleared as soon as the form is accepted.
- In a real system, the server must check every rule again. Browser validation helps the user, but it can be bypassed.

## Testing checklist

Fill in the result column as you test. Unless the test says otherwise, test by typing in the field and then pressing Tab.

| # | Test | Input | Expected | Result |
| --- | --- | --- | --- | --- |
| 1 | Empty submit | Click "Complete registration" | Summary "There are 16 problems", focus moves to it | |
| 2 | Summary link | Click "Enter your email address" in the summary | Cursor goes to the email field | |
| 3 | Email | `amara@mail` | "Enter an email address in the format name@example.com" | |
| 4 | Phone | `07700 9001x` | Phone format error | |
| 5 | Phone | `+44 (0)7700 900123` | Accepted | |
| 6 | Under 16 | Date of birth 16/04/2011 | "You must be 16 or older on 15 April 2027" | |
| 7 | Exactly 16 | Date of birth 15/04/2011 | Accepted | |
| 8 | Username | `Amara` | Error: lowercase letters, numbers and underscores only | |
| 9 | Password | `abcdefgh` | Error: needs a letter and a number; strength "Weak" | |
| 10 | Password strength | `Summit2027!x` | Strength "Strong" | |
| 11 | Passwords match | Confirm with a different password | "The passwords do not match" | |
| 12 | Ticket | Choose Standard | Side panel shows Standard, £149 | |
| 13 | Arrival date | 20/04/2027 | "Arrival date must be between 14 and 16 April 2027" | |
| 14 | Experience | `2.5`, then `51`, then `-1` | Whole number, 50 or less, not less than 0 | |
| 15 | Workshops | Tick 3 | "Choose no more than 2 workshops" | |
| 16 | Portfolio | `github.com/me` | "Enter a full web address starting with https://" | |
| 17 | Character counter | Type in access needs | Counter goes down, turns red at 30 left, stops at 300 | |
| 18 | Progress | Complete section 1 | "About you" gets a tick | |
| 19 | Fix an error | Correct any field with an error | Error and summary item disappear | |
| 20 | Success | Fill everything correctly and submit | Confirmation with reference; password cleared | |
| 21 | Phone layout | DevTools device toolbar, 375px | One column, stacked tickets, no sideways scroll | |
| 22 | Keyboard only | Tab through the whole form, Space for radios and checkboxes | Every control reachable, focus always visible | |
| 23 | No JavaScript | Disable JavaScript, submit empty | Browser's own messages appear | |
| 24 | HTML validation | validator.w3.org, upload index.html | No errors | |
| 25 | Lighthouse | DevTools > Lighthouse > Mobile | Accessibility 95+ | |

## Screenshots to take

1. Desktop, empty form (1280px)
2. Tablet (768px)
3. Phone (375px)
4. Error summary after an empty submit
5. Field errors close up: phone, date of birth and password mismatch
6. Fields that pass, with green ticks (type into them yourself; `:user-valid` needs real typing)
7. Password strength meter at "Weak" and at "Strong"
8. Ticket card selected, with the side panel total
9. Confirmation screen
10. The browser's own message with JavaScript disabled
11. DevTools Grid overlay on the field grid, showing the subgrid rows
12. W3C validator and Lighthouse results

## Demo script (about 3 minutes)

1. Point out the demo note. Click **Complete registration** on the empty form: the summary appears and is focused. Click one link to jump to its field.
2. Type a wrong email, an under-16 date of birth and a phone number with a letter, and read the clear messages.
3. Show the HTML for one field, for example `type="date" min max required`, to show the rule lives in the HTML. JavaScript only writes the message.
4. Show the password strength meter, Show/Hide and the "passwords do not match" rule.
5. Choose a ticket: the card highlights and the total updates. Tick 3 workshops to show the limit.
6. Resize the window. Fields go from two per row to one, ticket cards stack, and the event panel becomes a banner.
7. Turn on the DevTools Grid overlay to show subgrid keeping inputs level.
8. Fill in the rest and submit to show the confirmation screen.

## Known limitations

| Limitation | Possible improvement |
| --- | --- |
| No server, so nothing is saved | Send the form to a back end that validates again and stores the booking |
| "Username taken" cannot be checked | Ask the server while the user types |
| Password match and workshop limit need JavaScript | The server would check these too |
| Country list is short | Use the full ISO country list |
| Fonts load from Google Fonts | Serve the font files from the project |
