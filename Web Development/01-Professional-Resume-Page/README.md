# Professional Resume Page

A single-page professional resume built with semantic HTML5 and CSS3. It covers:

- the header
- profile summary
- projects
- education
- skills
- certifications
- contact information

The Projects section lists real work, linked to the working projects. The personal details are **placeholders** to replace (see below).

It looks like a sheet of paper on desktop, works as a single column on phones, follows the device's dark mode, and **prints to exactly two A4 pages** ("Save as PDF").

## How to run

Open `index.html` in any modern browser. No server or install is needed.

To see every placeholder that still needs replacing, open **`index.html?placeholders`**. Each one is outlined in yellow.

## Files

| File | Purpose |
| --- | --- |
| index.html | The resume |
| css/styles.css | All styling: layout, typography, dark theme, print layout |
| js/main.js | Save as PDF button, copy email, printing the folded project list, placeholder highlighting |
| images/favicon.svg | Tab icon with your initials |
| screenshots/ | Your captured screenshots |

## Replacing the placeholders

Every placeholder in the page is wrapped in `<span class="ph">…</span>`, so you can search for `class="ph"`. Replace the text and keep the span, or remove the span once the text is yours.

| Find | Replace with | Where |
| --- | --- | --- |
| `Your Name` | Your full name | `<title>`, meta description, JSON-LD, `h1` |
| `YN` | Your initials | Monogram in the header, `images/favicon.svg` |
| `you@example.com` | Your email | Header, Contact, JSON-LD, both `mailto:` links |
| `+44 0000 000000` / `+440000000000` | Your phone number | Header and Contact (text and `tel:` links) |
| `Your City, Your Country` | Your location | Header, Contact, JSON-LD |
| `linkedin.com/in/your-profile` | Your LinkedIn address | Header and Contact (text and links) |
| `github.com/your-username` | Your GitHub address | Contact |
| `junior front-end or web developer role` | The role you want | Profile |
| `Month Year` | When you can start | Profile |
| `Your Degree, e.g. BSc Computer Science`, `Your University`, `20XX – 20XX` | Your degree | Education |
| `Web Development, Databases, …` | Your real modules | Education |
| `Training provider` | Who ran the training | Education |
| `Certification name`, `Issuing organisation`, `Year` (3 entries) | Your certificates, or delete the extra entries | Certifications |

If you have no GitHub or LinkedIn yet, delete those `<li>` / `<div>` lines rather than leaving placeholders.

## Page structure (HTML5 semantics)

```
a.skip-link
div.page
 ├─ header.resume-header        h1 (name), role, tagline, address (quick contact), buttons, nav (section links)
 ├─ main.resume-body
 │   ├─ div.col-main
 │   │   ├─ section#summary     h2 Profile, lead paragraph, key strengths list
 │   │   ├─ section#projects    h2, ol of 6 article.project (each with header, h3 link, time, points, tags), details (more projects)
 │   │   └─ section#education   h2, ol timeline
 │   └─ aside.col-side
 │       ├─ section#skills      h2, 4 groups of h3 + tag lists
 │       ├─ section#certifications
 │       └─ section#contact     h2, address with a dl, Copy email button, status message
 └─ footer.resume-footer
```

- **Headings:** one `h1` (your name). Each section has an `h2` and is labelled by it (`aria-labelledby`). Items inside use `h3`, so the outline reads correctly in a screen reader's headings list.
- **`<address>`** holds contact information (in the header and in Contact), as the HTML standard intends.
- **Projects** are an ordered list of `<article>` elements, because each project makes sense on its own. They're ordered from the most complete to the smallest.
- **Dates:** `<time datetime="2026-10">` gives machine-readable dates.
- **Contact pairs:** `<dl>` pairs each label with its value (Email → address).
- **More projects:** a `<details>` element folds the extra projects away on screen without any script.
- **Structured data:** `Person` JSON-LD helps search engines and job sites read the page.
- **Skip link:** jumps straight to the resume.

## Design decisions

### Layout

- **The paper sheet:** the resume sits on a light "desk" with a soft shadow, at most 64rem wide. On phones the sheet fills the screen with no margins, so no space is wasted.
- **Header (CSS Grid with named areas):** on wide screens the name is on the left and the buttons on the right, with contact details and section links below. On narrow screens everything stacks.
- **Body:** a two-column grid (`1.75fr | 1fr`) on screens 52rem and wider. The main column holds what a recruiter reads first (profile, projects, education). The side column holds quick-scan facts (skills, certifications, contact). Below 52rem it becomes one column in reading order.
- **Flexbox for rows that wrap:** contact items, buttons, section links, tags and project headings (title left, date right, wrapping on small screens).
- **Fluid sizing:** sizes scale smoothly with `clamp()` (name, role, lead text, padding), so there are few breakpoints.

### Typography and readability

- **Two typefaces:**
  - **Newsreader** (serif) for the name, the profile paragraph and project titles. It gives the page a professional, editorial feel.
  - **Inter** (sans-serif) for everything else, for clarity at small sizes.
