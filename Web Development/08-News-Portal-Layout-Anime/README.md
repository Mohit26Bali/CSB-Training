# News Portal Layout - Panel & Pixel (anime version)

**Panel & Pixel** is a multi-column anime news website built with CSS Grid and responsive breakpoints. It is the anime companion to the space version (`08-News-Portal-Layout`), using the same newspaper grid. It shows **real, live data**:

- **Today's anime news** from Crunchyroll News, Anime News Network and MyAnimeList
- **This week's airing schedule** for the 50 most popular shows, with countdowns to each episode
- **Trending shows, the current season chart and next season's most anticipated shows**, from AniList
- **Fan favourites:** *Fullmetal Alchemist: Brotherhood* and *Demon Slayer*, with their live all-time rankings
- **A page for every show,** with synopsis, characters, trailer, next episode and related news

> **Demo:** Panel & Pixel is not a real news outlet. The headlines, show data and images are real. Every headline links to the original article and every show to its AniList page. The page shows this note at the top.

## How to run

### With live news (recommended)

1. Open a terminal in this folder.
2. Run `python server.py`.
3. Your browser opens **http://localhost:8000**.
4. Press **Ctrl+C** in the terminal to stop the server.

`server.py` uses only Python's standard library, so nothing needs to be installed.

### Without the server

You can also just open `index.html`:
- **The show data is still live,** because AniList is called straight from the browser.
- **The news headlines are saved ones** from 8 October 2026.
- The status line says so: **"Shows live · news saved 8 Oct (run server.py for live news)"**.

### Offline

With no internet at all, everything comes from the saved snapshot, and the status line says **"Showing data saved 8 Oct"**.

## Why a server is needed for the news

Browsers only let a web page read data from another website if that website allows it, which is called **CORS**.
- **AniList allows it,** so the page calls AniList directly with `fetch()`.
- **The news feeds don't allow it.** Crunchyroll, Anime News Network and MyAnimeList publish RSS feeds, but don't send the CORS header, so the browser blocks the page from reading them.

