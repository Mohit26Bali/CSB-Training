# News Portal Layout - Orbit Daily

**Orbit Daily** is a multi-column space news website built with CSS Grid and responsive breakpoints. It shows **real, live data**:
- today's space news with images
- upcoming rocket launches with countdowns
- the live position of the International Space Station

If the live services can't be reached, it shows a saved snapshot of real stories.

> **Demo:** Orbit Daily is not a real news outlet. The headlines, summaries, authors and images are real and come from their publishers through the Spaceflight News API. Every story links to its original article. The page shows this note at the top.

## How to run

Open `index.html` in any modern browser (Chrome, Edge, Firefox). No server or install is needed.

- **With internet:**
  - the page loads live news, launches and the ISS position
  - the status line in the masthead says **"Live · updated just now"**
- **Without internet:**
  - the page shows saved stories from 8 October 2026
  - the status line says **"Showing saved stories from 8 Oct, …"**

## Files

| File | Purpose |
| --- | --- |
| index.html | Front page. It already contains the saved stories, so it works without JavaScript or internet. |
| article.html | Story page, opened as `article.html?id=40300` |
| css/styles.css | All styling: the front-page grid, briefing columns, section layouts, light and dark themes, print |
| js/common.js | Shared code: fetching with time limits and caching, data formatting, "2 hours ago" times, countdowns, theme, image fallback |
| js/main.js | Front page: ticker, front grid, briefing, launches, topic sections, live ISS tracker, newsletter form |
| js/article.js | Story page: loads one story, text size control, related stories |
| js/snapshot.js | Saved copy of real data (24 stories, 12 topic stories, 6 launches, ISS positions) for offline use |
| images/gallery/ | 8 NASA images (public domain) |
| images/saved/ | Local copies of the saved stories' images |
| images/world-map.webp | NASA Blue Marble map for the ISS tracker |
| images/placeholder.svg, favicon.svg | Fallback image and tab icon |
| images/CREDITS.md | Credits for every image |
| screenshots/ | Your captured screenshots |

## Where the live data comes from

All four services are free, need no API key, and allow browser requests from any site (CORS).

| Data | Service | How often |
| --- | --- | --- |
| News stories, summaries, authors, images | Spaceflight News API (`api.spaceflightnewsapi.net/v4/articles`) | Every 5 minutes |
| Topic sections | Same API with `?search=moon`, `mars`, `climate` | With the news |
| Launch schedule | Launch Library 2 (`ll.thespacedevs.com/2.3.0/launches/upcoming`) | Every 30 minutes; the free tier allows about 15 requests an hour |
| ISS position and the last 45 minutes of its path | Where the ISS at? (`api.wheretheiss.at`) | Every 5 seconds, paused when the tab is hidden |

**How it works:**
1. **The saved stories are already in the HTML,** so the page is never empty.
2. **JavaScript then calls each API with `fetch()`** and replaces the saved content with live data.
3. **Each request has a 10-second time limit** and is cached in `sessionStorage`, so reloading doesn't waste the rate limits.
4. **If a service fails,** that part keeps its saved content and the status line says so. The rest still updates.

**"Realtime" here means polling:** asking again every few minutes. True push updates (WebSockets or Server-Sent Events) would need our own server.

## Page structure

| Part | HTML elements |
| --- | --- |
| Masthead | `<header>` with the date in `<time>`, a live status line (`role="status"`), the theme button, the logo, and `<nav aria-label="Sections">` |
| Ticker | A region labelled "Latest headlines". The list is repeated for smooth scrolling; the copy is `aria-hidden` and its links are not tabbable. It has a Pause button. |
| Front page | `<section>` containing `<article>` stories, a "Latest" `<ol>` and a "Next launch" `<section>` |
| Story | `<article>` with a kicker, `<h2>`/`<h3>` headline, summary, and byline: author, source and `<time datetime>` |
| Briefing, launches, topics | `<section>` each, with its own `<h2>` |
| ISS tracker | `<figure>` with the map and `<figcaption>`, and a `<dl>` of position, height, speed and light |
| Gallery | `<ul>` of `<figure>`s with captions and credits |
| Story page | `<nav aria-label="Breadcrumb">`, `<article>` with `<header>`, `<h1>`, byline, `<figure>`, text, and related `<section>`s |

## Design decisions

### The front page grid (the core of this brief)

The front page is one CSS Grid. **Only `grid-template-areas` changes at each breakpoint;** the HTML stays the same.

