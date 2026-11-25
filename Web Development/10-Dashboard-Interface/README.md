# Dashboard Interface - Wanderlane Admin

A modern admin dashboard for **Wanderlane Travel**, the agency from the Travel Agency Website project. It is the "back office" for that site: revenue, bookings, destinations, a bookings table, enquiries to answer and team activity. It is built with **CSS Grid and Flexbox**, and every chart is drawn by hand in **SVG**, with no chart library.

> **Demo:** Wanderlane Travel is fictional. All bookings, customers and figures are generated example data, as the note at the top of the dashboard says.

## How to run

Open `index.html` in any modern browser (Chrome, Edge, Firefox). No server, install or internet is needed, apart from the web font.

The data is generated from a fixed seed, so it is the same pattern every time, with dates counted back from **today**. The dashboard always looks current; the exact numbers shift by a day as time passes.

## Files

| File | Purpose |
| --- | --- |
| index.html | The app shell: sidebar, header, KPI cards and six widgets |
| css/styles.css | All styling: shell grid, sidebar states, header grid, 12-column widget grid, container queries, both themes |
| js/data.js | Demo data: about 400 days of bookings for the 8 packages, plus enquiries, notifications and team activity |
| js/charts.js | Hand-drawn SVG charts: area chart (with hover and keyboard read-out), sparklines, donut |
| js/main.js | Calculates every widget from the bookings, and runs the date range, table, dropdowns, sidebar, drawer and theme |
| images/favicon.svg | Tab icon |
| screenshots/ | Your captured screenshots |

## One source of truth

Every number on the screen is **calculated from the same list of bookings**, so the widgets always agree:

| Widget | Calculated as |
| --- | --- |
| Revenue | Sum of **confirmed** bookings in the period |
| Bookings | Bookings that are not cancelled |
| Average booking | Revenue ÷ confirmed bookings |
| Change % | This period compared with the period before it, for example the 30 days before the last 30 |
| Revenue chart | The same revenue, per day (7 or 30 days) or per month (12 months) |
| Donut | Non-cancelled bookings per destination. Its total equals the Bookings card. |
| Package bars | Confirmed revenue per package |
| Bookings table | All bookings in the period, including cancelled ones (the subtitle says so) |

The automated test checks these totals match each other.

## Page structure

| Part | HTML elements |
| --- | --- |
| Sidebar | `<aside>` with brand link, `<nav aria-label="Dashboard">`, link to the public website, collapse button (`aria-pressed`) |
| Header | `<header>`: menu button (`aria-expanded`), the page `<h1>`, a `<form role="search">`, a date-range `<fieldset>` of radio buttons, notifications and profile dropdowns (`aria-expanded`, `aria-controls`), theme button (`aria-pressed`) |
| KPI cards | `<section>` with a `<ul>` of cards; each change is written in words ("Up 7.9%"), not just ▲ |
| Widgets | One `<section>` each, with an `<h2>` |
| Revenue chart | `<figure>` with a text summary in `<figcaption>`, and a `<details>` "View as a table" with the full data in a `<table>` |
| Bookings table | Real `<table>` with `<caption>`, `<th scope>`, sortable headers as buttons with `aria-sort`, `<time>` dates, and pagination in a `<nav>` |
| Activity | `<ol>` timeline |

## Design decisions

### The app shell (Grid)

```css
.app {
    display: grid;
    grid-template-columns: var(--sidebar-width) minmax(0, 1fr);
    grid-template-areas:
        "sidebar header"
        "sidebar main";
}
```

| Width | Shell |
| --- | --- |
| Phone (under 768px) | `"header" "main"`: the sidebar becomes a **drawer** that slides in from the left |
| Tablet (768 to 1199px) | The sidebar starts **collapsed to icons** (4.75rem); the button expands it |
| Desktop (1200px and up) | Full sidebar (15.5rem); it can be collapsed |

- **Collapsing the sidebar only changes one custom property,** `--sidebar-width`. The grid animates to the new width.
- **Collapsed labels stay accessible.** They are visually hidden, not removed, so screen readers still read "Bookings". A tooltip shows the label on hover or keyboard focus.
- **The choice is remembered** (`localStorage`).

### The header (Grid areas)

```
Phone:    [☰] [Overview        ] [🔔 🌙 AK]
          [Search ..........................]
          [ 7 days | 30 days | 12 months    ]

Tablet:   [Overview      ] [7d|30d|12m] [🔔 🌙 AK]
          [Search ..................................]

Desktop:  [Overview] [Search ......] [7d|30d|12m] [🔔 🌙 AK Amira Khan]
```

