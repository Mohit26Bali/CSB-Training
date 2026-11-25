# Corporate Website Redesign - Fernhill College

A redesign of an outdated company website, shown as **two complete sites and a comparison page**:

| Folder | What it is |
| --- | --- |
| `before/` | The old Fernhill College website, built the way many college sites were around 2012: 4 pages |
| `after/` | The redesign: the same content, accessible, responsive and easier to read: 5 pages |
| `index.html` | The case study: a before/after slider, measured results, an audit table and screenshots |

> **Demo:** Fernhill College is fictional. Both sites were built for this project. The photos are stock photos from Pexels, not of a real college. The "before" site is **deliberately** inaccessible, so don't use it as an example to copy.

**Why a college?** In the UK, public-sector bodies, including further-education colleges, must meet accessibility regulations (WCAG 2.1 AA) and publish an accessibility statement. That gives the redesign a real-world reason, so the redesign includes one.

## How to run

Open `index.html` (the case study) in any modern browser. Its buttons open the old site and the redesign. No server or install is needed.

## Files

| File | Purpose |
| --- | --- |
| index.html, css/compare.css, js/compare.js | Case study page and its before/after slider |
| images/compare/ | Screenshots of both sites used on the case study page |
| before/index.html, courses.html, apply.html, contact.html | The old site (XHTML 1.0 Transitional, table layout) |
| before/css/style.css, before/js/scripts.js | Old styles and scripts: image rollovers, auto-slider, pop-up, `alert()` validation |
| before/images/ | Text baked into images: glossy buttons, logo, badges, social icons, visitor counter, CAPTCHA |
| after/index.html | Home: hero with course search, key facts, subjects, how to apply, open evenings, news |
| after/courses.html | 12 courses with search and filters (subject, study mode, level) |
| after/apply.html | Four steps, key dates, entry requirements, funding and questions |
| after/contact.html | Contact form with validation, contact details and reception hours |
| after/accessibility.html | Accessibility statement |
| after/css/styles.css | All styles for the redesign |
| after/js/site.js, courses.js, contact.js | Menu, course filters, contact form |
| after/images/ | Photos (WebP), favicon and `CREDITS.md` |
| screenshots/ | Your captured screenshots |

Both sites are generated from **one shared content file**, so the facts are identical: the same 12 courses, dates, fees, events and FAQs. Only the presentation and the wording changed.

## The "before" site: what an early-2010s site was like

| Era feature | In the old site |
| --- | --- |
| Fixed 960px layout | `<table id="wrapper" width="960">`, with nested tables and no viewport tag |
| "Web 2.0" gloss | Gradient image buttons with rollovers, glossy boxes, text shadows, a linen texture |
| Text in images | Menu, logo, "NEW!" starburst, social icons, Like and +1 buttons, visitor counter |
| Image slider | 3 slides that change every 3 seconds with no pause; 940px photos squashed into 700px |
| Scrolling news | A `<marquee>` ticker |
| Social widgets | "Latest Tweets", a Facebook "Like" box, a Google+ "+1" button, a tag cloud |
| Pop-up | A "Don't miss out!!!" newsletter pop-up 2 seconds after every page load; the X is a `div` |
| Small grey type | 11px Verdana in #999, centred paragraphs, jargon |
| Vague links | "Click here", "Read more...", "Click here for more info" |
| Old-style form | Placeholder text instead of labels, red stars, a CAPTCHA image, `alert()` errors |
| Footer of its time | "© 2012", "Best viewed in Internet Explorer 9 at 1024 x 768", a "Valid XHTML" badge |

## The redesign: design decisions

### HTML5 semantics

- **Language and landmarks:** `lang="en-GB"`, and `header`, `nav` (main, breadcrumb, footer links), `main`, `aside`, `section` and `footer` landmarks, each section labelled by its heading.
- **Headings:** one `h1` per page and a logical order of `h2`/`h3`.
- **Breadcrumbs:** an `ol` in `nav aria-label="Breadcrumb"`, with `aria-current="page"`.
- **Meaningful elements:**
  - `article` for each course card
  - `dl` for the course facts (Level, Study mode, Length, Starts, Campus, Cost)
  - `table` with `th scope` for key dates, entry requirements and opening hours
  - `time datetime` for every date and time
  - `address` for contact details
  - `details`/`summary` for the FAQ
  - `figure`/`blockquote` for the student quote
