# Portfolio Website

A responsive developer portfolio that shows **skills, projects and achievements**. Every project in it is real: the 15 websites and the SQL capstone built during this course, each with a screenshot, a short case study and a link to the working project.

> **Personal details are placeholders** ("Your Name", email, links, bio). Open **`index.html?placeholders`** to see each one outlined in yellow. The projects, numbers and achievements are real.

## How to run

Open `index.html` in any modern browser. No server or install is needed. The project links open the other course folders, so keep this folder next to them.

## Files

| File | Purpose |
| --- | --- |
| index.html | The portfolio (one page) |
| css/styles.css | All styling: light and dark themes, layouts, cards, dialog, motion, print |
| js/main.js | Theme switch, menu highlight, project and skill filters, case studies, contact form, placeholder view |
| images/projects/ | A screenshot of each project's home page (960 × 600 WebP), captured from the real projects |
| images/favicon.svg | Tab icon with your initials |
| screenshots/ | Your captured screenshots |

## Sections

| Section | What it shows | Main HTML |
| --- | --- | --- |
| Header | Name, section links, light/dark switch | `header`, `nav`, `button aria-pressed` |
| Hero | Who you are, what you do, two actions, availability, 4 key numbers | `h1`, `dl` of numbers |
| Projects | 16 projects with filters by type, each with a case study | `form` + `fieldset` of radio chips, `ul` of `article` cards, `dialog` |
| Skills | 12 skills in 3 groups, each with how many projects use it | `section`, `h3` groups, buttons |
| Achievements | 5 achievements with numbers, and a 5-step learning journey | `ul`, `data`, `ol` |
| About | A short bio and quick facts | `dl` |
| Contact | Links and a contact form | `form` with labels and errors |
| Footer | Copyright and back to top | `footer` |

## Design decisions

### Content first: evidence, not claims

- **No skill bars with made-up percentages.** Each skill shows **how many projects use it**, counted from the project list. Choose a skill and the project grid shows exactly those projects. The bar length is relative to the most-used skill (Responsive design, 13 projects).
- **Every number can be checked:**
  - 410 → 0 accessibility failures (the Fernhill redesign)
  - 96 automated checks (the capstone)
  - 0 chart libraries (the dashboard)
  - 30 projects in total
- **Short case studies.** Each case study has the same four parts: the brief, what I built, one decision I made, and the result. This is what interviewers usually ask about.
- **Built from one list.** The page was generated from a single list of projects, so the cards, case studies, filter counts and skill counts always agree. The case study text is also stored in the page as JSON (`#case-data`), which `main.js` reads when a case study opens.

### Layout (CSS Grid and Flexbox)

- **Hero:** a two-column grid (text and a fan of three project screenshots), stacking on smaller screens. The screenshots are decorative (`aria-hidden`), because the same projects appear with descriptions below.
- **Project grid:** `repeat(auto-fill, minmax(min(100%, 20rem), 1fr))`, giving 1, 2 or 3 columns.
  - **3 columns:** the featured first project and the SQL project (shown as a code sample) span two columns, so the 16 cards fill exactly six rows.
  - **2 columns:** every card spans one column, so they fill eight rows.
- **Cards:** Flexbox in a column, with `margin-top: auto` on the buttons, so buttons line up at the bottom whatever the text length.
- **Other grids:** skills, achievements, journey, about and contact all use `auto-fit` grids that reflow without many breakpoints.
- **Phones:** the five section links become a horizontally scrolling row under your name, so every section is one tap away without a hidden menu.

### Visual design

- **Typefaces:** Space Grotesk for headings (modern, slightly technical) and Inter for reading.
- **Colour:** one indigo accent for actions and highlights, and a lime highlighter mark on "everyone" in the headline.
- **Themes:** a light and a dark theme, built from colour tokens. It follows the device setting until the visitor chooses; the choice is saved and applied before the page is drawn, so it never flashes.
- **The SQL project's "screenshot"** is a real query from the HR database, shown with simple syntax colouring.

### Accessibility

- **Semantics:** a skip link, landmarks, one `h1`, and every section labelled by its heading.
- **Filters:**
  - the filter chips are real radio buttons in a `fieldset`
  - the result count is a `role="status"` message ("Showing 3 projects: Live data")
  - choosing a skill moves focus to the Projects heading
- **Case studies:** a native `<dialog>`, which keeps focus inside, closes with Escape and returns focus to the button that opened it. The screenshot has alt text; the database case study has no screenshot, so none is shown.
- **Buttons and links name their project** for screen readers ("Case study: Fernhill College redesign"), because a list of identical "View project" links would be confusing.
- **Theme switch:** a toggle button with `aria-pressed`.
- **Contact form:** labels, a hint, errors written next to each field, `aria-invalid`, and focus moves to the first problem.
- **Motion:** card lift, image zoom, the waving hand and the scroll reveal only run if the visitor hasn't asked for reduced motion.