The same five elements are rearranged only by changing `grid-template-areas`.

### The widget grid (12 columns)

| Widget | Phone | Tablet (768px+) | Desktop (1200px+) |
| --- | --- | --- | --- |
| KPI cards | 1 per row | 2 × 2 | 4 in a row |
| Revenue chart | 12 | 12 | **8** |
| Bookings by destination | 12 | 12 | **4** |
| Recent bookings | 12 | 12 | 12 |
| Revenue by package | 12 | 6 | 4 |
| Enquiries | 12 | 6 | 4 |
| Team activity | 12 | 12 | 4 |

The HTML order matches the visual order at every width, so keyboard and screen-reader order is always correct (no `dense` packing).

### Container queries (widgets respond to their own width)

Each widget is a container (`container-type: inline-size`), so it lays itself out based on **the space it gets**, not the screen size:

| Widget | Narrow | Wide |
| --- | --- | --- |
| KPI section | 1 column | 2 columns (520px+), 4 columns (960px+) |
| Donut | Legend under the chart | Legend beside the chart (440px+) |
| Bookings table | **Stacked cards** (under 640px) | Normal table |
| Enquiries | Button under the text | Button on the right (480px+) |

**Why it matters:** the donut is in a narrow 4-column slot on desktop but a full-width slot on tablet. Its layout follows the slot, which a media query could not do.

### Flexbox

Flexbox handles the one-direction layouts:
- the sidebar column, with the bottom links pushed down by `margin-top: auto`
- the header's action buttons
- widget headers
- pagination
- the legend rows

### Charts (SVG, no library)

- **Revenue area chart:**
  - drawn at the widget's real width, and redrawn when it resizes (`ResizeObserver`)
  - gridlines and axis labels
  - a gradient fill
  - a hover crosshair with a tooltip
- **Sparklines** in the KPI cards: green when the period is up, red when down.
- **Donut:** one SVG circle per destination using `stroke-dasharray`, with the total in the middle.
- **Package bars:** plain HTML and CSS. The width comes from a custom property (`--size: 64%`) and animates.

### Making the charts accessible

- Each chart has a **text summary**, for example "Total £459,450. Highest: Sat 26 Sep, £36,620".
- The revenue chart can be **read with the keyboard:** focus it and use ← →, Home and End. Each point is announced, for example "Thu 8 Oct: £4,300".
- **"View as a table"** shows the same numbers as a proper data table.
- The donut's numbers and percentages are written out in the legend.
- Up or down is shown with an arrow, a colour **and** the words "Up" or "Down" for screen readers.

### Visual design

- **Colours:** Wanderlane's teal on a cool grey background, white cards with a soft shadow, and a dark teal sidebar. Each package keeps one colour in the donut and the bars.
- **Dark theme:** follows the system setting; the 🌙 button overrides it and is remembered. Chart colours come from CSS variables, so the charts switch too.
- **Font:** Inter, with tabular numbers (`font-variant-numeric: tabular-nums`) so figures line up.
- **Status badges:** green Confirmed, amber Pending, red Cancelled, always with the word.

### Accessibility

- **Landmarks:** `<aside>`, `<nav>`, `<header>`, `<main>`. There is one `<h1>` ("Overview"), then `<h2>` per widget.
- A skip link goes straight to the dashboard.
- **Phone drawer:**
  - opens from the ☰ button and moves focus into the menu
  - keeps focus inside (Tab loops)
  - makes the rest of the page `inert`
  - closes with Escape, the ✕ button, the backdrop or choosing a link
  - returns focus to ☰
- **Dropdowns** (notifications, profile) use `aria-expanded` and `aria-controls`, and close with Escape (focus returns to the button) or a click outside.
- **The notifications button says how many are unread** ("Notifications, 4 unread").
- **Sortable columns** are buttons inside the headers, and `aria-sort` reports the order.
- **Announced changes:** changing the date range ("Showing the last 7 days"), the table's page, and toasts such as "Enquiry marked as answered" (`role="status"`).
- **Focus after "Mark answered"** moves to the next enquiry's button, so keyboard users don't lose their place.
- Every control is at least 44px tall, and focus outlines are visible.
- `prefers-reduced-motion` turns off the sidebar, drawer and chart animations.

### User experience

