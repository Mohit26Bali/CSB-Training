# Travel Agency Website - Wanderlane Travel

A travel booking homepage for **Wanderlane Travel**, a fictional agency. It has eight destinations with **live local time and weather**, eight holiday packages you can filter, save and enquire about, and a carousel of customer reviews.

> **Demo:** Wanderlane Travel is not a real company.
> - The prices, packages and reviews are examples, and nothing can be booked.
> - The destinations are real places, and their weather is live.
> - The reviews are labelled "Example reviews written for this demo" on the page, and use initials instead of photos of real people.

## How to run

Open `index.html` in any modern browser (Chrome, Edge, Firefox). No server or install is needed.
- **With internet:** the destination cards show live weather.
- **Without internet:** they still show each place's local time, and a note explains the weather is unavailable.

## Files

| File | Purpose |
| --- | --- |
| index.html | The page: hero search, destinations, packages, why us, reviews, FAQ, enquiry form, enquiry dialog |
| css/styles.css | All styling: hero grid, search form, bento destination grid, package cards, review carousel, dialog |
| js/main.js | Mobile menu, local times, live weather, trip search, package filters, saved trips, enquiry forms, carousel |
| images/hero-alpine-lake.webp | Hero photo, 1920 x 1080 |
| images/destinations/ | 8 destination photos, 720 x 900 |
| images/packages/ | 8 package photos, 800 x 533 |
| images/favicon.svg | Tab icon |
| images/CREDITS.md | Photographer credit for every photo (Pexels) |
| screenshots/ | Your captured screenshots |

## Destinations and packages

| Destination | Package | Nights | From (per person) | Type |
| --- | --- | --- | --- | --- |
| Kyoto, Japan | Japan: Temples & Neon | 10 | £2,450 | Culture |
| Santorini, Greece | Greek Island Hopping | 8 | £1,290 | Beach |
| Banff, Canada | Canadian Rockies Road Trip | 9 | £1,980 | Adventure |
| Marrakech, Morocco | Morocco: Medinas & Sahara | 6 | £890 | Culture |
| Bali, Indonesia | Tailor-made only | | | |
| South Iceland | Iceland Northern Lights | 5 | £1,150 | Adventure |
| Cape Town, South Africa | Tailor-made only | | | |
| Machu Picchu, Peru | Peru & the Inca Trail | 12 | £2,690 | Adventure |
| | Tanzania Safari | 7 | £3,180 | Adventure |
| | Maldives Island Escape | 7 | £2,890 | Beach |

Each package also has its best months to travel, for example the Japan trip is best in Mar–May and Oct–Nov.

## Page structure

| Part | HTML elements |
| --- | --- |
| Header | `<header>`, logo link, menu button (`aria-expanded`, `aria-controls`), `<nav aria-label="Main">` |
| Hero | `<section>` with the only `<h1>`, and a search `<form role="search">` using a `<select>`, `type="month"` and `type="number"` |
| Trust strip | `<section>` with a `<ul>` of key facts |
| Destinations | `<ul>` of `<article>` cards: photo, `<h3>`, country, local time, weather, price, link |
| Packages | Filter `<form>` (radio chips in a `<fieldset>`, selects, `type="range"` with `<output>`), and a `<ul>` of `<article>` cards with prices in `<data value>` |
| Why us | `<ul>` of four features with icons |
| Reviews | `<ul>` carousel of `<figure>`s, each with a star rating, `<blockquote>`, and a `<figcaption>` with the traveller and `<time>` |
| FAQ | `<details>` / `<summary>` |
| Enquiry | A general enquiry `<form>`, and a native `<dialog>` for package enquiries |
| Footer | `<footer>` with a footer `<nav>` |

## Design decisions

### Layout techniques

1. **Hero layers:** the photo, a dark gradient and the content all sit in one grid cell (`grid-area: 1 / 1`), with no absolute positioning.
2. **Search form:**

   | Width | Layout |
   | --- | --- |
   | Phones | One column |
   | Tablets | Two columns, button full width |
   | 960px and up | One row (`1.4fr 1.2fr 0.8fr auto`) |