```
Phone (under 640px)      Tablet (640px+)          Laptop (960px+)                   Wide (1280px+)
1 column                 2 columns                3 columns                         4 columns

+--------------+         +-------+-------+        +-------+-------+-------+         +-----+-----+-----+-----+
| lead         |         | lead          |        | lead          | latest|         |lead       |sec 1|lates|
+--------------+         +-------+-------+        |               |       |         |           +-----+-----+
| next launch  |         | sec 1 | sec 2 |        +-------+-------+-------+         |           |sec 2|launc|
+--------------+         +-------+-------+        | sec 1 | sec 2 |launch |         +-----+-----+-----+-----+
| second 1     |         | latest| launch|        +-------+-------+-------+
+--------------+         +-------+-------+
| second 2     |
+--------------+
| latest       |
+--------------+
```

```css
@media (min-width: 1280px) {
    .front {
        grid-template-columns: repeat(4, minmax(0, 1fr));
        grid-template-areas:
            "lead lead second1 latest"
            "lead lead second2 launch";
    }
}
```

- **On phones, "Next launch" comes second,** straight after the lead story, because the countdown is the most "live" thing on the page. On wider screens it moves to the side.
- **The HTML order is always lead, second stories, latest, next launch,** so screen-reader and keyboard order stays sensible at every width.

### Other layout techniques

| Section | Technique |
| --- | --- |
| **The briefing** | **CSS multi-column text:** `column-count` of 1, 2 or 3 at the breakpoints, with a `column-rule` line between columns, like a printed newspaper. `break-inside: avoid` keeps each story in one column. |
| **Launch schedule** | `repeat(auto-fill, minmax(min(100%, 280px), 1fr))`, so cards wrap without breakpoints |
| **Moon** | Span-based layout: the feature story spans 2 columns and 3 rows, and three short stories sit beside it in a row layout |
| **Mars** | Equal cards with `auto-fill` |
| **Earth** | A two-column list with each image beside its text (Flexbox inside Grid) |
| **ISS tracker** | Map and stats side by side from 960px. The dot and trail are placed by converting latitude and longitude to percentages on the map. |
| **NASA gallery** | Mosaic: `.big` spans 2×2, `.tall` spans 2 rows, `.duo` spans 2 columns, `.wide` spans the whole row. The spans change at 640px and 1100px. |
| **Masthead and footer** | Flexbox, and `auto-fit` grid |

**Whole cards are clickable.** The headline link stretches over the whole card with a `::after` overlay. That gives one link per story, not three, which is better for screen readers.

### Responsive design

| Width | What changes |
| --- | --- |
| Under 640px | Everything in one column. Section nav scrolls sideways. Lead summary cut to 4 lines. |
| 640px | Front page 2 columns. Briefing 2 text columns. Gallery 2 columns. |
| 768px | Earth list becomes 2 columns |
| 960px | Front page 3 columns. Moon feature layout. ISS map beside its stats. Section nav centred. |
| 1100px | Briefing 3 text columns. Gallery mosaic with 4 columns. |
| 1280px | Front page 4 columns: the full newspaper layout |

### Visual design

- **Light "newsprint" theme:** warm paper background (`#f6f3ec`), black ink, a rust-orange accent, double rules under the masthead and above the footer, and uppercase section labels.
- **Dark "night sky" theme:** follows the system setting. The **Light/Dark** button overrides it and is remembered.
- **Fonts:** Newsreader, a serif designed for news, for headlines and story text; Inter for labels and controls.
- **Kickers:** small orange topic labels such as MOON, MARS or LAUNCHES. They are worked out from each story's headline and summary.
- **Status badges:** "Go for Launch" in green, TBD in grey, Hold in amber, always with text.

### Accessibility

- Landmarks (header, nav, main, footer) and a clear heading order: one `<h1>` per page (the logo on the front page, the headline on the story page), then `<h2>` sections and `<h3>` stories.
- A skip link goes to the top stories.
- **Ticker:**
  - it can be paused, as WCAG requires for moving content, and it also pauses on hover and keyboard focus
  - with reduced motion it doesn't move at all and becomes a scrollable row
- **The live status line is `role="status"`,** so screen readers hear "Live" or "Showing saved stories" once. The countdowns and ISS numbers are **not** live regions, because they would interrupt every second.
- Story images that repeat the headline are decorative (`alt=""`), so the headline isn't read twice. Gallery images have full descriptions.
- Times use `<time datetime>` for machines, and readable text for people ("2 hours ago", with the exact time in the tooltip).
- **Story page:** a text size control (A−, A, A+) that uses `aria-pressed` and is remembered between stories.
- Focus outlines are visible everywhere. All controls are at least 44px tall.
- A print stylesheet removes the navigation, ticker, tracker and gallery.

### User experience

- A **live status line** shows whether the news is live or saved, and when it last updated.
- Times update themselves ("just now", then "5 minutes ago").
- **Countdowns** tick every second (T− 1d 03:20:17) and switch to T+ after launch.
- **No story appears twice:** the topic sections skip stories already on the front page.
- A **broken image** is replaced by a placeholder, so there are no empty boxes.
- A **"Back to top" button** appears after scrolling.
- The **newsletter form** checks the email address and confirms. It's a demo, so nothing is sent.