- **Date range** (7 days, 30 days, 12 months): every widget recalculates, the page fades briefly to show it updated, and the choice is remembered.
- **Header search** filters the bookings table as you type (booking number, customer, package, destination or status). Press Enter to jump to the table.
- **Bookings table:** sort any column (click again to reverse), filter by status, 8 rows per page, and a "no bookings match" message.
- **"Mark answered"** removes the enquiry and updates the KPI card and sidebar badge. It is remembered.
- **Notifications:** an unread count, a dropdown and "Mark all as read".
- **The sidebar highlights the section on screen** as you scroll (`IntersectionObserver`).
- **The greeting** changes with the time of day ("Good morning, Amira").
- **"View website"** links to the public Wanderlane site, if the two folders are kept together.

## Testing checklist

Fill in the result column as you test.

| # | Test | How | Expected | Result |
| --- | --- | --- | --- | --- |
| 1 | Totals agree | Compare the Bookings card with the donut centre | Same number | |
| 2 | Date range | Click 7 days, then 12 months | Every widget updates; chart has 7 points, then 12 months | |
| 3 | Remembered | Choose 12 months, reload | Still 12 months | |
| 4 | Chart hover | Move the mouse over the revenue chart | Crosshair and tooltip with date and amount | |
| 5 | Chart keyboard | Tab to the chart, press End, then ← | Tooltip moves; a screen reader reads each point | |
| 6 | Chart table | "View as a table" | Same figures in a table | |
| 7 | Sort | Click "Value", then again | High to low, then low to high; arrow shown | |
| 8 | Filter | Status: Cancelled | Only cancelled bookings | |
| 9 | Search | Type "maldives" in the header | Only Maldives bookings; subtitle shows the search | |
| 10 | No results | Type "zzzz" | "No bookings match" | |
| 11 | Pages | Next, then Previous | Rows 9–16, then 1–8 | |
| 12 | Enquiry | "Mark answered" on the first enquiry | List, KPI card and sidebar badge go from 6 to 5; toast shown | |
| 13 | Notifications | Bell, then "Mark all as read", then Escape | Count disappears; panel closes; focus on bell | |
| 14 | Theme | 🌙, then reload | Dark theme kept; charts use dark colours | |
| 15 | Collapse | "Collapse" in the sidebar (desktop) | Icons only; hover shows labels; remembered | |
| 16 | Tablet | 900px wide | Icon sidebar, KPI cards 2 × 2 | |
| 17 | Phone | 375px | ☰ menu; KPI cards stacked; table as cards | |
| 18 | Drawer | ☰, then Tab repeatedly, then Escape | Focus stays in the menu; Escape closes; focus back on ☰ | |
| 19 | Container query | Compare the donut on desktop and tablet | Legend under it on desktop (narrow slot), beside it on tablet | |
| 20 | Keyboard | Tab through the whole page | Everything reachable, focus always visible | |
| 21 | Reduced motion | Turn on "reduce motion" | No sliding or animated bars | |
| 22 | HTML validation | validator.w3.org | No errors | |
| 23 | Lighthouse | DevTools > Lighthouse | Accessibility 95+ | |

## Screenshots to take

1. Desktop (1440px), light theme
2. Desktop, dark theme
3. Sidebar collapsed, with a tooltip showing
4. Tablet (900px): icon sidebar, KPIs 2 × 2
5. Phone (375px): stacked layout
6. Phone with the drawer open
7. Revenue chart tooltip
8. 7 days and 12 months views
9. The bookings table sorted and filtered, and the same table as cards on a phone
10. Notifications dropdown
11. DevTools Grid overlay on `.app` and on `.dashboard`
12. DevTools showing a container query applied (Styles panel)
13. Lighthouse and validator results

## Demo script (about 3 minutes)

1. Explain that this is the back office for the Wanderlane site and every figure comes from one booking list. Show that the Bookings card equals the donut total.
2. Switch 7 days, 30 days and 12 months, and watch every widget update.
3. Hover over the revenue chart, then use the arrow keys on it. Open "View as a table".
4. Sort and filter the bookings table, and search "maldives" from the header.
5. Mark an enquiry as answered: the card, the badge and the list all update.
6. Turn on the DevTools Grid overlay on `.app` and `.dashboard` to show the named areas and the 12 columns.
7. Collapse the sidebar, switch to dark mode, then resize to tablet and phone. Open the drawer and close it with Escape.
8. Point out the container queries: the donut and the table change layout with their own width.

## Known limitations

| Limitation | Possible improvement |
| --- | --- |
| Data is generated in the browser | Load real bookings from a database through an API |
| No sign-in | Add accounts with roles (admin, agent) |
| Answering an enquiry only marks it | Open a reply form and send an email |
| Charts are custom and simple | A charting library for zoom, export and more chart types |
| Fonts load from Google Fonts | Serve the font files from the project |
