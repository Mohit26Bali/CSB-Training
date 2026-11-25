# Event Management Portal

An event portal for **Neon Frame Festival 2026**, a fictional three-day anime and film festival. It lists upcoming events, shows the full schedule as a timetable, and lets visitors book seats, book tickets or join a waiting list.

> **Demo:** the festival, venue and bookings are fictional. The films are real, but the festival is not connected to their studios. The photos show fan cosplay, figures and merchandise from Pexels, not official artwork. Bookings are saved only in your own browser. The page shows this note at the top.

## How to run

Open `index.html` in any modern browser (Chrome, Edge, Firefox). No server or install is needed. An internet connection loads the web fonts; without it the page uses system fonts.

## Files

| File | Purpose |
| --- | --- |
| index.html | Page structure: hero, featured event, 15 event cards, 3-day schedule, venue, FAQ, two dialogs |
| css/styles.css | All styling: neon theme, card grid, timetable grid, seat map, responsive rules |
| js/main.js | Filters, sorting, schedule tabs, countdown, booking dialog and seat map, My tickets, calendar files |
| images/hero-tokyo-neon.webp | Hero background, 1920 x 1200 |
| images/venue-cinema-sign.webp | Venue photo |
| images/events/ | 15 event photos, 800 x 533 WebP (about 650 KB in total) |
| images/favicon.svg | Browser tab icon |
| images/CREDITS.md | Photographer credit for every photo |
| screenshots/ | Your captured screenshots |

## The festival

**Friday 20 to Sunday 22 November 2026**, Lumen Arts Centre, Manchester (fictional venue). There are 4 venues: Screen 1, Screen 2, Main Hall and Studio.

| Day | Events |
| --- | --- |
| Friday | Cosplay Meet-up & Opening Party (drop in), **Spirited Away** (opening night), Anime Karaoke Night |
| Saturday | Manga Drawing Workshop, **My Neighbor Totoro** family matinee (sold out), Cosplay Championship (free), **One Piece Film: Red** sing-along, **Demon Slayer: Infinity Castle** (Saturday night headliner), **Naruto** late-night marathon |
| Sunday | Figure Painting Workshop, **Fullmetal Alchemist: The Sacred Star of Milos**, Pokémon TCG Tournament, **Dragon Ball Super: Super Hero**, Voice Acting Masterclass, **Attack on Titan: The Last Attack** (closing night) |

The Artist Alley & merch market runs all day Saturday and Sunday in the Foyer, with no booking needed.

## Page structure

| Part | HTML elements |
| --- | --- |
| Demo note | `<p>` at the very top |
| Header | `<header>`, logo, `<nav aria-label="Primary">`, "My tickets" `<button>` with a count |
| Hero | `<section>` with the only `<h1>`, dates in `<time datetime>`, a countdown with `role="timer"` |
| Featured event | `<article>` for opening night |
| Upcoming events | `<section>` with a search `<form role="search">`, category radio "chips" in a `<fieldset>`, and a `<ul>` of 15 `<article>` cards |
| Event card | Photo, date badge, category tag, `<h3>` title, start and end in `<time datetime>`, venue, seats in a `<meter>`, price in `<data value>`, booking button, "Add to calendar" |
| Schedule | Tabs (`role="tablist"`, `role="tab"`, `role="tabpanel"`), one timetable per day, each session an `<article>` linking to its card |
| Venue | `<figure>`, `<dl>` of facts, `<address>` |
| FAQ | `<details>` / `<summary>` |
| Booking | native `<dialog>` with a form: seat map (`<fieldset>` of checkboxes), number of tickets, name and email, total in `<output>` |
| My tickets | native `<dialog>` listing bookings, with cancel buttons |

## Design decisions

### Layout techniques

1. **Header with grid areas.**
   - On phones it has two rows: logo and tickets on top, the nav underneath (`"logo tickets" "nav nav"`).
   - From 900px it becomes one row (`"logo nav tickets"`).
   - Only `grid-template-areas` changes.
2. **Hero with stacked layers.** The photo, a dark gradient and the text all sit in the same grid cell (`grid-area: 1 / 1`), so no absolute positioning is needed.
3. **Event cards** use `repeat(auto-fill, minmax(min(100%, 300px), 1fr))`, which gives 1 to 3 columns without breakpoints.
   - Each card is a flex column.
   - The seats bar has `margin-top: auto`, so prices and buttons line up at the bottom of every card in a row.
4. **The timetable is the main CSS Grid feature.**
   - Rows are 30-minute slots, and columns are the 4 venues.
   - Each session is placed with CSS custom properties written into the HTML, for example `style="--col: 2; --start: 4; --end: 9"` with `grid-row: var(--start) / var(--end)`.
   - The 3-hour Demon Slayer slot covers 6 rows; a 1.5-hour matinee covers 3. So the box size shows the running time.
   - The hour labels stretch across all columns (`grid-column: 1 / -1`) to draw the guide lines.
   - The sessions are written in **time order** in the HTML, whatever their column. Screen readers and phones get a sensible list; Grid only changes where they appear on a wide screen.
   - Under 900px the timetable becomes a simple list in time order.
