# Employee Directory

A people directory for **Tidewell**, a fictional software company with 50 employees in 6 departments. It shows employee cards with profile photos, department information, search and filters, and is laid out with CSS Grid.

> **Demo data:** Tidewell is not a real company. All names, jobs and contact details are invented. The portraits are stock photos of models from Pexels, and the people in them are not the employees described. The page shows this note at the top.

## How to run

Open `index.html` in any modern browser (Chrome, Edge, Firefox). No server or install is needed. An internet connection loads the web font; without it the page uses system fonts.

## Files

| File | Purpose |
| --- | --- |
| index.html | Page structure, 6 department cards and 50 employee cards |
| css/styles.css | All styling: the page grid, card grid, subgrid, list view and responsive rules |
| js/main.js | Search, filters, sorting, card/list view and the profile dialog |
| images/people/ | 50 portraits, 320 x 320 WebP (369 KB in total, about 7 KB each) |
| images/favicon.svg | Browser tab icon |
| images/CREDITS.md | Photographer credit for every portrait |
| screenshots/ | Your captured screenshots |

## Departments

| Department | People | Head |
| --- | --- | --- |
| Engineering | 14 | Richard Hale |
| Design & UX | 7 | Claire Dubois |
| Marketing | 7 | Fiona Walsh |
| Sales | 8 | Vikram Rao |
| Human Resources | 6 | Patricia Johnson |
| Finance & Operations | 8 | Ahmed Rahman |
| **Total** | **50** | |

Offices: London, Toronto and Singapore, plus remote staff.

## Page structure

| Part | HTML elements |
| --- | --- |
| Demo note | `<p>` at the very top |
| Header | `<header>`, logo, `<nav aria-label="Primary">` |
| Page title | the only `<h1>` |
| Departments | `<section>` with a `<ul>` of `<article>` cards; facts in a `<dl>` (head, people, offices) |
| Filters | `<aside>` with a search `<form role="search">`, labelled inputs and a stats `<dl>` |
| Directory | `<section>` with a `<ul>` of 50 `<article>` cards |
| Employee card | `<img>`, name `<h3>`, job title, department and office, `<address>` with a `mailto:` link, skills `<ul>`, status, "View profile" `<button>` |
| Profile | native `<dialog>` with a `<dl>` of details and a `<time datetime>` start date |
| Footer | `<footer>` |

## Design decisions

### CSS Grid (the focus of this exercise)

The page uses Grid at four levels:

1. **Page layout with named areas.** `grid-template-areas` places the intro, departments, sidebar and directory.
   - On phones and tablets the areas stack in one column.
   - From 1100px the sidebar moves beside the directory, and only the area map changes:

   ```css
   grid-template-areas:
       "intro intro"
       "departments departments"
       "sidebar directory";
   ```

2. **Responsive card grids without breakpoints.**
   - The employee grid uses `repeat(auto-fill, minmax(min(100%, 250px), 1fr))`, which gives 1 to 4 columns depending on the space.
   - The department grid and the filter form use the same idea.

3. **Subgrid for aligned cards.**
   - Each employee card spans 5 rows of the main grid and uses `grid-template-rows: subgrid`.
   - The photo and name row, department row, contact row, skills row and status row line up across every card in a row of the grid, even when one person has a long job title or email.

4. **List view by changing only the grid.**
   - The "List" button adds one class. The cards switch to one row each, with five columns (`grid-template-columns`) for person, department, contact, skills and status.
   - The HTML does not change.

On phones, the department cards become a **swipeable row**, using `grid-auto-flow: column` with scroll snapping. Without it, the six cards filled five screens before the search box. The order of the HTML is unchanged, so keyboard and screen-reader order stay correct.

### Responsive design

| Width | Layout |
| --- | --- |
| Phone (under 640px) | Departments swipe sideways, filters stacked, 1 card per row |
| Tablet (640px and up) | Department cards 2 per row, filters in one row, 2 cards per row |
| 900px and up | The Cards/List toggle appears |
| Desktop (1100px and up) | Sticky sidebar beside the directory, 3 cards per row |

### Visual design

- **Palette:** teal brand colour (`#0f766e`) on a light grey background with white cards.
- **Department colours:** each department has its own colour, set with a CSS custom property (`--dept-color`). It is used for the department card's top border and the badge on each employee card. The badge background is made lighter with `color-mix()`.
- **Font:** DM Sans, a clean, readable sans-serif.
- **Portraits:** round photos with a soft teal ring, so 50 different photos look like one set.
- **Long emails** wrap neatly at the "@" (a `<wbr>` is placed before it).

### Accessibility

