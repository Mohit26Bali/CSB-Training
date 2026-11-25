# Mini Capstone Project - Summit Bouldering

A complete one-page website for **Summit Bouldering**, a fictional indoor climbing centre. It brings everything together in one site:

- semantic HTML5
- Flexbox and CSS Grid layouts
- forms with validation
- CSS animations
- responsive design, from a 320px phone up to a wide desktop
- accessibility

> **Demo:** Summit Bouldering, its coaches and reviews are fictional (the page says so). Bookings are not real and nothing is charged. Photos are from Pexels, and the coach portraits are stock photos of models.

## How to run

Open `index.html` in any modern browser. No server or install is needed.

## Files

| File | Purpose |
| --- | --- |
| index.html | The whole site: 11 sections, from the hero to the footer |
| css/styles.css | All styling: tokens, layouts, components, animations, print styles |
| js/main.js | Menu, header, menu highlight for the section in view, scroll reveal, counters, timetable filters, price switch, coach bios, gallery viewer, reviews carousel, opening hours, newsletter |
| js/booking.js | The three-step booking form: fields, checks, live price, confirmation, calendar file |
| images/hero-1920.webp, hero-960.webp | Hero photo in two sizes (`<picture>`) |
| images/gym/, coaches/, gallery/, community.webp | Section photos |
| images/favicon.svg | Tab icon |
| images/CREDITS.md | Photographer credit for every photo |
| screenshots/ | Your captured screenshots |

## Sections

| # | Section | Main techniques |
| --- | --- | --- |
| 1 | Header | Sticky header; Flexbox; menu button with `aria-expanded` on phones; animated underline; the menu link for the section in view is highlighted |
| 2 | Hero | Full-bleed `<picture>`, gradient overlay for readable text, `clamp()` headline, staggered entrance animation, floating decorative holds, counting numbers |
| 3 | Ticker | Endless scrolling strip (CSS `@keyframes`), pauses on hover |
| 4 | New to climbing? | `<ol>` of 3 steps laid out with Flexbox (`flex: 1 1 16rem`) |
| 5 | The gym | "Bento" CSS Grid: one big tile over 2 × 2 cells, four small tiles and a full-width banner |
| 6 | Timetable | 7-column Grid, filters by type, one day at a time on phones, "Today" highlighted, Book buttons linked to the form |
| 7 | Prices | Monthly/yearly switch (`aria-pressed`), `<data>` prices, highlighted card |
| 8 | Coaches | Auto-fit card grid; bios open with an animated grid row |
| 9 | Gallery | Grid with every photo placed by `grid-area`; full-screen `<dialog>` viewer with arrow keys |
| 10 | Reviews | Scroll-snap carousel with previous/next buttons |
| 11 | Book | Three-step form with validation, an error summary, a sticky live price summary, a confirmation and an "Add to calendar" file |
| 12 | FAQ | `<details>`/`<summary>` accordion where only one answer is open at a time (`name="faq"`), opening smoothly where supported |
| 13 | Visit | 12-column Grid; `<address>`, opening-hours `<table>` with today's row, live "Open now" badge |
| 14 | Footer | Grid; newsletter form |

## Design decisions

### HTML5 semantics

- **Landmarks:** `header`, `nav` (main menu and footer menu), `main`, `section` (each with an `aria-labelledby` heading), `aside` (booking summary) and `footer`.
- **One `h1`** ("Climb more. Worry less."). Each section has an `h2`, and the items inside use `h3`/`h4`.
- **Meaningful elements:**

  | Element | Used for |
  | --- | --- |
  | `<ol>` | the three steps and the booking progress, where order matters |
  | `<article>` | each coach card |
  | `<figure>` / `<blockquote>` / `<figcaption>` | reviews |
  | `<time datetime>` | class times |
  | `<data value>` | prices |
  | `<output>` | the booking total |
  | `<table>` with `<caption>` and `scope` | opening hours |
  | `<address>` | contact details |
  | `<dialog>` | the photo viewer |
  | `<details>` | the FAQ |
- **The timetable is real HTML:** it works without JavaScript, and JavaScript only filters it.
- **Structured data:** `SportsActivityLocation` JSON-LD with the address, phone number and opening hours.

### CSS layout: when Flexbox, when Grid

