# CineScope

Movie and TV discovery app backed by [TMDB](https://www.themoviedb.org). Spring Boot backend (`backend/`) + React
frontend (`frontend/`).

```
React (Vite) --/api--> Controller -> Service -> TMDB gateway --HTTPS--> api.themoviedb.org
                         (DTOs)     (rules)     (cache, token)
```

The backend keeps your TMDB token on the server (the browser never sees it), caches TMDB answers, and turns TMDB's
inconsistent JSON into small uniform responses. India (`IN`) is the default region for "in cinemas", "coming soon" and
age ratings.

## What works now (all five phases)

- **Home:** hero banner with the top trending title (watch its trailer without leaving the page) and rows for trending,
  popular, in cinemas, top rated, coming soon and TV, each with "See all".
- **Title pages:** movies and TV shows with score, genres, runtime, age rating (India, falling back to US), overview,
  director / creators, trailers and teasers (YouTube), facts, top cast and similar titles.
- **Browse:** filter by genre, year and minimum rating, sort by popularity / rating / newest, load more. Filters live in
  the URL, so a filtered view can be bookmarked.
- **Search:** movies, TV shows and people in one box, with tabs and counts.
- **People:** click any cast member, director or search result for their page: photo, born / died / age, biography
  (expandable), best-known titles and the full filmography split into Acting and Crew, newest first, each line linking
  to the title.
- **Where to watch:** every title page lists the services that offer it in India (or any of ten countries you pick):
  stream (subscription), free, rent and buy, with logos and a link to TMDB for more options. Data is provided by JustWatch.
- **What to watch tonight:** pick a movie or show, a mood, how much time you have, a language and whether it must stream
  in India, and get one pick (with why, runtime, genres and where to watch it) plus backups. "Show me another" draws
  again. Signed-in users never get what they have watched and are steered to their want-to-watch list.
- **Accounts:** register, sign in / out (JWT, BCrypt-hashed passwords, brute-force lockout). Passwords must be strong (8-72
  characters with upper and lower case, a digit and a symbol; not a common password; not containing your username or email),
  with a live checklist on the sign-up form. Browsing stays public.
- **Watchlist:** on any title page set *Want to watch / Watching / Watched*, a 1-10 rating (only once watched) and a
  note. `/watchlist` lists your titles with status tabs and counts, sorting, paging and removal. Every user sees only
  their own list.
- Light and dark theme (dark by default), phone layout, loading skeletons, friendly error states.
- Swagger UI for the API (with a login button for the protected endpoints).


- **Reviews and discussion** on every title: star rating, replies, helpful votes, a visible five-stage processing
  pipeline (clean, spam check, profanity filter, sentiment analysis, decision), a summary (histogram, CineScope score,
  mood, topics) and an admin moderation queue. Demo users and comments are seeded when `app.seed.demo-data=true`.
- Sign-in first, 36 film-quote sign-in screens, a hero carousel, profile and admin account pages; passwords are 8-20
  characters with a progressive checklist.
- **Guess the line quiz** (Play in the header): ten famous lines per round, from 353 quotes in four categories (Hollywood, TV,
  Bollywood, Anime, or a mix). Name the film or show, or who says it. Answers are checked on the server, a streak earns bonus
  points, there is a leaderboard per category, and a player never sees the same quote again until the whole category is used up.
- Verified 25 Nov 2026: 444 backend tests and 254 browser tests passing.

## Run it

Needs JDK 17, Node 20+ and MySQL 8.

0. **Database (once).** Create an empty schema: `CREATE DATABASE cinescope;`. The backend creates its tables itself
   (Flyway migrations) and connects as `root`. There is **no default password**: set `DB_PASSWORD` (and optionally `DB_URL`, `DB_USERNAME`) as environment variables or in the git-ignored `backend/config/application.properties`.
1. **TMDB token.** Copy `backend/config/application.properties.example` to `backend/config/application.properties`
   and paste your TMDB *API Read Access Token* (get one free at https://www.themoviedb.org/settings/api). That file is
   git-ignored. Alternatively set the `TMDB_READ_TOKEN` environment variable.
   In the same file (or as environment variables) also set `app.jwt.secret` / `JWT_SECRET` (32+ random characters; there is no default, so the
   backend refuses to start without it) and `spring.datasource.password` / `DB_PASSWORD`. The `.example` file shows how to generate a secret.
2. Start the backend (http://localhost:8095):
   ```powershell
   cd backend
   .\mvnw spring-boot:run
   ```
3. Start the frontend (http://localhost:5180):
   ```powershell
   cd frontend
   npm install      # first time only
   npm run dev
   ```

API documentation: http://localhost:8095/swagger-ui.html (OpenAPI JSON at `/v3/api-docs`).

## API

| Endpoint | Purpose |
|---|---|
| `GET /api/home` | hero title + all home rows in one request |
| `GET /api/titles?category=&type=&page=` | a full list: `trending`, `popular`, `top_rated`, `now_playing`, `upcoming`, `on_the_air` |
| `GET /api/discover?type=&genre=&year=&minRating=&sort=&page=` | filtered browsing (`sort`: popularity, rating, newest) |
| `GET /api/search?query=&page=` | movies, shows and people |
| `GET /api/tonight?type=&mood=&maxMinutes=&language=&streamingOnly=&seed=` | one pick + alternatives for your wishes (public; personalised with a login) |
| `GET /api/people/{id}` | a person with biography, best-known titles and filmography (`acting`, `crew`) |
| `GET /api/genres?type=` | genre list |
| `GET /api/titles/{type}/{id}` | one movie / show with cast, trailers, similar titles |
| `GET /api/titles/{type}/{id}/providers?region=` | where to watch: `stream`, `free`, `rent`, `buy` (default region India) |
| `POST /api/auth/register`, `POST /api/auth/login` | create an account / sign in; both answer with a bearer token |
| `GET /api/auth/me` | the signed-in user's profile |
| `GET /api/watchlist?status=&sort=&page=` | my list (`status`: WANT, WATCHING, WATCHED; `sort`: added, updated, title, rating) |
| `GET /api/watchlist/summary` | counts per status |
| `GET` / `PUT` / `DELETE /api/watchlist/{type}/{id}` | read, add-or-change (`status`, `rating`, `note`), or remove one entry |
| `GET /api/meta` | which application and database kind is running (used by the test guard) |

The catalogue endpoints above the line `POST /api/auth/...` are public; everything else needs
`Authorization: Bearer <token>` (deny by default).
Errors share one shape: `status`, `error`, `message`, `timestamp`. Bad input answers 400 (without calling TMDB), an unknown
title 404, a missing or invalid login 401, a taken username 409, too many failed logins 429, TMDB rate limiting 503, TMDB trouble 502 and a TMDB timeout 504.

## Tests

| Suite | Command | Notes |
|---|---|---|
| Backend (Mockito + MockMvc, 444 tests) | `cd backend; .\mvnw test` | offline: TMDB is replaced by canned answers, the database is in-memory H2 |
| Browser (Playwright, 254 tests) | `cd frontend; npm run e2e` | starts a **fake TMDB** (8097), the real backend (8096) on in-memory H2, and the frontend (5181) |

The browser tests never contact the real TMDB and never use your real token: `frontend/e2e/global-setup.ts` aborts the
run unless the backend is talking to the fake TMDB with the fake token **and** is using the in-memory H2 database (never your MySQL data). They use the installed Microsoft Edge
(`PW_CHANNEL=chromium` after `npx playwright install chromium` for Chromium); `PW_REUSE=1` reuses running servers.

## Where things are

- `backend/src/main/java/com/csb/cinescope/` - `controller/`, `service/`, `tmdb/` (the only code that talks to TMDB),
  `mapper/` (TMDB JSON to our records), `dto/`, `entity/` + `repository/` (JPA), `security/` (JWT), `exception/`, `config/`;
  database tables are in `src/main/resources/db/migration/`. Every class starts with a comment describing its
  role and how it connects to the others.
- `frontend/src/` - `pages/`, `components/`, `auth/` (session + route guard), `api/` (client, endpoints, React Query hooks), `lib/`.
- `frontend/e2e/` - browser tests and the fake TMDB (`tmdb-stub.mjs`).

## Attribution

This product uses the TMDB API but is not endorsed or certified by TMDB. Movie and TV data and images are provided by TMDB.
The app shows the TMDB logo and this notice in its footer, as TMDB's terms require. TMDB's free API is for non-commercial use.
