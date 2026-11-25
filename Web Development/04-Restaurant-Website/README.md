# Restaurant Website

A one-page website for **Saffron & Sage**, a fictional multi-cuisine restaurant serving Italian, Indian, Asian and Mediterranean food. It has a menu, gallery, chef information and a reservation section, built with semantic HTML5, CSS3 Grid and Flexbox, and a small amount of JavaScript.

## How to run

Open `index.html` in any modern browser (Chrome, Edge, Firefox). No server or install is needed. An internet connection loads the web fonts; without it the page uses system fonts.

## Files

| File | Purpose |
| --- | --- |
| index.html | Page structure and content |
| css/styles.css | All styling, including the responsive layouts and a print stylesheet for the menu |
| js/main.js | Mobile menu, cuisine filter, photo viewer, booking form checks, today's opening hours |
| images/ | 11 photos (WebP), favicon and CREDITS.md |
| screenshots/ | Your captured screenshots |

## Page sections

| Section | Content | Main HTML5 elements |
| --- | --- | --- |
| Header | Logo, menu links, "Book a table" button, mobile menu toggle | `<header>`, `<nav>`, `<button aria-expanded>` |
| Hero | Full-width photo, headline, two buttons | `<section>`, the only `<h1>`, `<img>` with a gradient overlay |
| About | Restaurant story and three highlights | `<section>`, `<figure>`, `<ul>` |
| Menu | 4 courses x 4 dishes, one from each cuisine, with dietary tags | `<section>` per course, `<article>` per dish, `<abbr>`, `<data>`, `<dl>` legend |
| Chef | Photo, story, quote, signature dishes | `<article>`, `<figure>`, `<blockquote>` |
| Gallery | 8 photos in a mosaic; each opens in a full-screen viewer | `<ul>`, `<figure>`, `<figcaption>`, `<dialog>` |
| Reservations | Booking form, opening hours, address | `<form>`, `<fieldset>`, `<legend>`, `<table>`, `<caption>`, `<time>`, `<address>` |
| Footer | Name, links, hours, copyright | `<footer>`, `<nav aria-label="Footer">` |

### Why these elements

- Each **course** is a `<section>` with its own heading, and each **dish** is an `<article>`: name (`<h4>`), price, description and tags.
- **Prices** use `<data value="14">$14</data>`, so the number is also machine-readable.
- **Dietary tags** use `<abbr title="Vegetarian">V</abbr>`. The full word shows on hover and is available to screen readers. A `<dl>` legend explains V, VG and GF.
- **Opening hours** are real tabular data, so they use a `<table>` with a `<caption>`, column headers (`scope="col"`) and row headers (`scope="row"`).
- **The booking form** is split into two `<fieldset>`s ("Your booking" and "Your details"), each with a `<legend>`.
- **The photo viewer** uses the built-in `<dialog>` element. It provides the dark backdrop, keeps keyboard focus inside the viewer and closes with Escape, with no extra code.

## The menu

| Course | Italian | Indian | Asian | Mediterranean |
| --- | --- | --- | --- | --- |
| Starters | Burrata & Heirloom Tomatoes $14 | Vegetable Samosas $9 | Chicken Gyoza $12 | Mezze Platter $16 |
| Mains | Wood-Fired Margherita $18 | Butter Chicken $22 | Tonkotsu Ramen $20 | Grilled Lamb Kofta $24 |
| Desserts | Classic Tiramisu $9 | Mango Kulfi $8 | Matcha Cheesecake $10 | Pistachio Baklava $8 |
| Drinks | Espresso $4 | Mango Lassi $6 | Iced Matcha Latte $6 | Mint Lemonade $5 |

## Design decisions

### Layout

- **CSS Grid** for the page's two-column areas (About, Menu, Chef, Reservations) and for the gallery.
- **Flexbox** for the header, buttons, filter chips, form rows, footer and each dish row.
- **Printed-menu dish rows:** name, a dotted line and the price sit in one Flexbox row. The dotted line is an empty element with `flex-grow: 1`, so it stretches to fill whatever space is left.
- **Gallery mosaic:** photos are marked `wide`, `tall` or `big` and span 2 columns, 2 rows or both. The order is chosen so the grid fills completely with no gaps: 4 rows of 4 on desktop, and 2 columns on tablets and phones. The order you see matches the HTML order, so keyboard users and the viewer's "next" button move through the photos in the same order.
- **Hero:** the photo fills the section with `object-fit: cover`. A dark gradient overlay (`::before`) keeps the white text readable over any part of the photo.
- **Chef photo:** `object-position` focuses the frame on the hands and the plate. The chef is a fictional character, so the photo shows hands at work rather than an identifiable face.

### Responsive design

| Width | Layout |
| --- | --- |
| Phone (under 640px) | One column, hamburger menu, form fields stacked, 2-column gallery |
| Tablet (640px and up) | Form fields side by side, larger gallery rows |
| Desktop (960px and up) | Full menu in the header, two-column sections, two-column menu, 4-column gallery |

- Headings scale smoothly with `clamp()`.
- The hero height adapts with `min(88vh, 760px)`.

### Visual design

- **Dark, warm theme:** charcoal background, cream text and gold accents, to feel like an evening restaurant.
- **Fonts:** Playfair Display (an elegant serif) for headings and Inter for body text.
- **Colours** are defined once as CSS custom properties. Text colours meet WCAG AA contrast on the dark background.
- **Cuisine names** are in a soft sage green, and dietary tags in small gold outlined pills, so they are easy to scan without clutter.