- **Structured data:** `CollegeOrUniversity` JSON-LD on the home page.

### Visual hierarchy

- **One main action:** "Apply now" is the only gold button in the header. Gold is used only for main actions, so the eye finds them.
- **Home page order:**
  1. Headline, course search and quick links by age or study mode: what people come for
  2. Key facts
  3. Subjects
  4. How to apply
  5. Events
  6. News
  7. Help
- **Type scale:** a fluid scale (`clamp()`) from 15px to 54px, with clear steps between levels.
- **Short text:** paragraphs are limited to about 68 characters a line.
- **Layout:**
  - **CSS Grid** for page layouts and card grids, using `repeat(auto-fit, minmax(min(100%, 20rem), 1fr))`, so they reflow without breakpoints.
  - **Flexbox** for rows that wrap (buttons, chips, the header).
  - Course filters sit beside the results on wide screens and above them on phones.

### Accessibility (WCAG 2.2 AA)

- **Typeface:** **Atkinson Hyperlegible**, designed by the Braille Institute for low-vision readers. Letters that are easy to confuse (I, l, 1; O, 0) look clearly different.
- **Contrast:** every text colour passes AA, most by a wide margin. Body text is 15.2:1, muted text 7.2:1, links 9.2:1 and text on the gold buttons 8.8:1.
- **Focus:** a thick blue-and-white focus ring that shows on any background. Links are always underlined, so they don't rely on colour.
- **Targets:** buttons and links are at least 44px tall.
- **Forms:**
  - visible labels and hints linked with `aria-describedby`
  - optional fields are marked instead of required ones
  - an error summary takes focus and links to each field
  - errors are written next to each field and shown with a thick bar, not just red text
  - `aria-invalid` is set on fields with errors
  - a fixed field clears its error as soon as it's valid
- **Course filters:** results update live, and the count is a `role="status"` message ("Showing 2 courses: Engineering"). Without JavaScript the form still works: it submits and lists every course.
- **Motion:** nothing moves on its own. Smooth scrolling is turned off for readers who ask for reduced motion.
- **Accessibility statement:** a page saying what works, what doesn't, and how to get information in another format.

### Readability and user experience

- **Plain English:** the opening went from a 37-word sentence of jargon to two short sentences that say who the college is for and what they get.
- **Course cards answer the real questions:** how long, when it starts, where, and how much.
- **Links between pages:**
  - subject cards and quick links open the course list already filtered (`courses.html?subject=health`)
  - the home search box submits to the course list (`courses.html?q=electrician`)
  - "Ask about this course" opens the contact form with the course already chosen
  - "Book a place" opens the form with "Booking an open evening place" chosen
- **Contact page:** today's reception hours are highlighted with an "open now" message, so visitors know whether calling will work.

## How the improvement was measured

| Measure | Tool | Before | After |
| --- | --- | --- | --- |
| Accessibility failures (WCAG 2.2 AA plus best practice) | axe-core 4.10 in headless Edge, every page | 410 elements, 10–12 failed rules per page | **0** on all 5 pages and on the case study page |
| Main text size (weighted by amount of text) | Browser measurement, home page | 11px | 18px |
| Text smaller than 14px | as above | 94% | 0% |
| Smallest text | as above | 8px | 15.2px |
| Body text contrast | WCAG contrast formula | 2.85:1 | 15.2:1 |
| Sideways scroll at 496px wide | `scrollWidth` | 464px | 0px |
| Images with no alt text (home) | DOM count | 26 | 0 |
| Headings (home) | DOM count | 0 | 21 (1 `h1`) |
| "Click here" / "Read more" links (home) | DOM count | 10 | 0 |
| Page weight (home, without fonts) | File sizes | 205 KB | 353 KB |

**Notes on these numbers:**
- **Page weight went up.** The new photos are larger and sharper. Most use `loading="lazy"`, and the hero has a smaller phone version (`srcset`). The case study page says this openly.
- **The old site's axe count varies slightly** (410–411) between runs, depending on which slide the auto-slider is showing.
- **Tools:** axe-core finds many issues automatically, but not all. The redesign was also checked by hand with the keyboard and at 320px wide.

## Testing checklist