- Landmarks (header, nav, main, aside, footer) and a clear heading order: `h1`, `h2` per section, `h3` per card.
- A skip link goes straight to the directory.
- Every portrait has alt text with the person's name. The small head-of-department photos are decorative (`alt=""`), because the name is written beside them.
- **Status** is shown as a coloured dot **and** text, so colour is never the only signal.
- **"View profile" buttons** include the person's name for screen readers ("View profile of Chloe Adams").
- The result count ("Showing 4 of 50 people") is announced when filters change.
- The Cards/List buttons use `aria-pressed`.
- The profile `<dialog>` keeps focus inside, closes with Escape, and returns focus to the button that opened it.
- Inputs and buttons are at least 44px tall. Focus outlines are visible everywhere. `prefers-reduced-motion` turns off the card hover movement.

### User experience

- **Search** matches name, job title, skill or department. Several words narrow the results, for example "react toronto".
- **Filters** for department, office and "Available now only" can be combined.
- **"View the team"** on a department card filters the directory to that department.
- **Sort** by last name, department or newest starter.
- **"No one matches"** message with a button to clear the filters.
- **Profile dialog** with bio, manager, start date, status, contact and skills.

### Progressive enhancement

- Without JavaScript:
  - All 50 cards and the department information still show.
  - The Cards/List toggle is hidden, because it needs JavaScript.

### How the HTML was produced

The 50 employee cards and 6 department cards share the same structure. They were generated from one data list by a small script, so every card is identical in structure and there are no copy-and-paste mistakes. The result is the plain HTML in `index.html`; no script is needed to view or edit it.

## Images

- 50 portraits were chosen from about 240 Pexels search results, taking one per photographer so the same model does not appear twice.
- Each was cropped to a square around the face and saved as a 320 x 320 WebP, about 7 KB each.
- Photographers are listed in `images/CREDITS.md`.

## Testing checklist

Fill in the result column as you test.

| # | Test | How | Expected | Result |
| --- | --- | --- | --- | --- |
| 1 | HTML validation | validator.w3.org, upload index.html | No errors | |
| 2 | CSS validation | jigsaw.w3.org/css-validator | No errors (`color-mix` and `subgrid` may show as warnings in older validators) | |
| 3 | Lighthouse | DevTools > Lighthouse > Mobile | Accessibility 95+ | |
| 4 | Phone layout | Device toolbar, 375px | Swipeable departments, 1 card per row | |
| 5 | Tablet layout | 768px | 2 cards per row | |
| 6 | Desktop layout | 1280px | Sidebar beside the directory, 3 cards per row | |
| 7 | Card alignment | Look at a row with a long email or title | Rows inside neighbouring cards line up | |
| 8 | Search | Type "react" | 2 people (Tobi Adeyemi, Imani Brooks) | |
| 9 | Combined filters | Human Resources + "Available now only" | 4 people | |
| 10 | No results | Search "zzzz" | Message and clear button | |
| 11 | Department link | "View the team" on Design & UX | 7 people | |
| 12 | Sort | "Newest first" | Ravi Kapoor first | |
| 13 | List view | Click "List" (900px and wider) | One row per person | |
| 14 | Profile | Click "View profile", then Escape | Dialog opens and closes, focus returns | |
| 15 | Keyboard | Tab through filters and cards | Focus always visible | |
| 16 | No JavaScript | Disable JavaScript, reload | All 50 people shown | |

## Screenshots to take

1. Desktop, full page (1280px)
2. Tablet (768px)
3. Phone (375px), showing the swipeable departments
4. A search result, for example "react"
5. Combined filters, for example HR + available now
6. The "No one matches" message
7. List view
8. A profile dialog open
9. DevTools Grid overlay on the employee grid, showing the subgrid lines (Elements panel > click the "grid" badge)
10. Lighthouse scores
11. W3C validator result

## Demo script (about 3 minutes)

1. Point out the demo-data note, then scroll through departments and the directory.
2. Open DevTools, turn on the Grid overlay and show the named page areas.
3. Show the card grid changing columns as the window is resized.
4. Show subgrid: a card with a long email, and how the rows of the neighbouring cards line up.
5. Switch to List view: same HTML, different grid.
6. Search for a skill, combine filters, use "View the team".
7. Open a profile, close it with Escape, and show focus going back.
8. Switch to phone size and swipe the departments.

## Known limitations

| Limitation | Possible improvement |
| --- | --- |
| Data is written into the HTML | Load employees from a JSON file or an HR system API |
| Status does not update live | Connect to a calendar or chat system |
| No org chart | Add a reporting-line view using the manager data |
| Filters reset when the page reloads | Keep them in the URL, for example `?dept=design` |
| Fonts load from Google Fonts | Serve the font files from the project |
