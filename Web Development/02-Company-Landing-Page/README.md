# Company Landing Page

A responsive one-page website for **Nimbus Software**, a fictional software company. It is built with semantic HTML5, CSS3 Flexbox and a small amount of JavaScript.

## How to run

Open `index.html` in any modern browser (Chrome, Edge, Firefox). No server or install is needed.

## Files

| File | Purpose |
| --- | --- |
| index.html | Page structure and content |
| css/styles.css | All styling, mobile first, Flexbox layout |
| js/main.js | Mobile menu, header shadow, active menu link, contact form message |
| images/ | Hero photo, two spare photos and CREDITS.md |
| screenshots/ | Your captured screenshots |

## Page sections

| Section | HTML elements |
| --- | --- |
| Skip link | `<a class="skip-link">`, hidden until focused with Tab |
| Header | `<header>`, `<nav aria-label="Primary">`, menu `<ul>`, toggle `<button>` |
| Hero | `<section>` with the only `<h1>`, two call-to-action links, hero image, stats `<ul>` |
| Trusted by | `<section>` with a `<ul>` of client names |
| Services | 6 x `<article>` cards with inline SVG icons |
| Testimonials | 3 x `<figure>` with a rating, `<blockquote>` and `<figcaption>` |
| Pricing | 3 x `<article>` plans, the middle one highlighted |
| Call to action | `<section>` band with two links |
| Contact | `<address>`, `<form>` with labels and HTML5 validation |
| Footer | `<footer>` with two `<nav>` link lists, contact details and social links |

## Design decisions

### Layout

- Flexbox only, as the brief asks. It is used for the header, hero, stats, card rows, pricing, form rows, contact split and footer.
- Cards use `flex-wrap` with `flex-basis`: 1 per row on phones, 2 on tablets, 3 on desktop.
- Pricing cards are flex columns with `flex-grow` on the feature list, so all three buttons line up at the bottom.
- Testimonial quotes use `flex-grow`, so the names line up at the bottom of each card.

### Responsive design

- Mobile first: the base CSS is for phones, and `min-width` media queries add the larger layouts.
- Breakpoints: 600px (tablet) and 900px (desktop).
- `clamp()` scales headings smoothly between screen sizes.
- The container is capped at 1120px so lines stay readable on wide screens.

### Visual design

- One brand colour (indigo `#3b5bdb`) with a light tint for backgrounds, and grey text for secondary content.
- All colours are defined once as CSS custom properties in `:root`.
- Alternating section backgrounds separate the sections without heavy borders.
- The recommended plan has a badge and a coloured border, so the eye goes there first.

### Accessibility

- Landmarks: header, nav, main, footer. Each section is labelled by its heading with `aria-labelledby`.
- One `h1`, then `h2` per section and `h3` per card, with no skipped levels.
- A skip link lets keyboard users jump straight to the content.
- A visible focus outline (`:focus-visible`) is shown on every link, button and field.
- Buttons and inputs are at least 44px tall for touch.
- The menu button uses `aria-expanded` and `aria-controls`. Escape closes the menu.
- Every form field has a `<label>`. Required fields use `required`, the hint is linked with `aria-describedby`, and the success message is announced through `role="status"`.
- Decorative icons, avatar initials and the large quote mark are hidden from screen readers with `aria-hidden` or CSS-only content.
- `prefers-reduced-motion` turns off smooth scrolling and transitions for users who ask for it.
- Text colours meet WCAG AA contrast against their backgrounds.

### Progressive enhancement

- Without JavaScript, the menu is shown as a normal list and the page still works.
- A one-line script in `<head>` adds a `js` class, which hides the mobile menu behind the toggle only when JavaScript is running.

### Contact form

- Front end only. The browser checks the fields (empty fields, email format, message at least 20 characters).
- When the form is valid, JavaScript shows a thank-you message and clears it. Nothing is sent, because there is no backend in the brief.

## Visual enhancements

These were added after the first version to make the page look more finished. All of them use plain HTML, CSS and Flexbox, with a small amount of JavaScript for the last one.

| # | Enhancement | How it works |
| --- | --- | --- |
| 1 | SVG service icons | Six simple line icons drawn as inline `<svg>`. They use `currentColor`, so they follow the brand colour, and `aria-hidden` because the heading already says what each service is. |
| 2 | Pricing badge on the card edge | The "Most popular" badge is `position: absolute` and moved up by half its height, so all three plan headings line up. On desktop the Growth card is raised 12px with a stronger shadow. |
| 3 | Call-to-action band | A full-width indigo gradient section before Contact breaks up the light sections. It uses white buttons and a white focus outline so it stays readable. |
| 4 | Layered hero | The stats card overlaps the bottom of the photo with a negative top margin and a stronger shadow, so the image and stats read as one group. |
| 5 | Card hover effect | Cards lift 4px with a deeper shadow on hover. With `prefers-reduced-motion: reduce` the movement and transitions are switched off. |
| 6 | Testimonial details | A large decorative quote mark is added with `::before` (not read by screen readers). A 5-star rating has a hidden text label: "Rated 5 out of 5". |
| 7 | "Trusted by" strip | A row of client names under the hero, laid out with `flex-wrap`. The names are fictional, matching the testimonials. |
| 8 | Full footer | Brand and social links, Services links, Company links, and contact details with small icons. The columns stack on phones, sit two per row on tablets and four across on desktop. |
| 9 | Inter web font | Loaded from Google Fonts with `preconnect` and `display=swap`, so text shows straight away in a fallback font. The system fonts stay as the fallback if Google Fonts cannot load. |
| 10 | Smarter header | A shadow appears once the page is scrolled. The menu link for the section on screen is underlined using `aria-current`, found with `IntersectionObserver`. |