### User experience

- **The contact form really works without a server:** after checking the fields, it opens the visitor's email app with the message, subject and their details filled in (`mailto:`), and says so first.
- **"Read my resume"** links to the resume page, which saves as a 2-page PDF.
- **Every "View project" link opens the working project** in this course folder.

## Replacing the placeholders

Search for `class="ph"` in `index.html`, or open `index.html?placeholders`.

| Find | Replace with |
| --- | --- |
| `Your Name`, `YN` | Your name and initials (also `<title>`, meta description, JSON-LD and `images/favicon.svg`) |
| `junior front-end roles`, `Month Year` | The role you want and when you can start |
| `you@example.com`, `your-profile`, `your-username` | Your email, LinkedIn and GitHub (text and links); the contact form sends to the email in the Contact links |
| About text, `Your City`, `e.g. React and TypeScript`, `Your hobbies` | Your bio and facts |
| `Your award, certificate or hackathon` | A real achievement, or delete that card |

The about "photo" is a monogram. To use a real photo, replace `<div class="about-photo">` with an `<img>` with descriptive alt text.

## Testing checklist

Fill in the result column as you test.

| # | Test | How | Expected | Result |
| --- | --- | --- | --- | --- |
| 1 | Desktop | Open at 1280px | Hero with screenshots; 3-column project grid with no gaps | |
| 2 | Phone | 375px | One column; section links scroll sideways; no page scroll sideways | |
| 3 | Filter | "Live data" chip | 3 projects; "Showing 3 projects: Live data" | |
| 4 | Skill | Skills, then "SVG" | Jumps to Projects showing Wanderlane Admin; "Show all projects" button | |
| 5 | Case study | "Case study" on any project | Dialog with brief, built, decision, result and a link | |
| 6 | Dialog keys | Tab inside the dialog, then Escape | Focus stays inside; Escape closes and focus returns | |
| 7 | View project | Click "View project" on each card | The real project opens | |
| 8 | Theme | Theme button, then reload | Switches theme and remembers it | |
| 9 | Contact errors | Send the empty form | Three errors; focus on the name | |
| 10 | Contact send | Fill in correctly and send | Your email app opens with the message ready | |
| 11 | Placeholders | Open `index.html?placeholders` | Every placeholder outlined in yellow | |
| 12 | Reduced motion | Turn on "reduce motion" | No lifting, zooming, waving or fading | |
| 13 | Keyboard | Tab through the whole page | Skip link first; focus always visible | |
| 14 | HTML validation | validator.w3.org | No errors | |
| 15 | Lighthouse | DevTools > Lighthouse | Accessibility 95+ | |

**Automated checks I ran in headless Edge:**

| Check | Result |
| --- | --- |
| Page tests at desktop width (filters, skill filter, skill counts against the cards, case-study dialog, theme, contact form) | 21/21 pass |
| Same tests at phone width | 21/21 pass |
| axe-core, light theme | 0 issues |
| axe-core, dark theme | 0 issues |
| Project links | all 16 point to files that exist |

**Note on the axe run:** it was done with reduced motion on. With motion on, cards further down the page start semi-transparent until they scroll into view, and axe reports their faded colours as contrast failures before they appear. Visitors always see them at full strength.

## Screenshots to take

1. Hero, light and dark themes
2. Projects grid on desktop (3 columns), then filtered to "Live data"
3. A case study dialog
4. Skills, and the grid after choosing a skill
5. Achievements and the learning journey
6. Contact form with errors
7. Phone (375px): hero, scrolling section links and project cards
8. `?placeholders` view
9. DevTools Grid overlay on `.project-grid`
10. Lighthouse results

## Demo script (about 3 minutes)

1. **Hero:** introduce yourself, and point out the four numbers and the theme switch.
2. **Projects:** filter to "Live data", then open a case study and walk through brief, built, decision, result. Open one project to show it's real.
3. **Skills:** explain why there are no percentage bars, then choose "Accessibility (WCAG)" to see the evidence.
4. **Achievements:** the 410 → 0 redesign result, and the learning journey.
5. **Phone width:** resize to show the scrolling section links and the one-column cards.
6. **Contact form:** send it empty to show the errors, then show that a valid message opens the email app.

## Known limitations

| Limitation | Possible improvement |
| --- | --- |
| Personal details are placeholders | Replace them using the table above |
| Project links only work inside this course folder | Publish the projects (for example on GitHub Pages) and link to those |
| The contact form needs an email app on the visitor's device | Use a form service or a small server to send messages |
| Screenshots are static | Record short screen videos of the interactive projects |