### User experience

- **Course links** jump straight to Starters, Mains, Desserts or Drinks.
- **Cuisine filter:** shows only Italian, Indian, Asian or Mediterranean dishes. The change is announced to screen readers ("Showing 4 Indian dishes.").
- **Photo viewer:**
  - Click any gallery photo to open it large.
  - Use the arrow buttons or the left and right arrow keys to move between photos, and Escape or the close button to exit.
  - Focus returns to the photo you opened.
- **Booking form:**
  - Past dates cannot be picked.
  - Choosing a Monday shows "We are closed on Mondays".
  - Choosing a Sunday dinner time shows "We serve lunch only on Sundays".
  - When the form is valid, a confirmation appears, for example "your request for a table for 4 on Friday 16 October at 19:30".
- **Today's opening hours** are highlighted in the table automatically.
- **Printing the page** prints just the menu, in black on white.

### Accessibility

- Landmarks (header, nav, main, footer). One `h1`, then `h2` per section, `h3` per course and `h4` per dish.
- A skip link, and visible focus outlines on every link, button and field.
- The mobile menu button uses `aria-expanded`. Filter chips use `aria-pressed`.
- Every meaningful image has alt text. The hero photo is decorative (`alt=""`), because the headline carries the message.
- Form fields all have labels; hints are linked with `aria-describedby`; messages are announced through `role="status"`.
- Buttons and fields are at least 44px tall.
- `prefers-reduced-motion` turns off smooth scrolling, hover movement and transitions.

### Progressive enhancement

- Without JavaScript:
  - The menu shows as a list.
  - All dishes are visible, and the cuisine filter is hidden.
  - Gallery photos open as plain image files.
  - The form still uses the browser's own validation.

## Images

- 11 photos from Pexels, free to use. Photographers are listed in `images/CREDITS.md`.
- Photos were resized and saved as WebP:
  - The hero was cropped to 16:9 at 1920 x 1080 (233 KB).
  - Gallery photos are 1200px or less on the longest side.
  - All images together are about 760 KB.
- Every image has `width` and `height`. All except the hero use `loading="lazy"`.

## Testing checklist

Fill in the result column as you test.

| # | Test | How | Expected | Result |
| --- | --- | --- | --- | --- |
| 1 | HTML validation | validator.w3.org, upload index.html | No errors | |
| 2 | CSS validation | jigsaw.w3.org/css-validator | No errors | |
| 3 | Lighthouse | DevTools > Lighthouse > Mobile | Accessibility 95+ | |
| 4 | Phone layout | Device toolbar, 375px | One column, Menu button, 2-column gallery | |
| 5 | Tablet layout | 768px | Form fields side by side | |
| 6 | Desktop layout | 1280px | Two-column menu, 4-column gallery | |
| 7 | Mobile menu | Tap Menu, then a link | Opens, then closes after the link | |
| 8 | Course links | Click "Desserts" | Scrolls to Desserts | |
| 9 | Cuisine filter | Click "Indian" | 4 Indian dishes only | |
| 10 | Photo viewer | Click a photo, use arrows, press Escape | Opens large, moves, closes, focus returns | |
| 11 | Past date | Try to pick yesterday | Not allowed | |
| 12 | Monday | Pick a Monday and submit | "We are closed on Mondays" | |
| 13 | Sunday dinner | Pick a Sunday and 19:00, submit | "We serve lunch only on Sundays" | |
| 14 | Valid booking | Fill all required fields | Confirmation message with the date and time | |
| 15 | Today's hours | Look at the hours table | Today's row highlighted | |
| 16 | Print | Ctrl+P | Only the menu, black on white | |
| 17 | Keyboard | Tab through the page | Focus always visible, logical order | |
| 18 | No JavaScript | DevTools > Disable JavaScript, reload | Page usable, all dishes shown | |

## Screenshots to take

1. Desktop, full page (1280px)
2. Tablet (768px)
3. Phone (375px), with the menu closed and open
4. The menu filtered to one cuisine
5. The gallery, and the photo viewer open
6. The booking form showing the Monday message
7. The booking confirmation message
8. The opening hours with today highlighted
9. Print preview of the menu
10. Lighthouse scores
11. W3C validator result

## Demo script (about 3 minutes)

1. Scroll the page from the hero to the footer and name each section.
2. Point out the semantic structure: sections, articles, figure and figcaption, blockquote, table, fieldset, dialog.
3. Show the menu: the course links, the cuisine filter and the dietary tags (hover over V or GF).
4. Open a gallery photo, move with the arrow keys, close with Escape.
5. Try to book a Monday, then a Sunday dinner, then make a valid booking.
6. Resize to phone size and show the layout, the hamburger menu and the gallery changing.
7. Open print preview to show the print-only menu.
8. Mention reduced motion and the screen-reader announcements.

## Known limitations

| Limitation | Possible improvement |
| --- | --- |
| The booking form does not send data | Connect it to a booking system or email service |
| Table availability is not checked | Check real availability from a server before confirming |
| Opening hours and closed days are written into the HTML and JavaScript | Load them from one settings file so they stay in sync |
| No map | Add an embedded map or a "Get directions" link |
| Fonts load from Google Fonts | Serve the font files from the project |