Fill in the result column as you test.

| # | Test | How | Expected | Result |
| --- | --- | --- | --- | --- |
| 1 | Case study | Open `index.html`, drag the slider, then use the arrow keys | Old design on the left, new on the right | |
| 2 | Old site pop-up | Open `before/index.html`, wait 2 seconds, press Tab and Escape | Pop-up covers the page; can't be closed with the keyboard | |
| 3 | Old site on a phone | DevTools, 375px | Page scrolls sideways or shrinks; tiny text | |
| 4 | New site on a phone | 320px and 375px | One column, menu button, no sideways scroll | |
| 5 | Menu | 375px: Menu, then Escape | Opens; Escape closes and focus returns | |
| 6 | Subject link | Home: click "Health and Social Care" | Courses page shows 2 health courses | |
| 7 | Search | Home: search `electrician` | 1 course: Electrical Installation Apprenticeship | |
| 8 | Filters | Courses: Part-time, then tick Level 1 | 3 courses, then 2; count message updates | |
| 9 | No results | Search `zzz` | "No courses found" with a link to an adviser | |
| 10 | Clear | Clear filters | All 12 courses; focus in the search box | |
| 11 | Ask about a course | "Ask about this course" on Barista Skills | Contact form with Barista Skills and the topic chosen | |
| 12 | Form errors | Submit the empty contact form | Error summary (4 links) gets focus; inline errors | |
| 13 | Format errors | Email `sam@`, phone `123`, message `Hi` | Three specific error messages | |
| 14 | Success | Fill in correctly and send | Thank-you message with your topic and email | |
| 15 | Hours | Contact page | Today's row highlighted; open or closed message | |
| 16 | Keyboard | Tab through every page | Skip link first; focus always visible | |
| 17 | Zoom | 400% zoom on the home page | No sideways scrolling; nothing cut off | |
| 18 | axe | axe DevTools extension on each page | 0 issues on the new site; many on the old | |
| 19 | Lighthouse | DevTools > Lighthouse on both home pages | Large accessibility score increase | |
| 20 | Validation | validator.w3.org on the new pages | No errors | |

**Automated checks I ran in headless Edge:**

| Check | Result |
| --- | --- |
| axe-core on all pages | old site 410 failing elements; new site and case study page 0 |
| Courses page | 12/12 pass |
| Filters arriving in the address (`?subject=health`, `?q=electrician`) | 5/5 pass |
| Contact page | 14/14 pass |
| Course chosen from the address (`?course=barista`) | 2/2 pass |
| Every new page at 320px | no sideways scroll, checked visually |

## Screenshots to take

1. The case study page: slider in the middle, then dragged left and right
2. Old home page, and the old home page with the pop-up
3. New home page, desktop and phone
4. Old and new courses pages
5. Old contact form, and the new one showing the error summary
6. New courses page with filters applied
7. Accessibility statement
8. axe DevTools results on both home pages (many issues against 0)
9. Lighthouse on both home pages
10. DevTools Grid overlay on `.hero-grid`, `.subject-grid` and `.course-layout`

## Demo script (about 4 minutes)

1. **Case study page:** drag the slider and explain the idea: same content, new presentation, measured results.
2. **The old site (2012):** point out the image buttons, the squashed slider and the marquee. Wait for the pop-up and try to close it with the keyboard. Resize to a phone width.
3. **Run axe:** run the axe extension (or Lighthouse) on the old home page and show the issue count.
4. **The new site:** show the headline, course search and gold "Apply now". Search for a course, filter the list, and open "Ask about this course" to show the form already filled in.
5. **The contact form:** submit it empty to show the error summary, then fix the errors.
6. **Run axe again:** 0 issues. Tab through the page to show the focus ring.
7. **Phone width:** resize to a phone and open the menu.
8. **Finish:** show the audit table, where each problem is linked to its WCAG rule and fix.

## Known limitations

| Limitation | Possible improvement |
| --- | --- |
| Only 12 example courses | Load the full course list from the college's database |
| Forms don't send anything | Connect to the college's enquiry and application systems |
| Automated testing can't find every accessibility problem | Test with real screen-reader users and people with disabilities |
| Page weight increased | Serve AVIF images and self-host a subset of the font |
| No course detail pages | Add one page per course with modules, assessment and progression |