3. **Destinations bento grid.** The tiles change shape at each breakpoint:

   ```
   Phone / tablet (2 columns)     Desktop, 1100px+ (4 columns)

   +-----------------+            +---------+----+----+
   | Kyoto (wide)    |            | Kyoto   |San-|Banff
   +--------+--------+            | (2x2)   |tor-+----+
   |Santor. | Banff  |            |         |ini |Marr.
   +--------+--------+            +----+----+----+----+
   |Marrak. | Bali   |            |Bali|Ice.|Cape|Machu
   +--------+--------+            +----+----+----+----+
   |Iceland |CapeTown|
   +--------+--------+
   | Machu Picchu    |
   +-----------------+
   ```

   - The tiles get their shapes from classes: `.big` spans 2×2, `.tall` spans 2 rows, `.wide-md` spans 2 columns on small screens.
   - On phones, the small tiles hide their tagline, price and time, so each tile shows only its name, weather and link.

4. **Package cards** use `repeat(auto-fill, minmax(min(100%, 330px), 1fr))`, giving 1 to 3 columns without breakpoints. Each card is a flex column with the price row pushed to the bottom (`margin-top: auto`), so prices line up across a row.
5. **Reviews carousel:** `grid-auto-flow: column` with `scroll-snap-type`. It works by swipe, trackpad or arrow keys (the list can take keyboard focus), and the Previous and Next buttons scroll one card at a time.
6. **Filter chips** are real radio buttons styled with `:has(input:checked)`.

### Responsive design

| Width | What changes |
| --- | --- |
| Under 640px | Menu behind a ☰ button. Search form in one column. Destinations in a 2-column grid with simple tiles. One package per row. |
| 640px | Search form 2 columns. Taller destination tiles. Enquiry form 2 columns. |
| 900px | Full navigation shows; menu button hidden. Trust strip 4 across. Enquiry text beside the form. |
| 960px | Search form in one row |
| 1100px | Destinations become the 4-column bento |

### Visual design

- **Colours:** deep teal (`#0f5c6e`) for buttons and the footer, warm sand background, coral eyebrow labels and a sunny orange accent.
- **Fonts:** Fraunces, a friendly serif, for headings; Manrope for text.
- **Destination tiles:** white text over a dark gradient, so it stays readable on bright photos (strengthened after testing on the Cape Town photo).
- **Package cards:** a type label (Beach, Culture or Adventure), a save heart, fact pills, a list of what's included with ticks, and the price in large serif type.

### Accessibility

- Landmarks (header, nav, main, footer), one `<h1>`, `<h2>` per section and `<h3>` per card.
- A skip link goes to the main content.
- **Mobile menu:** the button reports `aria-expanded`. Escape closes the menu and returns focus to the button, and choosing a link closes it too.
- **Star ratings** have a text label, for example "Rated 4 out of 5", and the stars themselves are hidden from screen readers.
- **Reviews** use `<blockquote>` inside a `<figure>` with a `<figcaption>` naming the traveller.
- **Buttons have unique names for screen readers,** for example "Enquire about Tanzania Safari" and "Save Japan: Temples & Neon".
- **The result count is announced** when filters change ("Showing 2 of 8 trips").
- **The enquiry dialog:**
  - keeps focus inside
  - shows an error under each field
  - moves focus to the first problem, then to the confirmation
  - returns focus to the Enquire button when closed
- **After a search,** focus moves to the packages heading, so screen-reader users hear the results.
- **Weather icons are hidden from screen readers;** the weather is also written as text ("17°C, Clear").
- Every control is at least 44px tall, and focus outlines are visible.
- `prefers-reduced-motion` turns off smooth scrolling and the photo zoom.

### User experience

- **The trip search does three things:**
  - filters the packages by country
  - shows the total for your group ("£3,450 for 3 travellers")
  - tells you whether your chosen month is a good time to go ("Great time to go in January", or "January is outside the best season (May–Sep)")
- **Filters:** trip type, country, length, a **budget slider** and "Saved only". Sort by recommended, price or length.
- **"See trips"** on a destination filters the packages to that country. Destinations without packages offer "Ask an expert" instead.
- **Saved trips:** the heart button is remembered in the browser (`localStorage`).
- **The enquiry dialog** takes the month and travellers from your search and shows an **estimated total** that updates as you type.
- **Live local time** in each destination's own time zone, using `Intl.DateTimeFormat` with `timeZone`.
- **Live weather** for all 8 places in **one request** to Open-Meteo, cached for 15 minutes.

