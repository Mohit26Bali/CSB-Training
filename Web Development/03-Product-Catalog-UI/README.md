# Product Catalog UI

A responsive product catalog for **Green Corner**, a fictional plant shop. It shows 12 products as cards, with CSS3 transitions, hover effects, category filters and sorting.

## How to run

Open `index.html` in any modern browser (Chrome, Edge, Firefox). No server or install is needed. An internet connection loads the web fonts; without it the page uses system fonts.

## Files

| File | Purpose |
| --- | --- |
| index.html | Page structure and the 12 product cards |
| css/styles.css | All styling: CSS Grid catalog, card design, transitions and hover effects |
| js/main.js | Category filter, sorting, wishlist hearts and cart count |
| images/ | 12 product photos (WebP), favicon and CREDITS.md |
| screenshots/ | Your captured screenshots |

## The 12 products

| # | Product | Category | Price | Badge | Pet safe |
| --- | --- | --- | --- | --- | --- |
| 1 | Monstera Deliciosa | Indoor plants | $34.99 | Bestseller | No |
| 2 | Snake Plant | Indoor plants | ~~$24.99~~ $19.99 | Sale | No |
| 3 | Golden Pothos | Indoor plants | $14.99 | | No |
| 4 | Calathea Orbifolia | Indoor plants | $29.99 | New | Yes |
| 5 | Spider Plant | Indoor plants | $12.99 | | Yes |
| 6 | Echeveria | Succulents & cacti | $18.99 | | Yes |
| 7 | Aloe Vera | Succulents & cacti | $11.99 | Out of stock | No |
| 8 | Zebra Haworthia | Succulents & cacti | $9.99 | | Yes |
| 9 | Ceramic Plant Pot (15 cm) | Pots & planters | $22.00 | | |
| 10 | Macrame Hanging Planter | Pots & planters | ~~$19.50~~ $16.50 | Sale | |
| 11 | Brass Watering Can | Care & tools | $27.00 | | |
| 12 | Slow-Release Plant Food | Care & tools | $8.99 | | |

"Pet safe" is shown only on plants listed as non-toxic to cats and dogs (calathea, spider plant, echeveria, haworthia).

## Page structure

| Part | HTML elements |
| --- | --- |
| Skip link | `<a class="skip-link">` to jump straight to the products |
| Header | `<header>`, logo, `<nav aria-label="Primary">`, cart count |
| Intro | `<section>` with the only `<h1>` and a list of shop perks |
| Catalog | `<section>` with an `<h2>`, toolbar, and the products as a `<ul>` of `<li>` items |
| Product card | `<article>` labelled by its `<h3>` name: image, badge, wishlist `<button>`, category, description, tags, rating, price, cart `<button>` |
| Footer | `<footer>` with `<address>` and opening hours |

- The products are a list (`<ul>`), so screen readers announce "list, 12 items".
- Each card is an `<article>` because it makes sense on its own.
- Sale prices use `<del>` (old price) and `<ins>` (new price), with hidden "Was" and "Now" text for screen readers.

## Design decisions

### Layout

- **CSS Grid for the catalog:** `grid-template-columns: repeat(auto-fill, minmax(min(100%, 250px), 1fr))`.
  - This one line gives 1 column on phones, 2 on tablets and 4 on desktop, with no media query per size.
  - `min(100%, 250px)` stops cards overflowing on very narrow screens.
- **Flexbox inside each card:** the card body is a flex column, and the price row uses `margin-top: auto`. Prices and buttons line up at the bottom of every card in a row, even when descriptions have different lengths.
- **Flexbox for the toolbar, header, perks and footer:** all of these wrap neatly on small screens.
- **Images:** every image sits in a square frame (`aspect-ratio: 1 / 1` with `object-fit: cover`), so all cards match even though the photos come from different sources.

### Transitions and hover effects

| Effect | How |
| --- | --- |
| Card lifts on hover | `transform: translateY(-6px)` with a deeper `box-shadow`, 0.35s |
| Image zooms on hover | `transform: scale(1.07)` inside a frame with `overflow: hidden`, 0.6s |
| Wishlist heart appears on hover | fades and slides in with `opacity` and `transform` |
| Heart fills when saved | `fill` changes to terracotta |
| Menu links | underline grows from left to right (animated `background-size`) |
| Filter buttons | colours change smoothly; the selected one is filled green |
| Add to cart | button darkens briefly; the cart count pops (`scale(1.3)`) |
| Buttons | press down slightly when clicked (`:active` scale) |

- A custom easing curve (`cubic-bezier(0.2, 0.7, 0.3, 1)`) makes movements start fast and settle gently.
- **Hover effects only apply on devices that can hover** (`@media (hover: hover)`). On phones the wishlist heart is always visible, so nothing is hidden behind a hover that cannot happen.
- **Keyboard users get a highlight too:** `:focus-within` gives the card a green border and shadow when any button inside it has focus.
- **Reduced motion:** with `prefers-reduced-motion: reduce`, all movement and transitions are turned off.

### Visual design

