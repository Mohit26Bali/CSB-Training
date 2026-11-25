# Blog Platform UI - Long Way Round

**Long Way Round** is a slow-travel journal with four writers and ten stories. It has three kinds of page, all linked together:

- **Home page:** featured stories, browsing by category, a searchable list of every story, a sidebar and the writers.
- **Story pages (10):** built for comfortable reading.
- **Writer profile pages (4).**

> **Demo:** the writers and their stories are fictional, but every place is real and the travel facts were written to be accurate. Writer portraits are stock photos of models from Pexels. The newsletter form does not send anything.

## How to run

Open `index.html` in any modern browser. No server or install is needed.

```
index.html (home)
 ├─ Featured (3) ───────────────┐
 ├─ Explore by category tiles ──┼─ filter "All stories" on the same page
 ├─ All stories (search, chips) ┼─> stories/<story>.html ──┬─ breadcrumb / tag ─> index.html?category=...
 ├─ Sidebar: popular, newsletter┘                          ├─ writer name / box ─> writers/<writer>.html
 └─ Meet the writers ───────────────> writers/<writer>.html └─ older / newer / related ─> other stories
```

## Files

| File | Purpose |
| --- | --- |
| index.html | Home page: featured stories, category tiles, All stories (search, category chips, Show more), sidebar (about, most read, newsletter), Meet the writers |
| stories/*.html | 10 story pages |
| writers/clara.html, sofia.html, dev.html, tom.html | 4 writer profile pages |
| css/styles.css | All styling: themes, header, cards (with container queries), story page typography, profiles, footer, print |
| js/site.js | Every page: the Display menu (theme and text size), saved settings |
| js/home.js | Home page: search, category filter, Show more, "Read" badges, newsletter form |
| js/story.js | Story pages: reading progress bar, table of contents highlighting, Copy link, marking a story as read |
| images/stories/ | Each story's cover photo at 1600 and 800 pixels wide, plus one photo inside the story (WebP) |
| images/writers/ | 4 writer portraits, 480 × 480 |
| images/favicon.svg | Compass tab icon |
| images/CREDITS.md | Photographer and source for every photo (Pexels) |
| screenshots/ | Your captured screenshots |

## Content

| Story | Category | Writer | Date |
| --- | --- | --- | --- |
| Twelve hours in Lisbon, on foot and by tram (lead feature) | Cities | Clara Mendes | 6 Oct 2026 |
| Falling asleep in Vienna, waking up in Venice (feature) | Rail journeys | Sofia Marchetti | 2 Oct 2026 |
| Eating my way through Hanoi, one plastic stool at a time (feature) | Food | Dev Raman | 28 Sep 2026 |
| Hut to hut across the Dolomites | Outdoors | Tom Lindqvist | 23 Sep 2026 |
| Kyoto belongs to early risers | Cities | Clara Mendes | 17 Sep 2026 |
| Iceland's Ring Road without breaking the bank | Budget & tips | Sofia Marchetti | 10 Sep 2026 |
| A market breakfast in Oaxaca | Food | Dev Raman | 3 Sep 2026 |
| The West Highland Line is the best train ride in Britain | Rail journeys | Tom Lindqvist | 27 Aug 2026 |
| How I pack for two weeks in one carry-on bag | Budget & tips | Clara Mendes | 20 Aug 2026 |
| Wild camping under the midnight sun in Lofoten | Outdoors | Tom Lindqvist | 12 Aug 2026 |

Every story has the same parts:
- a category, a place and a short summary
- a cover photo with a caption
- an introduction and three sections, with a photo inside the story and a pull quote
- a tips box
- an "About the writer" box

## Page structure (HTML5 semantics)

**Home page:**

```
header.site-header ─ logo, nav "Main", details.display-menu (form with 2 fieldsets)
main
 ├─ section.featured   h1 + 3 × article.story-card
 ├─ section.categories h2 + 5 links
 ├─ section#stories    h2, form[role=search] (search + fieldset of chips), p[aria-live], ul of article cards
 ├─ aside.sidebar      section (about), section (ol most read), section#newsletter (form)
 └─ section#writers    h2 + 4 × article.writer-card (dl of stats)
footer.site-footer     section + 2 × nav (categories, writers)
```

**Story page:**

```
main
 ├─ article.story
 │   ├─ header      nav.breadcrumb (ol), h1, summary, address (author), time[datetime], Copy link
 │   ├─ figure      cover photo + figcaption (caption and photo credit)
 │   ├─ nav.toc     details/summary + ol of section links
 │   └─ div.story-body
 │       ├─ section × 3 (each h2), figure, aside.pull-quote, aside.tip-box
 │       └─ footer  filed under, section.author-box, nav.pager (rel=prev / rel=next)
 └─ section.related  3 × article.story-card
```

- **One `h1` per page,** and headings never skip a level. The featured area has a visually hidden "Featured stories" `h2`, so the cards' `h3` titles sit under an `h2`.
- **`article`** is used for each story and each card (a card makes sense on its own). **`aside`** holds content beside the main story: the sidebar, the pull quote and the tips box.
- **`address`** marks up the author byline. The HTML standard says it is for contact information for the nearest `article`, which here is the writer's profile link. **`rel="author"`** marks the same link.
- **`time datetime="2026-09-28"`** gives a machine-readable date beside the readable "28 September 2026".
- **`figure` / `figcaption`** pair each photo with its caption and credit.
- **Breadcrumbs** are an `ol` inside `nav aria-label="Breadcrumb"`, with `aria-current="page"` on the last item.
- **Structured data:** `BlogPosting` JSON-LD on story pages and `ProfilePage` / `Person` on writer pages.

## Design decisions

### Layout (CSS Grid, Flexbox and container queries)

- **Featured grid:**
  - **Desktop:** a large lead story on the left and two stories stacked on the right. The lead photo grows (`grid-template-rows: 1fr auto`) so both columns end at the same line.
  - **Tablet:** the lead runs full width, with the other two side by side underneath.
  - **Phone:** everything is in one column.
- **One card, many layouts (container queries):** every story card is the same HTML. Its parent is a container (`container-type: inline-size`), and the card changes with the space it is given, not with the screen size:
  - **34rem or wider:** the card turns sideways, with the photo on the left and the text on the right. This is how "All stories" looks on desktop and tablet.
  - **Narrower:** the photo sits on top, as on phones, in the related grid and beside the lead.
  - **Under 17rem:** the summary is hidden to keep the card short.

  A media query couldn't do this, because the same card is narrow in one place and wide in another on the same screen.
- **Home body:** on desktop it is a two-column grid (stories plus a 21rem sidebar). On tablet the sidebar moves under the stories and splits into two columns. On a phone it is one column.
- **Category tiles:**
  - **Desktop:** a five-column grid.
  - **Smaller screens:** a swipeable row with scroll-snap. The row scrolls inside itself, so the page never scrolls sideways.
- **Story page grid:** `minmax(0, 1fr) | 42rem | minmax(0, 1fr)`, so the text stays centred under its title. The table of contents sits sticky in the left margin. Below 70rem the table of contents becomes a collapsible box above the story.
- **Header:** on phones the navigation moves to its own row, and the Display button shrinks to an icon.

### Readability

- **Line length:** story text is limited to 42rem, about 65–70 characters a line, which is the comfortable range for reading.
- **Typeface and spacing:** a serif made for long reading on screens (Source Serif 4), with a line height of 1.75. The size scales smoothly with `clamp()`, from 17px on phones to 19px on desktops.
- **Interface text** (buttons, labels, meta) uses Inter, a sans-serif, so it stands apart from the story text.
- **Helping readers find their way:**
  - a drop cap on the first paragraph
  - clear section headings
  - a pull quote and a tips box to break up long text
  - captions under every photo
- **Before reading:** each story shows its reading time (word count ÷ 200 words a minute) and its date.
- **While reading:** a progress bar and the highlighted table of contents show where the reader is.
- **Display menu:**
  - **Theme:** Match device, Light, Sepia or Dark. Sepia is a warm paper look for long reading.
  - **Text size:** Standard, Large or Larger. This changes the root font size, so every `rem` on the page grows with it.
  - **Saved settings:** the choice is stored and applied by a tiny script in `<head>` before the page is drawn, so the page never flashes the wrong theme.
- **Headlines** use `text-wrap: balance`, so they don't leave a single word on the last line.

### Visual design

- **Colours:** warm paper and ink with a terracotta accent, chosen to feel like a printed travel magazine. All colours are CSS custom properties, so each theme only redefines the tokens.
- **Dark theme:** it follows the device (`prefers-color-scheme`) unless the reader chooses a theme.
- **Cards:** whole-card click areas. The title link's `::after` covers the card, and the category and writer links sit above it, so they still work. On hover the card lifts slightly and the photo zooms gently.

### Accessibility

- **Skip link and focus:** a "Skip to content" link, and a visible focus outline on everything. Cards show a focus ring around the whole card when their title link is focused (`:has()`).
- **Filters:**
  - The category chips are real radio buttons in a `fieldset` with a `legend`, so they work with the keyboard and are announced as a group.
  - The search has a visible label.
  - The result count is a live region ("Showing 2 stories in Rail journeys").
- **Moving focus:**
  - "Show more" moves focus to the first newly shown story.
  - A category tile moves focus to the "All stories" heading.
  - "Show all stories" in the empty state moves focus back to the search box.
- **Display menu:** a native `details` element, so it opens and closes without script. Escape closes it and returns focus, and so does clicking anywhere else.
- **Pull quotes:** they have `aria-hidden="true"` because they repeat a sentence from the story, and a screen reader would otherwise read it twice.
- **Images:**
  - Every photo has alt text describing it.
  - Writer avatars next to a visible name have empty alt text, so the name isn't read twice.
  - Profile portraits have descriptive alt text.
- **Newsletter errors:** they are written in text, linked to the input with `aria-describedby`, and the input is marked `aria-invalid`.
- **Animations:** they are only used when the reader hasn't asked for reduced motion (`prefers-reduced-motion`). Smooth scrolling follows the same rule.
- **Print:** printing a story prints only the story (the header, table of contents and related stories are hidden).

### User experience

- **Search:** it matches every word typed against each card's title, summary, place, category and writer. For example "tom" finds Tom's 3 stories, and "japan" finds Kyoto. It waits 200 ms after the last key press, so the list doesn't flicker while typing.
- **Show more:** the list shows 6 stories at a time, and the button says how many are left. Changing the filter starts again from 6.
- **Shareable filter:** the chosen category is stored in the address (`?category=food#stories`). Breadcrumbs and category tags on story pages use the same link, so they open the home page already filtered.
- **"Read" badges:** a story counts as read once the reader reaches the writer's box at the end, and the home page then shows a "✓ Read" badge on that card.
- **Copy link:** it copies the story's address and shows a message. If the browser blocks the clipboard, which can happen for files opened from disk, the message shows the link instead.
- **Ways to continue reading:** "Older story" / "Newer story", "More from Clara" and three related stories (same category first).

## Testing checklist

Fill in the result column as you test.

| # | Test | How | Expected | Result |
| --- | --- | --- | --- | --- |
| 1 | Home page | Open `index.html` | 3 featured stories, 5 category tiles, "Showing 6 of 10 stories" | |
| 2 | Show more | Press "Show more stories (4 more)" | All 10 shown; focus on the 7th story; button gone | |
| 3 | Category | Choose "Rail journeys" | 2 stories; "Showing 2 stories in Rail journeys" | |
| 4 | Search | Type `japan` | Only the Kyoto story | |
| 5 | Search + category | Outdoors, then type `norway` | Only the Lofoten story | |
| 6 | No results | Type `zzzz` | "No stories found" and a "Show all stories" button | |
| 7 | Category tile | Click "Food" | List filtered to 2 Food stories; focus on "All stories" | |
| 8 | Filter in address | Open `index.html?category=budget` | Iceland and one-bag stories only | |
| 9 | Story page | Open the Hanoi story | Breadcrumb, byline, date, 2 min read, cover photo with caption | |
| 10 | Contents | Scroll through the story | The current section is highlighted; progress bar fills | |
| 11 | Contents (phone) | 375px: open "In this story", choose a section | Jumps to it and the box closes | |
| 12 | Read badge | Scroll to the end, go home | "✓ Read" on that story's card | |
| 13 | Copy link | Press Copy link | "Link copied…" message (or the link shown if blocked) | |
| 14 | Writer | Click "Dev Raman" | Profile with 2 stories and 3 other writers | |
| 15 | Breadcrumb | On a story, click the category in the breadcrumb | Home page filtered to that category | |
| 16 | Themes | Display > Sepia, then Dark, then reload | Colours change; choice kept after reload | |
| 17 | Text size | Display > Larger | All text grows; layout still fits | |
| 18 | Newsletter | Submit empty, then `sam@`, then a real address | Two different error messages, then a thank-you | |
| 19 | Phone | 375px | One column; nav on its own row; tiles swipe; no sideways page scroll | |
| 20 | Tablet | 768px | Story cards turn sideways; sidebar in two columns | |
| 21 | Keyboard | Tab through the home page and a story | Everything reachable; focus always visible; Escape closes Display | |
| 22 | Print | Print preview of a story | Only the story is printed | |
| 23 | HTML validation | validator.w3.org | No errors | |
| 24 | Lighthouse | DevTools > Lighthouse | Accessibility 95+ | |

**Automated checks I ran in headless Edge:**

| Page | Result |
| --- | --- |
| Home page | 35/35 pass: filters, search, Show more, focus, read badges, newsletter, Display menu |
| Hanoi story | 24/24 pass on four runs in a row: semantics, contents highlighting, progress bar, read tracking, Copy link |
| Tom's profile | 6/6 pass |
| Filter in the address | pass |

All 15 HTML files passed a tag and id check: no unclosed tags, no duplicate ids, and no references to ids that don't exist.

## Screenshots to take

1. Home page, desktop (1280px): featured grid and category tiles
2. All stories with the sidebar (desktop)
3. A category filter and a search with the result count
4. The empty "No stories found" state
5. Story page, desktop: title, byline, cover photo, sticky table of contents
6. Story body: drop cap, photo inside the story, pull quote, tips box
7. End of a story: the writer's box, older/newer links, related stories
8. Display menu open, with Sepia and Dark themes
9. A writer profile page
10. Phone (375px): home page and a story with the contents box open
11. Tablet (768px): sideways story cards (container query)
12. DevTools: the container query on a card, and the Grid overlay on `.featured-grid` and `.story-layout`
13. Lighthouse and validator results

## Demo script (about 3 minutes)

1. **Home page:** show the featured layout, then resize the window to show it change from three columns to two, then to one.
2. **Filters:** choose "Food" from the category tiles, then search for "train". Point out the live result count, then press "Show more".
3. **Container queries:** open DevTools and show that the same story card is sideways in "All stories" but stacked in the related grid. The card responds to its container, not the screen.
4. **A story:** open one and show the reading layout. Point out:
   - the line length and serif text
   - the drop cap
   - the sticky table of contents highlighting as you scroll
   - the progress bar
5. **Display menu:** switch to Sepia, then Dark, then Larger text. Reload to show the choice is remembered.
6. **Writer profile:** scroll to the writer's box at the end and open the profile.
7. **Read badge:** go back to the home page and point out the "Read" badge on the story you just finished.
8. **Semantics:** show the page source: `article`, `aside`, `address`, `time datetime`, `figure`, the breadcrumb and the JSON-LD.
9. **Phone width:** show the navigation row, the swipeable category tiles and the folded table of contents.

## Known limitations

| Limitation | Possible improvement |
| --- | --- |
| Stories are fixed HTML pages | Load stories from a content management system |
| Search only covers the 10 cards on the home page | Search the full text of every story on a server |
| The newsletter does not send anything | Connect to an email service, with confirmation by email |
| "Read" badges and settings are saved only in this browser | Store them in a reader account |
| No comments | Add moderated comments under each story |
| Fonts load from Google Fonts | Serve the font files from the project |