### Honesty in the content

- Two package names were changed to match the photos:
  - The safari photo was taken in **Tanzania** (not Kenya), so the trip is the "Tanzania Safari".
  - The beach photo is from the **Maldives**, so that trip is the "Maldives Island Escape" (Bali is offered as tailor-made).
- No regulatory or protection scheme numbers are shown, because a fictional company should not claim real accreditation.

## Testing checklist

Fill in the result column as you test.

| # | Test | How | Expected | Result |
| --- | --- | --- | --- | --- |
| 1 | Live weather | Open with internet | All 8 destinations show °C and conditions | |
| 2 | Local time | Compare Kyoto with the time in Japan | Matches Japan time | |
| 3 | Offline | Turn off Wi-Fi, reload | Local times still shown; note says weather is unavailable | |
| 4 | Type filter | Click "Beach" | Greek Island Hopping and Maldives | |
| 5 | Combined | "Adventure" + "Up to 7 nights" | Tanzania Safari and Iceland | |
| 6 | Budget | Slide to £1,300 | 3 trips (Greece, Iceland, Morocco) | |
| 7 | No results | Slide to £800 | Message and Clear filters button | |
| 8 | Sort | "Price: low to high" | Morocco first, Tanzania last | |
| 9 | See trips | "See trips" on Machu Picchu | Only Peru & the Inca Trail | |
| 10 | Save | Heart two trips, tick "Saved only", reload | The same two trips are still saved | |
| 11 | Search validation | Travellers 9, Find trips | "Travellers must be a whole number from 1 to 8." | |
| 12 | Search | Iceland, January 2027, 3 travellers | Iceland trip, "£3,450 for 3 travellers", "Great time to go in January" | |
| 13 | Off season | After test 12, clear filters | Greece says January is outside its best season | |
| 14 | Enquiry errors | Enquire, then Send with nothing filled in | Name and email errors; focus on name | |
| 15 | Big group | Travellers 12 | "For groups larger than 8, please call us" | |
| 16 | Enquiry sent | Fill in correctly | Confirmation with reference; focus back to Enquire when closed | |
| 17 | Reviews | Next and Previous buttons, then swipe | Scrolls one review at a time | |
| 18 | Phone | 375px | ☰ menu, stacked search, 2-column destination tiles | |
| 19 | Tablet | 768px | 2-column search and packages | |
| 20 | Desktop | 1280px | 4-column bento, search in one row | |
| 21 | Keyboard | Tab through everything; Escape closes menu and dialog | All reachable, focus visible | |
| 22 | HTML validation | validator.w3.org | No errors | |
| 23 | Lighthouse | DevTools > Lighthouse | Accessibility 95+ | |

## Screenshots to take

1. Desktop hero with the search form
2. Destinations bento with live weather (1280px)
3. Packages with filters
4. A search result showing totals and season notes
5. The enquiry dialog with estimated total
6. Reviews carousel
7. Phone: hero, menu open, destinations
8. Tablet (768px)
9. DevTools Grid overlay on the destination grid
10. Lighthouse and validator results

## Demo script (about 3 minutes)

1. Point out the demo note, then the live local time and weather on the destinations. Explain that it is one request to Open-Meteo.
2. Search for Iceland, January, 3 travellers. Show the total for 3, the season notes and focus moving to the results.
3. Use the type chips, the budget slider and sorting. Heart two trips and show "Saved only".
4. Select "See trips" on Machu Picchu.
5. Open an enquiry: show the error messages, then a successful enquiry with its estimated total.
6. Scroll the reviews with the buttons, then by swiping.
7. Resize to phone: show the ☰ menu, the stacked search and the bento changing from 4 columns to 2. Show the Grid overlay.

## Known limitations

| Limitation | Possible improvement |
| --- | --- |
| Nothing can actually be booked | Connect to a booking system with real availability and payments |
| Prices are fixed examples | Load live prices from flight and hotel providers |
| Reviews are written examples | Collect verified reviews after each trip |
| Saved trips stay in one browser | Save them to a customer account |
| Fonts load from Google Fonts | Serve the font files from the project |
