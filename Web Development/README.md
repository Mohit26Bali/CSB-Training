# Web Development

Fifteen front-end projects (plus an anime variant of project 8) built with plain HTML, CSS and JavaScript: no frameworks and no build step. Each folder has its own README with what it demonstrates, how it is built and how it was checked.

| # | Project | What it shows |
|---|---|---|
| 01 | [Professional Resume Page](01-Professional-Resume-Page) | Semantic HTML5, print-friendly CSS |
| 02 | [Company Landing Page](02-Company-Landing-Page) | Flexbox layout, one-page navigation |
| 03 | [Product Catalog UI](03-Product-Catalog-UI) | Cards, transitions, filtering and sorting |
| 04 | [Restaurant Website](04-Restaurant-Website) | Menu, gallery, reservation form |
| 05 | [Employee Directory](05-Employee-Directory) | Search and filters over 50 people, list/grid views |
| 06 | [Registration Form](06-Registration-Form) | HTML5 validation, error summary, password strength |
| 07 | [Event Management Portal](07-Event-Management-Portal) | Timetable, seat and ticket booking |
| 08 | [News Portal Layout (space)](08-News-Portal-Layout), [anime version](08-News-Portal-Layout-Anime) | CSS Grid newspaper layout with live data (NASA, spaceflight news; AniList and RSS feeds) |
| 09 | [Travel Agency Website](09-Travel-Agency-Website) | Live local time and weather, filterable packages |
| 10 | [Dashboard Interface](10-Dashboard-Interface) | Admin dashboard, charts, tables |
| 11 | [Portfolio Website](11-Portfolio-Website) | A portfolio of this course's projects |
| 12 | [E-Commerce Product Page](12-E-Commerce-Product-Page) | A home page with hero and catalogue, one detail page per product, reviews |
| 13 | [Blog Platform UI](13-Blog-Platform-UI) | Home page, ten story pages and four writer profiles |
| 14 | [Corporate Website Redesign](14-Corporate-Website-Redesign) | Before and after, with a comparison page |
| 15 | [Mini Capstone Project](15-Mini-Capstone-Project) | A complete one-page site bringing it all together |

## Running them

Open any project's `index.html` in a browser, or serve the whole folder so that every page behaves as it would on a website:

```bash
py -m http.server 8000        # Windows;  python3 -m http.server 8000 elsewhere
# then open http://localhost:8000/05-Employee-Directory/
```

The anime news site (08) also has its own `server.py` that fetches live news feeds (it listens on localhost only); without it the page shows saved headlines.

## How they were checked

All 30 pages were opened in a real browser (Microsoft Edge) at desktop and phone widths: no JavaScript errors, no missing local files or images, and no sideways scrolling on a 375 px screen. That check found and fixed two phone-layout bugs (a swipeable row in the Employee Directory and the navigation in the Event portal widened the page).

## Notes

- Company names, people, e-mail addresses (`*.example`) and prices are invented. Placeholders such as "Your Name" and `your-username` in the resume and portfolio are meant to be replaced.
- Photos come from Pexels (free to use) or NASA; projects with photos list the photographers in `images/CREDITS.md` or in their README.
- Some pages call free public services from the browser (Google Fonts, Open-Meteo, AniList, NASA and others), so they need an internet connection to show live data and fall back to saved data where noted in each README.
