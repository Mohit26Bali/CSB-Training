# E-Commerce Product Page - Green Corner

A small shop for **Green Corner**, the plant shop from the Product Catalog UI project: a **home page** with a hero section and a catalogue of the **same 12 products as the Product Catalog UI project** (8 plants and 4 accessories), and **12 product pages**, each with a gallery of 4 photos (48 product images). Each page has a gallery with full-screen view, size and pot options that change the price, a quantity stepper, **Add to basket** and **Buy now** buttons, a basket drawer, a wishlist, a delivery estimate, tabs for details, reviews and related products.

> **Demo:** Green Corner is a fictional shop, so nothing can be bought. Reviews are written examples and are labelled as such on the page. The plant care facts are real.

## How to run

Open `index.html` (the home page) in any modern browser. No server or install is needed.

```
index.html (home)
 ├─ hero: "Shop all plants" / "Pet-safe plants"
 ├─ Shop by need tiles ──────────┐  filter the catalogue
 └─ Shop the collection (12) ────┼─> monstera.html ... plant-food.html (product pages)
                                  │      ├─ "You may also like" ─> other product pages
                                  │      └─ logo, Home, Shop all, breadcrumb ─> back to index.html
```

- **All links stay inside this project:** the logo, "Home", "Shop all" and the breadcrumb always lead back to the home page. The breadcrumb's category link opens the home page already filtered, for example `index.html?filter=succulents`.
- **One basket for the whole shop:** add a plant on its page and the count shows on every page, including the home page.

## Files

| File | Purpose |
| --- | --- |
| index.html | Home page: hero, trust strip, Shop by need, the catalogue of 12 products with filters and sorting, Why Green Corner |
| monstera.html, snake-plant.html, golden-pothos.html, calathea.html, spider-plant.html, echeveria.html, aloe-vera.html, zebra-haworthia.html | The 8 plant pages |
| ceramic-pot.html, macrame-planter.html, watering-can.html, plant-food.html | The 4 accessory pages |
| css/styles.css | All styling: gallery, buy box, option cards, swatches, tabs/accordion, reviews, basket drawer, viewer |
| js/basket.js | Shared by every page: the basket drawer, saved data and messages |
| js/home.js | Home page: catalogue filters, sorting and Shop by need links |
| js/main.js | Product pages: gallery, viewer, options and price, quantity, wishlist, delivery, tabs, reviews, sticky bar |
| images/plants/ | 48 photos (4 per product), 1000 × 1000 WebP |
| images/hero-living-room.webp | Home page hero photo |
| images/favicon.svg | Tab icon (same as the catalogue) |
| images/CREDITS.md | Photographer credit for every photo (Pexels) |
| screenshots/ | Your captured screenshots |

## The plants

| Plant | Small / Medium / Large | Pets | Care |
| --- | --- | --- | --- |
| Monstera Deliciosa | £21.99 / £34.99 / £55.99 (large sold out) | Toxic | Easy |
| Snake Plant | £11.99 / £19.99 / £31.99 | Toxic | Easy |
| Golden Pothos | £8.99 / £14.99 / £23.99 | Toxic | Easy |
| Calathea Orbifolia | £18.99 / £29.99 / £47.99 | **Pet safe** | Needs some attention |
| Spider Plant | £7.99 / £12.99 / £20.99 (large sold out) | **Pet safe** | Easy |
| Echeveria | £11.99 / £18.99 / £29.99 | **Pet safe** | Easy |
| Aloe Vera | £6.99 / £11.99 / £18.99 (medium sold out) | Toxic | Easy |
| Zebra Haworthia | £5.99 / £9.99 / £15.99 | **Pet safe** | Easy |

## The accessories

| Accessory | Options | Price (matches the catalogue) |
| --- | --- | --- |
| Ceramic Plant Pot (15 cm) | Colour: Chalk white, Sage green, Terracotta blush (sold out) | £22.00 |
| Macramé Hanging Planter | Length: 80 cm, 110 cm, or a twin pack | £16.50 / £19.50 / £29.00 |
| Brass Watering Can | Size: 1 litre, 1.8 litres | £27.00 / £36.00 |
| Slow-Release Plant Food | Pack: 200 g, 3 × 200 g | £8.99 / £22.99 |

Accessory pages use the same template as the plants, with the plant-only parts swapped:

| Part | Plant page | Accessory page |
| --- | --- | --- |
| Under the name | Latin name and nickname | Short description |
| Options | Size, plus a pot choice | One choice: colour, length, size or pack (no pot) |
| Second tab | Care guide | Details (size, material, how to use) |
| Third tab | Sizes table | Options table |
| Guarantee | 30-day happy plant guarantee | Return unused items within 30 days |
| You may also like | 3 plants and 1 accessory | The other 3 accessories and 1 plant |

Stock messages use the right word, for example "Only 5 left in this colour".

- **Medium prices** match the Product Catalog UI project.
- **Pots:** grower's pot (included), terracotta (+£6) or stone ceramic (+£12).
- **Stock** differs per size, so some show "Only 3 left" and some are sold out.

## Home page

| Part | What it has |
| --- | --- |
| Hero | Photo with a dark gradient, the page's `<h1>` "Plants that make a house a home", and two buttons. Photo and text sit in one grid cell (`grid-area: 1 / 1`). |
| Trust strip | Next-day delivery, 30-day guarantee, free delivery over £40 (`auto-fit` grid) |
| Shop by need | Four photo tiles (Pet safe, Low light, Succulents, Pots & care) that filter the catalogue and move focus to it |
| Shop the collection | Filter chips (Everything, Indoor plants, Succulents, Pet safe, Low light, Pots & care), sort (featured, price, rating), and 12 cards. Each card shows badges (Pet safe, Low light, Low stock), the Latin name, rating and "From" price. A second photo fades in on hover. 2 columns on phones, `auto-fill` on wider screens. |
| Why Green Corner | Three short reasons |

The chosen filter is kept in the address (`?filter=pet-safe`), so it can be bookmarked or linked to. Some browsers block this for pages opened straight from a file; the filter still works, only the address doesn't change.

## Product page structure

| Part | HTML elements |
| --- | --- |
| Header | `<header>`, logo, `<nav aria-label="Primary">`, basket button with a count (screen readers hear "Basket, 2 items") |
| Breadcrumb | `<nav aria-label="Breadcrumb">` with an `<ol>` and `aria-current="page"` |
| Gallery | `<section>`: a list of slides (`<ul>`), thumbnail buttons with `aria-current`, previous/next and full-screen buttons |
| Buy box | `<h1>`, Latin name in `<i lang="la">`, price in `<data value>`, a `<form>` with two `<fieldset>`s (size cards and pot swatches are real radio buttons), a number input with − / + buttons |
| Details | Four `<section>`s (Description, Care guide, Sizes, Delivery & returns). Care uses a `<dl>` and a `<meter>` for difficulty; Sizes uses a `<table>`. |
| Reviews | Summary with rating-bar buttons (`aria-pressed`), and a `<ul>` of `<article>` reviews with `<time>` |
| Related | `<ul>` of 4 cards linking to the other plant pages |
| Basket | Native `<dialog>` drawer: items, a free-delivery `<progress>` bar, subtotal in `<output>` |
| Viewer | Native `<dialog>` for full-screen images |
| Structured data | `<script type="application/ld+json">` with schema.org `Product` and price range, so search engines can read the product. Example reviews are not included in it. |

## Design decisions

### Layout

**Product area (Grid):**

```
Phone / tablet (one column)          Desktop, 900px+ (two columns)

+---------------------------+        +-----+----------------+  +------------------+
|        main image         |        |thumb|                |  | Indoor plants    |
|        (swipe)            |        |thumb|   main image   |  | Monstera ...     |
+---------------------------+        |thumb|                |  | £34.99           |
| thumb thumb thumb thumb   |        |thumb|                |  | [size cards]     |
+---------------------------+        +-----+----------------+  | [pot swatches]   |
| buy box                   |                                    | [qty] [Add][Buy] |
+---------------------------+                                    +------------------+
                                                                  sticky while you scroll
```

- **Grid areas move the thumbnails:** under the image on phones, beside it on desktop.
- **Tablets (600–899px):** the gallery is capped at 32rem and centred, so the price and buttons aren't pushed far down.
- **The buy box is `position: sticky`** on desktop, so the price and Add button stay in view while you look at the photos.

**Other layouts:**

| Part | Technique |
| --- | --- |
| Slides | `grid-auto-flow: column` with `scroll-snap-type`: swipe on phones, buttons and thumbnails everywhere |
| Size cards | 3-column grid; the chosen card is styled with `:has(input:checked)` |
| Pot swatches | Flexbox pills with a colour dot from a CSS custom property (`--swatch`) |
| Action buttons | Flexbox with `flex: 1 1 12rem`, so the buttons share the row and wrap on small screens |
| Reviews | Summary beside the list from 900px |
| Related products | 2 columns on phones, 4 on desktop |
| Basket | A `<dialog>` styled as a side drawer that slides in |