## Images

| Image | Where | Notes |
| --- | --- | --- |
| images/logo.png | Header logo | Made from the original logo: cropped, transparent background, 160 x 116, 18 KB |
| images/logo-white.png | Footer logo | White version for the dark footer, 11 KB |
| images/favicon.png | Browser tab icon | Square 64 x 64 version, 5 KB |
| images/Cloud Code Upload Icon.png | Not used on the page | Original logo artwork (1254 x 1254, 1 MB), kept as the source file |
| images/hero-whiteboard.jpg | Hero, with the stats card overlapping it | Pexels photo, credits in images/CREDITS.md |
| images/hero-team-meeting.jpg | Not used | Spare hero option |
| images/hero-developer-desk.jpg | Not used | Spare hero option |

- The hero image has descriptive `alt` text, because it shows the kind of work the company does.
- `width` and `height` are set, so the browser reserves the space and the page does not jump while the image loads.
- It is not lazy-loaded, because it is near the top of the page. `fetchpriority="high"` asks the browser to load it early.
- `aspect-ratio: 3 / 2` with `object-fit: cover` keeps the shape the same at every screen size.
- Testimonial avatars stay as initials. Using stock photos of real people next to invented names and quotes would misrepresent them.
- Service, footer and social icons are inline SVG, not image files.
- The logo images have `alt=""` because the company name is written next to them, so a screen reader would otherwise read it twice.
- The original logo had a white background and was 1 MB. It was cropped, made transparent and resized, so it loads quickly and also works on the dark footer.

## Testing checklist

Fill in the result column as you test.

| # | Test | How | Expected | Result |
| --- | --- | --- | --- | --- |
| 1 | HTML validation | Upload index.html at validator.w3.org | No errors | |
| 2 | CSS validation | Upload styles.css at jigsaw.w3.org/css-validator | No errors | |
| 3 | Lighthouse | Chrome DevTools > Lighthouse > Mobile | Accessibility 95+ | |
| 4 | Phone layout | DevTools device toolbar, 375px | One column, Menu button | |
| 5 | Tablet layout | 768px | Cards 2 per row, pricing stacked | |
| 6 | Desktop layout | 1280px | Cards 3 per row, full menu | |
| 7 | Mobile menu | Tap Menu, then a link | Opens, closes after link | |
| 8 | Keyboard | Tab from the top of the page | Skip link appears first, focus always visible | |
| 9 | Escape key | Open menu, press Escape | Menu closes, focus returns to button | |
| 10 | Empty form | Click Send message with empty fields | Browser shows required messages | |
| 11 | Bad email | Type "abc" in email and submit | Email format message | |
| 12 | Valid form | Fill all required fields | Thank-you message, form clears | |
| 13 | No JavaScript | DevTools > Settings > Disable JavaScript, reload | Menu visible as list, page usable | |
| 14 | Zoom | Browser zoom 200% | No horizontal scroll, text readable | |
| 15 | Header shadow | Scroll down a little | Shadow appears under the header | |
| 16 | Active link | Scroll to each section | Matching menu link is underlined | |
| 17 | Card hover | Move the mouse over a card | Card lifts with a deeper shadow | |
| 18 | Reduced motion | Turn on reduced motion in the OS, reload | Cards do not move on hover, no smooth scroll | |

## Screenshots to take

1. Desktop, full page (1280px)
2. Tablet, full page (768px)
3. Phone, top of page with the menu closed (375px)
4. Phone with the menu open
5. Keyboard focus on a button (shows the focus outline)
6. The skip link visible after pressing Tab
7. The form showing a required-field message
8. The form showing the thank-you message
9. Lighthouse report scores
10. W3C validator result
11. Pricing section showing the raised "Most popular" card
12. Header with the active menu link underlined after scrolling
13. A card in its hover state

Tip: in DevTools device toolbar, the menu (three dots) has "Capture full size screenshot".

## Demo script (about 2 minutes)

1. Open the page on desktop and scroll through the five sections.
2. Point out the semantic structure: header, nav, main, sections, articles, figures, footer.
3. Open DevTools, switch to phone size and show the layout changing at 900px and 600px.
4. Open and close the mobile menu, including with Escape.
5. Use only the keyboard: Tab to the skip link, through the menu, and into the form.
6. Submit the form empty, then with a bad email, then correctly.
7. Show the Lighthouse accessibility score.
8. Explain one Flexbox decision: the pricing buttons stay aligned because of `flex-grow`.

## Known limitations

| Limitation | Possible improvement |
| --- | --- |
| The form does not send data | Connect it to a backend or a form service |
| The hero photo is a 210 KB JPEG at 1880px wide | Add a smaller WebP version with `srcset` so phones download less |
| All content is in the HTML | Move content to a CMS if it changes often |
| One language only | Add translations with `lang` attributes |
| The Inter font is loaded from Google Fonts, an outside service | Download the font files and serve them from the project |
| Social links point to `#` | Replace with the company's real profile links |