- **Palette:** deep green `#2f6b4f` (brand), warm cream background, terracotta `#b0502c` for sale prices and saved hearts.
- **Fonts:** Fraunces (a soft serif) for headings, Inter for body text, both from Google Fonts with system fallbacks.
- **Star ratings:** partial stars, such as 4.4, are drawn with one row of star characters and a gradient (`background-clip: text`) that fills up to the rating. No star images are needed.
- **Sold-out product:** the photo is greyed out and the button is disabled and reads "Out of stock".

### Accessibility

- Landmarks (header, nav, main, footer), one `h1`, an `h2` per section, an `h3` per product.
- A skip link goes straight to the products.
- Every product image has descriptive alt text.
- **Buttons name their product for screen readers.**
  - "Add to cart" buttons say "Add Monstera Deliciosa to cart". The product name is visually hidden, so the button still looks short.
  - Wishlist hearts are labelled "Save Monstera Deliciosa to wishlist" and use `aria-pressed` to say whether the item is saved.
- **Ratings are read out as text:** "Rated 4.8 out of 5 from 124 reviews". The star characters are hidden from screen readers.
- **Filter buttons use `aria-pressed`,** so screen readers know which one is selected.
- **Screen readers hear changes as they happen:** the product count ("Showing 3 of 12 products") and cart messages ("Echeveria added to cart. 2 items in cart.") are announced through live regions.
- Buttons and filter chips are at least 44px tall for touch.
- A visible focus outline is shown on every interactive element.
- Text and badge colours meet WCAG AA contrast.

### Progressive enhancement

- Without JavaScript, all 12 products still show and the page works.
- The filter and sort toolbar is hidden when JavaScript is off, because it would not do anything.

### Performance

- Photos are cropped to 600 x 600 and saved as WebP, between 11 KB and 51 KB each (about 310 KB for all 12).
- The first 4 images load straight away. The other 8 use `loading="lazy"`, so they load only when scrolled near.
- Every image has `width` and `height`, so the page does not jump while images load.

## Images

Photos are from Pexels and are free to use. Photographers are listed in `images/CREDITS.md`. The favicon is a hand-drawn SVG leaf.

## Testing checklist

Fill in the result column as you test.

| # | Test | How | Expected | Result |
| --- | --- | --- | --- | --- |
| 1 | HTML validation | validator.w3.org, upload index.html | No errors | |
| 2 | CSS validation | jigsaw.w3.org/css-validator | No errors | |
| 3 | Lighthouse | DevTools > Lighthouse > Mobile | Accessibility 95+ | |
| 4 | Phone layout | DevTools device toolbar, 375px | 1 card per row | |
| 5 | Tablet layout | 768px | 2 cards per row | |
| 6 | Desktop layout | 1280px | 4 cards per row | |
| 7 | Card hover | Move the mouse over a card | Card lifts, image zooms, heart appears | |
| 8 | Filter | Click "Succulents & cacti" | 3 products, "Showing 3 of 12 products" | |
| 9 | Sort | Choose "Price: low to high" | Cheapest first | |
| 10 | Add to cart | Click "Add to cart" twice | Cart count shows 2 and pops | |
| 11 | Sold out | Look at Aloe Vera | Grey photo, disabled "Out of stock" button | |
| 12 | Wishlist | Click a heart | Heart fills and stays visible | |
| 13 | Keyboard | Tab through a card | Focus outline visible, card highlighted | |
| 14 | Reduced motion | Turn on reduced motion in the OS, reload | No lifting or zooming | |
| 15 | No JavaScript | DevTools > Disable JavaScript, reload | All 12 products show, toolbar hidden | |
| 16 | Zoom | Browser zoom 200% | No horizontal scroll, readable | |

## Screenshots to take

1. Desktop, full page (1280px)
2. Tablet (768px)
3. Phone (375px)
4. A card in its hover state (lifted, zoomed, heart visible)
5. The "Succulents & cacti" filter selected
6. Sorted by price, low to high
7. The cart count after adding items
8. The sold-out Aloe Vera card
9. Keyboard focus on a card
10. Lighthouse scores
11. W3C validator result

## Demo script (about 2 minutes)

1. Open the page on desktop and scroll through the catalog.
2. Hover over a few cards: lift, image zoom, heart, button colour.
3. Explain the CSS Grid line that makes the layout responsive without a media query per size.
4. Resize the window, or switch DevTools to phone size, and show 4, 2 and 1 columns.
5. Filter by category and sort by price; point out the product count updating.
6. Add two items to the cart, save one to the wishlist, and show the sold-out card.
7. Use Tab to move through a card and show the focus highlight.
8. Mention reduced motion and the screen-reader labels on the buttons.

## Known limitations

| Limitation | Possible improvement |
| --- | --- |
| The cart only counts items; there is no cart page or checkout | Add a cart drawer that lists items and totals |
| Wishlist and cart reset when the page reloads | Save them with `localStorage` |
| Product data is written in the HTML | Load products from a JSON file or an API |
| No product detail pages | Add a page or modal for each product |
| Web fonts load from Google Fonts, an outside service | Serve the font files from the project |