- **Flexbox for one direction:** the header, buttons in a row, chips, the three steps (they wrap to new lines on their own), form rows and the card footers.
- **Grid for two directions:**
  - **Bento:** 4 columns; the main tile spans 2 × 2, and the last tile spans the full width so no row is half empty.
  - **Timetable:** 7 equal day columns, then 4 on tablets, then 1 day at a time on phones.
  - **Prices and coaches:** `repeat(auto-fit, minmax(min(100%, 15rem), 1fr))`, so they reflow with no media queries.
  - **Gallery:** each photo is placed with `grid-area` on a 4 × 4 grid, so there are no gaps. The HTML order matches the reading order, so keyboard users move through the photos in the order they see them.
  - **Visit:** a 12-column grid (4 + 3 + 5), which becomes 6 + 6, then 1 column.
  - **Booking:** the form and a 20rem summary that stays in view (sticky) on desktop.
- **Responsive without fixed widths:** `clamp()` for type and spacing, `min()` in grid tracks, and `max-width` on text.

### Animation (all optional for the reader)

- **The animations:**
  - hero text rising in, staggered with a `--i` custom property
  - slow zoom on the hero photo
  - floating holds and the scrolling ticker
  - scroll reveal (an `IntersectionObserver` adds `.is-visible`)
  - counting numbers
  - the price flip
  - the menu icon turning into an ×
  - coach bios sliding open
  - the booking steps sliding in
  - the photo viewer zooming open
  - the tick being drawn on the confirmation
  - the pulsing "Open now" dot
- **Reduced motion:** every animation is inside `@media (prefers-reduced-motion: no-preference)`. Readers who ask for less motion see the same content with no movement, and the counters show their final number straight away.
- **Hover effects:** button lift, photo zoom and the underline grow, only where the reader allows motion.

### Accessibility

- **Navigation:** a skip link, a visible focus outline on everything, and the menu button announces whether it is open. Escape closes the menu and returns focus to the button, and so does clicking anywhere else.
- **Contrast:**
  - Body text is dark ink on chalk.
  - The orange accent is only used behind dark text, never as small text on white. Orange-coloured text uses a darker shade (`#b23a12`).
  - White text on the hero photo sits on a dark gradient.
- **Not by colour alone:** class types have a coloured stripe and a written label. "3 spaces left" and "Full" are written, not just coloured.
- **Booking form:**
  - every field has a visible `<label>`, with hints linked by `aria-describedby`
  - groups use `fieldset`/`legend`
  - errors are written next to each field and marked `aria-invalid`
  - an error summary at the top gets focus, and its links jump to each field
  - the current step is marked `aria-current="step"`
  - after each step, focus moves to the new step
  - after booking, focus moves to the confirmation heading
- **Gallery:** each photo is a button labelled "View larger: …". The `<dialog>` viewer works with the keyboard (arrows, Escape) and returns focus to the photo you opened.
- **Reviews carousel:** a focusable region with a label, so keyboard users can scroll it, and the buttons disable at each end. The stars have a text label ("5 out of 5 stars").
- **Decoration:** decorative images and SVGs use `alt=""` / `aria-hidden="true"`. The ticker is hidden from screen readers because it repeats information from elsewhere on the page.

### User experience

- **One main action everywhere:** "Book" in orange in the header, hero, prices and timetable. Each class's Book button opens the form with that class already chosen.
- **The booking form only asks what it needs:**
  - **Taster:** asks for a date and a time.
  - **Day pass:** asks for a date.
  - **Class:** picks from the timetable, and the date is calculated automatically (the next time that class runs).
  - **Shoe hire:** hidden when shoes are included.
  - **Kids' Club:** locks the adults counter at 0, because it's a drop-off class.
- **Sensible rules with clear messages:**
  - no past dates, and no more than 60 days ahead
  - no taster start time that has already passed today
  - under-16s need an adult
  - a class can't be overbooked ("Only 1 space is left in this class")
- **Live price summary:** it updates on every change, so there are no surprises.
- **Confirmation:** gives a booking reference and offers an `.ics` calendar file.
- **Opening hours:** today's row is highlighted, and the badge says whether the gym is open right now.

## Testing checklist

Fill in the result column as you test.