- **Section titles:** small capitals with letter spacing and an accent bar, so sections are easy to find when scanning.
- **Line length and spacing:** the text is limited to about 70 characters a line, with 1.6 line height and generous space between sections.
- **Projects:**
  - each has one sentence of summary, then short bullet points of what was built
  - the points are measurable where possible ("14 management reports", "12 product pages")
  - technology tags come last, for quick scanning

### Colour

- **Palette:** a deep navy header and warm terracotta accent on white, chosen to look professional and calm. All colours are custom properties.
- **Dark mode:** if the device uses dark mode, the page switches automatically (`prefers-color-scheme`) by redefining the tokens.
- **Contrast:** all text meets the WCAG AA contrast ratio. The peach role text on navy is a light tint, not the darker accent, so it stays readable.

### Accessibility

- **Focus and skip link:** a visible focus outline on every link and button (light-coloured on the navy header), and a skip link.
- **Icons:** the contact icons are decorative (`aria-hidden`) because the text beside them says what they are. The monogram is hidden from screen readers because the name is right next to it.
- **Copy email:** the "Copied…" message is in a `role="status"` element, so screen readers announce it. If the browser blocks the clipboard, the message shows the address instead.
- **Animations:** only the smooth scrolling to sections, which is turned off for readers who ask for reduced motion.

### Print and PDF

A resume is often sent as a PDF, so the print styles were tested by printing to PDF in Edge:

- **Size:** A4 page size and margins (`@page`). The root font size drops to 8.5pt for print, and because every size is in `rem`, the whole page scales with it.
- **What's left out:** buttons, section links and notes that only make sense on screen. The key strengths list is also left out, because it repeats the Skills section.
- **Colour:** the navy header and tags keep their colour (`print-color-adjust: exact`).
- **Page breaks:** projects, education entries and certificates are never split across pages (`break-inside: avoid`). Section titles never sit alone at the bottom of a page.
- **More projects:** the folded list is opened automatically before printing (in `beforeprint`) and printed in two columns.
- **Result:** exactly **two A4 pages**.

## Testing checklist

Fill in the result column as you test.

| # | Test | How | Expected | Result |
| --- | --- | --- | --- | --- |
| 1 | Desktop | Open at 1280px | Paper sheet; header with name left and buttons right; two columns | |
| 2 | Phone | 375px | One column, no sideways scroll; header stacks | |
| 3 | Tablet | 768px | One column, comfortable margins | |
| 4 | Section links | Click Projects, Skills, Contact | Jumps to each section | |
| 5 | Project links | Click each project title | The working project opens | |
| 6 | More projects | Open "More projects" | 8 more websites and the SQL summary | |
| 7 | Copy email | Copy email address | "Copied …" message (or the address if blocked) | |
| 8 | Save as PDF | Save as PDF, choose "Save as PDF" | Two A4 pages, header in colour, no buttons | |
| 9 | Placeholders | Open `index.html?placeholders` | Every placeholder outlined in yellow | |
| 10 | Dark mode | Switch the OS to dark mode | Dark page, readable text | |
| 11 | Keyboard | Tab through the page | Skip link first; focus always visible | |
| 12 | Screen reader headings | Headings list (e.g. NVDA H key) | Name, then each section, then items | |
| 13 | Zoom | Browser zoom 200% | Nothing overlaps or is cut off | |
| 14 | HTML validation | validator.w3.org | No errors | |
| 15 | Lighthouse | DevTools > Lighthouse | Accessibility 95+ | |

**Automated checks I ran in headless Edge:**

| Check | Result |
| --- | --- |
| Page structure, sections, links, layout, copy email and print preparation | 15/15 pass at desktop width and 15/15 at phone width |
| Placeholder highlighting (`?placeholders`) | pass |
| Project links | all 14 point to files that exist |
| Print to PDF | exactly 2 A4 pages |

## Screenshots to take

1. Desktop: the full resume
2. Phone (375px): header and profile
3. Tablet (768px)
4. The projects section with "More projects" open
5. Dark mode
6. Print preview or the saved PDF (both pages)
7. `?placeholders` view (before you replace them)
8. DevTools Grid overlay on `.resume-body` and `.resume-header`
9. Lighthouse and validator results

## Demo script (about 2 minutes)

1. Show the page on desktop and explain the layout: a header, and two columns with the most important content on the left.
2. Open DevTools: show the semantic structure (header, main, aside, sections, `address`) and the Grid overlay.
3. Resize to phone width to show the single column, wrapping contact details and fluid type.
4. Click a project title to open the real project, then come back.
5. Press "Save as PDF" to show the two-page A4 print layout.
6. Switch to dark mode, then show `?placeholders` and explain how to personalise the page.

## Known limitations

| Limitation | Possible improvement |
| --- | --- |
| Personal details are placeholders | Replace them using the table above |
| Project links are relative, so they only work inside this course folder | Publish the projects online (for example GitHub Pages) and link to those addresses |
| The SQL project links to a README rather than a live demo | Add screenshots of the query results, or a small web front end |
| Fonts load from Google Fonts | Serve the font files from the project |