### Tabs on wide screens, accordion on small screens

The four detail sections are written once in the HTML:

| Situation | How the details show |
| --- | --- |
| **900px and wider** | JavaScript turns them into **ARIA tabs** (`role="tablist"`, `tab`, `tabpanel`). Arrow keys, Home and End move between tabs. |
| **Narrower** | The same sections become an **accordion**. Each heading holds a button with `aria-expanded`. |
| **Without JavaScript** | All four sections simply show, one after another |

The page switches between tabs and accordion when the window is resized (`matchMedia`).

### Visual design

- **Same look as the catalogue:** forest green, warm cream background, terracotta labels, Fraunces headings with Inter text.
- **Prices** use the heading font, large, with "including VAT".
- **Sold-out sizes** have a dashed border, "Sold out" in red, and are disabled. You can't choose them.
- **Care badges:** green "Pet safe" or orange "Toxic to pets", always written in words.
- **Photos:** all square crops (1000 × 1000), so the gallery and cards line up.

### Accessibility

- One `<h1>` (the plant name), `<h2>` sections and `<h3>` cards. A skip link goes to the product.
- **Option cards and swatches are real radio buttons.** They work with the keyboard and are grouped by `<fieldset>`/`<legend>`. The pot's name is written next to its colour.
- **Price changes are announced,** for example "Small, Stone ceramic: £33.99", through a `role="status"` region.
- **Quantity:** − / + buttons with hidden labels ("Increase quantity"), disabled at the limits. Typed values are checked, with clear messages.
- **Thumbnails** are buttons named "Show image 2: In bright light by a window". The selected one has `aria-current`.
- **The full-screen viewer:** arrow keys, Escape to close, a caption "2 of 4: …", and focus returns to the button that opened it.
- **The basket drawer:**
  - its title receives focus, and the message ("Added 2 × Monstera…") is read out
  - after "Remove", focus moves to the next item
  - closing it returns focus to the button that opened it
- **Star ratings** have text ("Rated 4 out of 5"). Rating bars are buttons with `aria-pressed`. The review count is announced when filtering.
- All controls are at least 44px tall, and focus is always visible. `prefers-reduced-motion` turns off zoom, sliding and smooth scrolling.

### User experience

- **Price follows your choices:** size price + pot price. The total updates in the buy box and the sticky bar.
- **Stock-aware quantity:**
  - you can't choose more than are left
  - the basket counts too: with 2 of the last 4 Monstera mediums in your basket, you can only add 2 more
  - when you have them all: "All remaining stock of this size is in your basket"
- **The same plant, size and pot are merged into one basket line.**
- **The basket is shared by all 8 pages and remembered** (`localStorage`). It shows a **free-delivery progress bar** ("Spend £5.01 more for free delivery") and subtotal.
- **"Buy now"** adds the item and moves straight to the Checkout button. Checkout explains that this is a demo shop.
- **Delivery estimate:** weekday orders before 2 pm are delivered the next working day, for example "Order within 3 h 8 min for delivery on Monday 12 October". It updates every minute and skips weekends.
- **Gallery:**
  - hover zoom that follows the mouse (desktop)
  - swipe (phones)
  - arrow keys when the gallery is focused
  - full-screen viewer
- **Sticky add-to-basket bar** on phones and tablets: it appears once the main buttons scroll out of view.
- **Reviews:** sort by newest, highest or lowest, select a star bar to filter, and mark reviews "Helpful".
- **Wishlist:** a heart button, remembered, with a note saying how many plants are saved.

### Honesty in the content

- **The care facts are real,** including which plants are toxic to cats and dogs.
- **The reviews are examples written for this demo,** and the page says so. The rating summary is calculated from those reviews only, with no invented totals.
- **The structured data has no ratings,** because publishing made-up ratings to search engines would be misleading.
- **Gallery photos show the same species, not one individual plant.** They come from different photographers (credited in `images/CREDITS.md`). One photo was swapped during checking because two searches had returned the same picture.

### How the pages were produced

- The 8 pages share one template and one set of plant data (prices, stock, care, reviews), so every page has the same structure.
- Each page is plain HTML. Its product data (sizes, prices, stock) is also stored in a small JSON block that `main.js` reads.

## Testing checklist

Fill in the result column as you test.