| # | Test | How | Expected | Result |
| --- | --- | --- | --- | --- |
| 1 | First view | Open `index.html` | Hero animates in; numbers count up | |
| 2 | Menu (phone) | 375px: Menu, then Escape | Opens; Escape closes and focus returns | |
| 3 | Menu highlight | Scroll down the page | The link for the section in view is underlined | |
| 4 | Timetable filter | Choose "Kids" | 3 classes; Monday says "No classes of this type" | |
| 5 | Timetable (phone) | 375px: tap "Sat" | Only Saturday; "Showing 3 classes on Saturday" | |
| 6 | Full class | Tuesday 18:30 | "Full" and a disabled button | |
| 7 | Price switch | Yearly | £560 and £420 "per year" with a flip | |
| 8 | Coach bio | More about Frank | Bio slides open; button says "Less about Frank" | |
| 9 | Gallery | Open a photo; arrows; Escape | Viewer, "3 / 8", wraps around; focus returns | |
| 10 | Reviews | Next / previous | Scrolls one review; buttons disable at the ends | |
| 11 | Booking errors | Continue with nothing chosen | Error summary with 2 links; inline errors | |
| 12 | Dates | A past date; 90 days ahead | Two different errors | |
| 13 | Adults rule | 0 adults, 1 child | "Under-16s must climb with an adult…" | |
| 14 | Price | Taster, 2 adults + 1 child | £60.00 | |
| 15 | Kids' Club | Choose Sat 09:30 Kids' Club, 2 children | Adults locked at 0; "Only 1 space is left…" | |
| 16 | Book from timetable | Monday 18:00 Book | Form opens with that class chosen | |
| 17 | Confirm | Complete all steps | Confirmation, reference, focus on the heading | |
| 18 | Calendar | Add to calendar | `.ics` file downloads and opens in a calendar app | |
| 19 | FAQ | Open another question | The previous one closes | |
| 20 | Open now | Look at Visit | Correct open/closed text; today's row highlighted | |
| 21 | Reduced motion | Turn on "reduce motion" in the OS | No animations; all content visible | |
| 22 | Keyboard only | Tab through the whole page | Everything reachable; focus always visible | |
| 23 | Widths | 320, 375, 768, 1024, 1440px | No sideways scrolling; layouts change as described | |
| 24 | No JavaScript | Disable JS | All content readable; menu links wrap; all days shown | |
| 25 | Validation | validator.w3.org | No errors | |
| 26 | Lighthouse | DevTools > Lighthouse | Accessibility 95+ | |

**Automated checks I ran in headless Edge:**

| Check | Result |
| --- | --- |
| Page behaviour at desktop width | 28/28 pass |
| Page behaviour at phone width, including the menu, one-day timetable and no sideways scroll | 30/30 pass |
| Booking form: every error, price rule, Kids' Club rule, class spaces, confirmation and the timetable Book buttons | 38/38 pass |
| HTML tag and id check | pass |

## Screenshots to take

1. Hero on desktop (1440px) and on a phone
2. The gym bento grid, with the DevTools Grid overlay
3. Timetable on desktop, then on a phone with one day chosen
4. Prices, monthly and yearly
5. A coach bio opened
6. Gallery grid, and the photo viewer
7. Booking step 1 with the error summary
8. Booking step 2 with the live price summary
9. Booking confirmation
10. FAQ with one answer open
11. Visit: the "Open now" badge and the hours table
12. Phone menu open
13. Tablet (768px) overview
14. Lighthouse and validator results

## Demo script (about 4 minutes)

1. **Hero:** load the page to show the entrance animation and counters. Point out the semantic structure in DevTools (landmarks, one `h1`).
2. **Scroll down:** show the reveal animation and the menu link highlighting the section in view.
3. **The gym:** show the bento grid with the Grid overlay. Explain Flexbox vs Grid using the steps and the bento.
4. **Timetable:** filter to "Kids", then press Book on a class to show it filling in the form.
5. **Booking:**
   - press Continue with gaps to show the error summary and inline errors
   - fix them and show the live price
   - try 0 adults with a child
   - finish, then show the confirmation and the calendar file
6. **Prices and gallery:** switch prices to Yearly, then open the gallery viewer and use the arrow keys and Escape.
7. **Phone width:** resize to show the menu button, the one-day timetable, the swipeable reviews and the stacked form.
8. **Reduced motion:** turn it on in DevTools (Rendering) and show that everything still works without animation.

## Known limitations

| Limitation | Possible improvement |
| --- | --- |
| Bookings are not stored or sent | Send them to a booking system with real-time spaces |
| Class spaces are fixed in the page | Load live availability |
| Opening hours ignore bank holidays | Add a list of special dates |
| Reviews are examples | Show real, verified reviews |
| Fonts load from Google Fonts | Serve font files from the project |