5. **The seat map is a grid** of 15 columns: row letter, 6 seats, aisle, 6 seats, row letter.
   - Each seat is a real checkbox. Its label is styled as a seat, so keyboard and screen-reader users can pick seats too.
   - Each seat has a hidden name, for example "Row C, seat 5", or "Row A, seat 3, taken".
6. **`:has()`** styles the selected category chip, chosen seats and taken seats with no extra classes.

### Responsive design

| Width | Layout |
| --- | --- |
| Phone (under 640px) | One card per row. Timetable shown as a list. Nav under the logo. Seat map shrinks to fit. |
| Tablet (640px and up) | Two cards per row, two-column booking form |
| 768px and up | Featured event and venue side by side |
| 900px and up | One-row header, full timetable grid |
| Desktop (about 1000px and up) | Three cards per row |

### Visual design

- **Neon night theme:** a near-black indigo background with neon pink (`#ff3d9a`) and cyan (`#2ee6ff`), like cinema signs at night.
- **Glowing title:** "Neon Frame" uses layered `text-shadow`, and "Neon" flickers gently.
- **Category colours** come from one CSS custom property, `--cat`. It colours the card's top border, date badge, category tag, time, button, timetable block and hover glow:

  | Category | Colour |
  | --- | --- |
  | Screening | Pink |
  | Cosplay | Violet |
  | Workshop | Cyan |
  | Gaming | Yellow |
  | Music & talks | Orange |

- **Fonts:** Unbounded, a wide display font for headings, and Outfit for body text.
- **Seat map:**
  - free seats are bright violet and numbered
  - chosen seats are neon pink
  - taken seats are dim with a ×
  - wheelchair spaces have a cyan outline and the ♿ symbol

### Accessibility

- Landmarks (header, nav, main, footer) and a clear heading order: `h1`, `h2` per section, `h3` per card.
- A skip link goes straight to the events.
- Every photo has alt text describing the cosplay or object. The hero photo is decorative (`alt=""`).
- **Buttons have unique names for screen readers.** "Choose seats" also says which event, for example "Choose seats: Spirited Away".
- **Seats left is shown as a `<meter>` and as text** ("Only 8 left", "Sold out"), so colour is never the only signal.
- **Result count** ("Showing 2 of 15 events") is announced when filters change.
- **Tabs follow the ARIA pattern:** arrow keys, Home and End move between days, and only the selected tab is in the Tab order.
- **Dialogs:**
  - keep focus inside and close with Escape
  - return focus to the button that opened them
  - move focus to the "You're booked!" message when booking succeeds
- **Booking form errors** are shown as text under each field, and focus moves to the first problem.
- **The countdown** is a `role="timer"` with live announcements turned off, so it doesn't interrupt every minute.
- All targets are at least 44px tall, except the seats. Seats are smaller on phones but spaced apart, which meets the WCAG 2.2 spacing exception.
- **Reduced motion:** `prefers-reduced-motion` stops the flicker, the hover lift and the transitions.

### User experience

- **Countdown** to opening night, updated every 30 seconds.
- **Filters** can be combined: search (several words narrow it, for example "late night"), category, day, "Hide sold out" and "Free only".
- **Sort** by date, by price, or by "Fewest seats left" (popular events first).
- **Schedule links** jump to the event card and focus it. If the filters were hiding that card, they are cleared first.
- **Three booking types:**
  - **Choose seats** (cinema screens) opens a seat map, with up to 6 seats.
  - **Book tickets** (hall and studio events) uses a number input. The Pokémon tournament allows one entry per player.
  - **Join waiting list** for sold-out events. Drop-in events say "Just turn up".
- **After booking:**
  - the button changes to "✓ Booked" and the seats-left count goes down
  - the featured banner updates, and the "My tickets" count goes up
  - bookings stay after a page reload (`localStorage`)
- **My tickets** lists everything in date order, with seats, total and reference, and lets you cancel. Cancelling returns the seats.
- **Add to calendar** downloads a standard `.ics` file that opens in Google Calendar, Outlook or Apple Calendar. Times are written in UTC; November in the UK is GMT, which is the same as UTC.

### Progressive enhancement

- Without JavaScript:
  - all events, the full schedule (all three days, one after another) and the venue information still show
  - booking buttons are hidden, and a note gives the box office phone number instead

### How the HTML was produced

- The 15 event cards and the 3-day timetable share the same event data, for example the time, venue and seats.
- They were generated from **one list of events** by a small script, so a card and its timetable slot can never disagree.
- The result is the plain HTML in `index.html`; no script is needed to view or edit it.