| # | Test | How | Expected | Result |
| --- | --- | --- | --- | --- |
| 0a | Home page | Open `index.html` | Hero, trust strip, Shop by need, 12 product cards | |
| 0e | Accessories | "Pots & care" chip; open Ceramic Plant Pot; choose Sage green | 4 products; no pot choice; "Only 5 left in this colour" | |
| 0b | Home filters | Pet safe; then Low light | 4 plants; then 2 (Snake Plant, Golden Pothos) | |
| 0c | Shop by need | Click "Succulents" tile | Catalogue shows 3 succulents and focus moves to it | |
| 0d | Links | Open a plant, then the logo, Home and Shop all | Always back to the home page | |
| 1 | Default | Open `monstera.html` | Medium chosen, £34.99, "Only 4 left in this size" | |
| 2 | Sold out | Try to choose Large | Disabled, shows "Sold out" | |
| 3 | Price | Choose Small, then Stone ceramic | £21.99, then £33.99 | |
| 4 | Stepper | Press + many times | Stops at 4; + disabled | |
| 5 | Bad quantity | Type 9, Add to basket | "Only 4 more can be added in this size." | |
| 6 | Add | Quantity 2, Add to basket | Drawer opens; "Added 2 × Monstera…"; badge shows 2 | |
| 7 | Free delivery | After test 6 | "You have free delivery!" (subtotal £69.98) | |
| 8 | Stock in basket | Add 2 more of the same | One line with quantity 4; then "All remaining stock…" | |
| 9 | Remove | Remove an item | Item gone; focus moves to the next item | |
| 10 | Buy now | Choose Small, Buy now | Drawer opens with focus on Checkout | |
| 11 | Shared basket | Open Snake Plant from "You may also like" | Same basket count | |
| 12 | Wishlist | Heart, then reload | Still saved | |
| 13 | Gallery | Thumbnails, arrows, swipe on a phone | Image and "2 / 4" counter change | |
| 14 | Hover zoom | Move the mouse over the image (desktop) | Zooms where the mouse is | |
| 15 | Full screen | Full-screen button, arrow keys, Escape | Viewer works; focus returns | |
| 16 | Delivery | Look at the delivery line before and after 2 pm | Next working day; skips weekends | |
| 17 | Tabs | Desktop: click tabs, then use arrow keys | One panel shows at a time | |
| 18 | Accordion | Phone: open and close sections | Buttons expand and collapse | |
| 19 | Reviews | Sort "Lowest rated"; select "5 star" | Order changes; 3 of 4 shown | |
| 20 | Sticky bar | Phone: scroll past the buttons | Bar appears with price and Add | |
| 21 | Other plant | Calathea page | "Pet safe" badge; care guide says non-toxic | |
| 22 | Phone | 375px | One column; size cards fit; buttons wrap | |
| 23 | Keyboard | Tab through the whole page | Everything reachable, focus visible | |
| 24 | HTML validation | validator.w3.org | No errors | |
| 25 | Lighthouse | DevTools > Lighthouse | Accessibility 95+ | |

## Screenshots to take

1. Home page hero and catalogue (desktop and phone)
1. Desktop (1280px): product gallery and buy box
2. Different size and pot chosen (price updated)
3. Basket drawer after adding
4. Full-screen viewer
5. Tabs: Care guide open
6. Reviews filtered to 5 stars
7. Related products
8. Phone (375px): gallery, buy box, accordion
9. Phone: sticky add-to-basket bar
10. Tablet (768px)
11. A pet-safe plant (Calathea)
12. DevTools Grid overlay on `.product` and `.gallery`
13. Lighthouse and validator results

## Demo script (about 3 minutes)

1. Start on the home page: hero, Shop by need, filter to Pet safe, then open a plant.
1. Show the gallery: thumbnails, arrows, hover zoom, then full screen with the arrow keys.
2. Change the size and pot and watch the price change. Show that Large is sold out.
3. Use the quantity stepper up to the stock limit, then try typing 9 to show the error.
4. Add to basket: the drawer opens with the message, the free-delivery bar and the subtotal. Remove an item.
5. Open another plant from "You may also like": the basket is still there.
6. Show the delivery estimate and explain the 2 pm cut-off.
7. Resize to phone: the gallery swipes, tabs become an accordion, and the sticky bar appears when scrolling.
8. Show the reviews filter and sort, and the structured data in the page source.

## Known limitations

| Limitation | Possible improvement |
| --- | --- |
| No real checkout or payment | Connect to a payment provider and an order system |
| Stock is fixed in the page | Load live stock from the shop's database |
| Reviews are examples | Collect reviews from real customers after delivery |
| Gallery photos show the species, not the exact plant | Photograph the shop's own plants from several angles |
| Fonts load from Google Fonts | Serve the font files from the project |