### Honesty about the content

- The stories are real. Orbit Daily only shows the **publisher's summary** and links to the full article, like Google News.
- No authors, quotes or stories were invented. Bylines show the real author and source.
- Two gallery images are labelled as not being photos: the **Juno illustration** and the **Earthrise visualisation**.

### How the HTML was produced

- `index.html` is the page template with the saved stories already filled in.
- The page's own JavaScript rendered the snapshot once, and the result was kept. So the saved HTML and the live updates come from **the same code** and always look the same.

## Testing checklist

Fill in the result column as you test. Results depend on that day's news.

| # | Test | How | Expected | Result |
| --- | --- | --- | --- | --- |
| 1 | Live data | Open with internet | Status says "Live · updated just now"; today's stories | |
| 2 | Offline | Turn off Wi-Fi, reload | "Showing saved stories from …"; page still complete | |
| 3 | Phone | DevTools, 375px | One column: lead story, then next launch | |
| 4 | Tablet | 768px | Front page 2 columns, briefing 2 columns | |
| 5 | Laptop | 1024px | Front page 3 columns, lead beside Latest | |
| 6 | Wide | 1440px | Front page 4 columns | |
| 7 | Grid overlay | DevTools > Elements > "grid" badge on `.front` | Area names lead, second1, latest… shown | |
| 8 | Briefing columns | Resize past 640px and 1100px | 1, then 2, then 3 text columns with rules | |
| 9 | Countdown | Watch the next launch | Ticks every second | |
| 10 | ISS | Watch the map for 30 seconds | Dot moves; speed about 27,600 km/h | |
| 11 | Ticker | Hover it, then press Pause | Stops; button says Play | |
| 12 | Reduced motion | Turn on "reduce motion" in the OS | Ticker doesn't move, no pulsing | |
| 13 | Theme | Press Light/Dark, reload | Theme changes and is remembered | |
| 14 | Story page | Select any story | Headline, image, summary, "Read the full story" | |
| 15 | Full story link | Select "Read the full story" | Opens the publisher's article | |
| 16 | Text size | A+ on a story | Text gets bigger; remembered on the next story | |
| 17 | Related | Bottom of a story | "More from NASA" (or the story's source) and "More on …" | |
| 18 | Bad link | Open `article.html?id=1` | "Story not found" with a link home | |
| 19 | Newsletter | Submit "astro@" | Error message | |
| 20 | Keyboard | Tab through the page | Every story and control reachable, focus visible | |
| 21 | No JavaScript | Disable JavaScript | Saved stories, launches and gallery still shown | |
| 22 | Print | Print a story page | Clean page without navigation | |
| 23 | HTML validation | validator.w3.org | No errors | |
| 24 | Lighthouse | DevTools > Lighthouse | Accessibility 95+ | |

## Screenshots to take

1. Front page at 1440px (4 columns), light theme
2. Front page at 1024px (3 columns)
3. Tablet, 768px
4. Phone, 375px
5. DevTools Grid overlay showing the named areas on `.front`
6. The briefing in 3 columns
7. Launch schedule with countdowns
8. ISS tracker
9. Moon and Earth sections (two different layouts)
10. NASA gallery mosaic
11. Dark theme
12. A story page, with related stories
13. Offline mode ("Showing saved stories")
14. Lighthouse and validator results

## Demo script (about 3 minutes)

1. Open the page and point to **"Live · updated just now"**: these are today's real stories. Open one to show it links to the original publisher.
2. Show the launch countdown ticking and the ISS dot moving across the map.
3. **The main point: resize the window from wide to phone.** The front page goes from 4 columns to 3, 2 and 1. Turn on the DevTools Grid overlay to show the named areas, and show the `@media` rules where only `grid-template-areas` changes.
4. Show the briefing's newspaper columns changing from 3 to 2 to 1.
5. Scroll through the Moon, Mars and Earth sections, which use three different grid patterns, and the NASA gallery mosaic.
6. Switch to dark mode. Pause the ticker.
7. Turn off the Wi-Fi and reload. The page still works and says it is showing saved stories.

## Known limitations

| Limitation | Possible improvement |
| --- | --- |
| Only the publisher's summary is shown | A real news site would publish its own full articles |
| Updates by polling, not push | WebSockets or Server-Sent Events with our own server |
| The launch API allows about 15 requests an hour without a key | A free API key, or a small server that caches the data |
| Topic sections use simple keyword search | Proper tagging by an editor |
| The saved snapshot gets older | Rebuild it regularly (a scheduled job) |
| Images from publishers vary in quality and shape | A server that crops and resizes them |