### Copyright and images

- All photos are from Pexels and credited in `images/CREDITS.md`.
- They show fans' cosplay, figures and merchandise, not official studio artwork, which would not be free to use.
- Film titles are real, because a festival listing needs them. The festival itself is fictional.
- **Demon Slayer** uses a Tanjiro cosplay photo (checked haori, forehead scar and katana).
- **Fullmetal Alchemist:** Pexels has no genuine Fullmetal Alchemist cosplay. The card uses a light-painting photo of a glowing orange circle, which looks like a transmutation circle. Its alt text describes what the photo really shows.

## Testing checklist

Fill in the result column as you test.

| # | Test | How | Expected | Result |
| --- | --- | --- | --- | --- |
| 1 | Countdown | Load the page | Days, hours and minutes to 20 Nov 19:00 | |
| 2 | Search | Type "ghibli" | 2 events (Spirited Away, Totoro) | |
| 3 | Category | Click "Workshop" | 2 events | |
| 4 | Combined filters | Sunday + Screening | 3 events | |
| 5 | Hide sold out | Tick it | Totoro disappears, 14 events | |
| 6 | Free only | Tick it | 2 events | |
| 7 | No results | Search "zzzz" | Message and Clear filters button | |
| 8 | Sort | "Fewest seats left" | Totoro, Figure Painting, then Demon Slayer | |
| 9 | Tabs | Click Saturday, then press the Right arrow key | Saturday shows, then Sunday | |
| 10 | Timetable | Desktop width | Each event's box height matches its running time | |
| 11 | Schedule link | Filter to Workshop, then click Naruto in the schedule | Filters clear, Naruto card is focused | |
| 12 | Seat booking errors | Spirited Away > Confirm with nothing filled in | Name, email and seat errors | |
| 13 | Seat limit | Try to pick a 7th seat | Refused, "You can choose up to 6 seats" | |
| 14 | Seat booking | Pick 2 seats, fill in, confirm | "You're booked!", total £24, button shows "✓ Booked" | |
| 15 | Seats update | After test 14 | "Only 6 left", featured banner also says 6 | |
| 16 | Calendar | "Add to calendar" | `.ics` file downloads and opens in a calendar app | |
| 17 | One per player | Pokémon tournament with 2 tickets | Error: whole number from 1 to 1 | |
| 18 | Waiting list | Totoro > Join waiting list | "You're on the waiting list" | |
| 19 | My tickets | Header button | Bookings in date order | |
| 20 | Cancel | Cancel the Spirited Away booking | Seats return to "Only 8 left" | |
| 21 | Remembered | Book, then reload the page | Booking still shown | |
| 22 | Keyboard | Tab through filters, tabs, cards; pick seats with Space | Everything reachable, focus visible | |
| 23 | Phone | 375px | One column, schedule as a list, seat map fits | |
| 24 | No JavaScript | Disable JavaScript | All events and schedule shown, box office note | |
| 25 | Reduced motion | Turn on "reduce motion" in the OS | No flicker or hover movement | |
| 26 | HTML validation | validator.w3.org | No errors | |
| 27 | Lighthouse | DevTools > Lighthouse | Accessibility 95+ | |

## Screenshots to take

1. Desktop: hero with countdown and the featured event
2. Event cards with the filters
3. A filtered result, for example Sunday + Screening
4. The Saturday timetable (desktop)
5. DevTools Grid overlay on the timetable, showing rows and columns
6. Seat map with seats chosen
7. "You're booked!" confirmation
8. A card after booking ("✓ Booked", fewer seats)
9. My tickets dialog
10. Sold-out card with "Join waiting list"
11. Phone: hero, cards, and the schedule as a list
12. Tablet (768px)
13. Lighthouse and validator results

## Demo script (about 3 minutes)

1. Point out the demo note, the neon hero and the live countdown.
2. Filter the events: search "demon slayer", then "ghibli", then choose Sunday + Screening. Show the result count.
3. Open the schedule:
   - switch days with the arrow keys
   - turn on the DevTools Grid overlay to show how a film's box spans its running time
4. Click an event in the schedule to jump to its card.
5. Book Spirited Away:
   - pick seats on the seat map
   - show the total and an error message
   - confirm, and point out the card and banner updating
6. Download the calendar file. Open My tickets and cancel a booking.
7. Show the waiting list on the sold-out Totoro matinee.
8. Resize to phone width: the header becomes two rows and the timetable becomes a list.

## Known limitations

| Limitation | Possible improvement |
| --- | --- |
| Bookings live only in this browser | Save them on a server with real accounts and payments |
| Taken seats are generated, not real | Load the real seat plan from a booking system |
| Two visitors could book the same seat | A server would hold seats while you pay |
| Phone page is long (15 full cards) | Show compact cards or "Load more" on phones |
| Fonts load from Google Fonts | Serve the font files from the project |