`server.py` solves this:
1. It serves the website at `http://localhost:8000`.
2. At `/api/news` it reads the three feeds itself (servers aren't limited by CORS).
3. It merges the stories newest first and returns them to the page as JSON.
4. It caches them for 5 minutes and skips any feed that fails.

```
Browser ──fetch('/api/news')──> server.py ──> Crunchyroll, ANN and MAL RSS feeds
Browser ──fetch(AniList GraphQL)──> graphql.anilist.co        (allowed directly)
```

## Files

| File | Purpose |
| --- | --- |
| index.html | Front page. It already contains the saved data, so it works without JavaScript, the server or the internet. |
| show.html | Show page, opened as `show.html?id=5114` |
| server.py | Local server: serves the site and the live news at `/api/news` |
| css/styles.css | All styling: front-page grid, briefing columns, schedule, posters, favourites, show page, themes |
| js/anilist.js | The GraphQL queries: one request fills the whole front page |
| js/common.js | Shared code: cached fetching, seasons, formatting, countdowns, theme, image fallback |
| js/main.js | Front page: news, schedule tabs, trending, season chart, favourites, coming soon, newsletter |
| js/show.js | Show page: details, characters, trailer, related news |
| js/snapshot.js | Saved real data from 8 October 2026: 60 headlines and all the show lists |
| images/saved/ | Local copies of cover art, banners and news images for offline use |
| images/placeholder.svg, favicon.svg | Fallback image and tab icon |
| images/CREDITS.md | Image credits |
| screenshots/ | Your captured screenshots |

## Page structure

| Part | HTML elements |
| --- | --- |
| Masthead | `<header>`: date in `<time>`, live status (`role="status"`), theme button, logo as the page's `<h1>`, Japanese name in `<span lang="ja">`, `<nav aria-label="Sections">` |
| Ticker | A region labelled "Latest headlines", with a Pause button. The repeated copy is `aria-hidden`. |
| Front page | `<article>` news stories, a "Latest" `<ol>`, and a "Next big episode" `<section>` with a countdown (`role="timer"`) |
| Airing this week | Seven day tabs (`role="tablist"`, `tab`, `tabpanel`), each with an `<ol>` of episodes and air times in `<time>` |
| Trending | Ranked `<ol>` of `<article>` cards |
| Season chart, coming soon | `<ul>` of poster `<article>`s |
| Fan favourites | Two `<article>`s with a `<dl>` of stats and a list of ranking badges |
| Show page | Banner `<section>`, breadcrumb `<nav>`, `<h1>`, `<aside>` with a facts `<dl>`, characters `<ul>`, trailer link |

## Design decisions

### The front page grid (the core of this brief)

This is the same four-breakpoint grid as the space version. Only `grid-template-areas` changes:

```
Phone (under 640px)      Tablet (640px+)          Laptop (960px+)                   Wide (1280px+)
1 column                 2 columns                3 columns                         4 columns

+--------------+         +-------+-------+        +-------+-------+-------+         +-----+-----+-----+-----+
| lead         |         | lead          |        | lead          | latest|         |lead       |sec 1|lates|
+--------------+         +-------+-------+        |               |       |         |           +-----+-----+
| next episode |         | sec 1 | sec 2 |        +-------+-------+-------+         |           |sec 2|next |
+--------------+         +-------+-------+        | sec 1 | sec 2 | next  |         +-----+-----+-----+-----+
| second 1     |         | latest| next  |        +-------+-------+-------+
+--------------+         +-------+-------+
| second 2     |
+--------------+
| latest       |
+--------------+
```

The stories with images take the big slots (lead and second); text-only stories go in "Latest" and the briefing.

### Other layout techniques

| Section | Technique |
| --- | --- |
| **The briefing** | CSS multi-column text (`column-count` 1, 2 or 3) with a `column-rule` line, like a newspaper |
| **Airing this week** | Episode list: 1, 2 or 3 columns. Each row is a small grid with `grid-template-areas` (time, cover, title, countdown). |
| **Trending** | **A swipeable row on phones and tablets** (`grid-auto-flow: column` with scroll snapping), a 5-column grid from 1100px |
| **Season chart** | `auto-fill` poster grid, and exactly 6 columns from 960px |
| **Fan favourites** | Cards with the banner image behind a gradient. Inside, `grid-template-areas` places the cover, title, stats, synopsis and links, and changes from phone to tablet. Side by side from 1024px. |
| **Show page** | Banner hero, then a main column and an `<aside>` of facts, side by side from 960px. Characters in an `auto-fill` grid. |

### Responsive design

| Width | What changes |
| --- | --- |
| Under 640px | One column. Trending swipes sideways. Day tabs and section nav scroll. Lead summary cut to 4 lines. |
| 640px | Front page 2 columns. Briefing 2 columns. Favourites put stats beside the cover. |
| 768px | Schedule 2 columns |
| 960px | Front page 3 columns. Season chart 6 columns. Show page facts beside the synopsis. |
| 1024px | Fan favourites side by side |
| 1100px | Briefing 3 columns. Schedule 3 columns. Trending 5-column grid. |
| 1280px | Front page 4 columns |

### Visual design

- **Colours:** sakura pink (`#d6246e`) with indigo. Score badges are indigo, and all-time ranking badges are gold.
- **Light and dark themes:** the dark theme follows the system, and the Light/Dark button overrides it and is remembered.
- **Fonts:**
  - **Dela Gothic One** for the logo and section titles, a bold display font in the style of Japanese posters
  - **Zen Kaku Gothic New** for text; it includes Japanese characters, so native titles like 鋼の錬金術師 display properly

### Accessibility

- Landmarks, one `<h1>` per page, and `<h2>` for each section.
- A skip link goes to the top stories.
- **Japanese text is marked `lang="ja"`,** so screen readers pronounce it correctly.
- **Scores have a full label for screen readers,** for example "Average score 90 out of 100".
- **Trending ranks are read as text,** for example "Trending number 1".
- **Cover images that repeat the title are decorative** (`alt=""`), so titles aren't read twice. The show page cover has alt text.
- **Day tabs follow the ARIA tab pattern:**
  - arrow keys, Home and End move between days
  - only the selected tab is in the Tab order
  - one listener on the container keeps the tabs working when the schedule refreshes
- **The status line is a live region; the countdowns are not,** so screen readers aren't interrupted every second.
- **The ticker can be paused** and stops completely with reduced motion.
- Focus outlines are visible everywhere. Controls are at least 44px tall.

### User experience

- **One request for the front page:** a single GraphQL query fetches trending, the season chart, next season, the favourites and the 50 airing shows. It's cached for 10 minutes, well within AniList's rate limit.
- **The schedule starts at "Today"** and uses your own time zone. If today's episodes have aired, it says "No more episodes … today. Try Tomorrow."
- **Countdowns:** the next big episode, and every episode in the schedule and trending list.
- **Season names update automatically:** in January the page shows the Winter season and "Coming in Spring".
- **Show pages** list related news, found by matching the show's title in the headlines.
- **Partial failures are handled:** if only the news fails, the shows still update, and the status line says exactly what is live.

### Honesty about the content

- Headlines and summaries are the publishers' own, and link to the full articles.
- No stories, quotes or scores were invented.
- Cover art and banners belong to the studios, and are shown with links to AniList.

### How the HTML was produced

`index.html` contains the saved data, rendered once by the page's own JavaScript and kept, so it works without JavaScript. In the browser, the schedule is always redrawn, because "Today" depends on the current date.

## Testing checklist

Fill in the result column as you test. Results depend on the day's news.

| # | Test | How | Expected | Result |
| --- | --- | --- | --- | --- |
| 1 | Live | `python server.py` | Status "Live · updated just now", today's headlines | |
| 2 | Without server | Open `index.html` directly | "Shows live · news saved … (run server.py for live news)" | |
| 3 | Offline | Turn off Wi-Fi, reload | "Showing data saved 8 Oct"; page still complete | |
| 4 | Headline link | Select a headline | Opens the original article (Crunchyroll, ANN or MAL) | |
| 5 | Phone | DevTools, 375px | One column; Trending swipes sideways | |
| 6 | Tablet | 768px | Front page 2 columns | |
| 7 | Laptop | 1024px | Front page 3 columns; favourites side by side | |
| 8 | Wide | 1440px | Front page 4 columns | |
| 9 | Grid overlay | DevTools "grid" badge on `.front` | Area names lead, second1, latest, next | |
| 10 | Schedule tabs | Click Tomorrow, then press the Right arrow | Panels change; focus moves | |
| 11 | Countdown | Watch "Next big episode" | Ticks every second | |
| 12 | Season name | Check the section titles | "Fall 2026 season", "Coming in Winter 2027" | |
| 13 | Fan favourites | Scroll to the section | FMA:B "#4 highest rated of all time", Demon Slayer "#2 most popular of all time" (live values) | |
| 14 | Show page | Select Demon Slayer | Synopsis, 6 characters including Tanjiro, studio ufotable, trailer | |
| 15 | Show page offline | Offline, open a saved show | Saved facts with "Showing saved data" | |
| 16 | Bad show id | `show.html?id=999999999` | "Show not found" | |
| 17 | Theme | Press Light/Dark, reload | Theme kept | |
| 18 | Ticker | Hover, then Pause | Stops; button says Play | |
| 19 | Reduced motion | Turn on "reduce motion" | Ticker still, no pulsing | |
| 20 | Keyboard | Tab through the page | Every link and control reachable, focus visible | |
| 21 | No JavaScript | Disable JavaScript | Saved stories, shows and favourites still shown | |
| 22 | HTML validation | validator.w3.org | No errors | |
| 23 | Lighthouse | DevTools > Lighthouse | Accessibility 95+ | |

## Screenshots to take

1. Front page at 1440px (4 columns), live
2. Front page at 1024px and 768px
3. Phone, 375px, including the swipeable Trending row
4. DevTools Grid overlay on `.front`
5. Airing this week, with a day selected
6. Season chart and Coming soon
7. Fan favourites
8. Show page (FMA:B or Demon Slayer)
9. Dark theme
10. The terminal running `server.py`, and `/api/news` open in the browser (raw JSON)
11. Without the server (status line message)
12. Lighthouse and validator results

## Demo script (about 3 minutes)

1. In a terminal, run `python server.py`. Explain that it fetches the news feeds the browser isn't allowed to read (CORS).
2. Show the live status, today's real headlines (open one), and the next big episode countdown.
3. **Resize from wide to phone** to show the 4, 3, 2 and 1 column layouts, with the Grid overlay turned on.
4. Show "Airing this week": switch days with the arrow keys.
5. Swipe the Trending row on phone width, then show the season chart.
6. Show **Fan favourites**, with FMA:B and Demon Slayer's live all-time rankings, then open a show page with its characters and trailer.
7. Stop the server and reload. The shows are still live and the news is saved, and the status line explains it.

## Known limitations

| Limitation | Possible improvement |
| --- | --- |
| Live news needs `server.py` running | Host the server online (for example on a free cloud service) |
| Only headlines and summaries | A real news site would write its own articles |
| The schedule covers only the 50 most popular airing shows | Page through AniList's full schedule |
| Related news uses simple title matching | Tag stories with show IDs |
| The saved snapshot gets older | Rebuild it regularly |
