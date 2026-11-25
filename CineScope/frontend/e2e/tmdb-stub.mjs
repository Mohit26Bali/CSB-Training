/**
 * A fake TMDB for the browser tests: a tiny HTTP server (no dependencies) that answers the TMDB v3 endpoints CineScope
 * uses, with invented films and shows, so the tests are fast, offline and deterministic.
 *
 * How it fits in: playwright.config.ts starts it on port 8097 and starts the REAL Spring Boot backend with
 * TMDB_BASE_URL=http://localhost:8097/3, so the backend's HTTP client, JSON mapping, caching and error handling are all
 * exercised for real; only the far end is fake.
 *
 * Catalogue: 24 movies (ids 1001-1024) and 16 shows (ids 2001-2016) with fixed genres, years and ratings, plus a few
 * people. Lists return 8 items per page (TMDB uses 20) so that "Load more" can be tested with a small catalogue.
 * Special titles for failure tests: movie 666 -> HTTP 500, movie 667 -> HTTP 429, movie 404404 -> 404 (anything unknown).
 * Control endpoints: GET /__health, GET /__stats (request counts and the last Authorization header), GET /__reset.
 * Every /3 request must carry "Authorization: Bearer e2e-token", exactly like the real API demands a token.
 */
import { readFileSync } from 'node:fs'
import http from 'node:http'

const PORT = 8097
const TOKEN = 'e2e-token'
const PAGE_SIZE = 8

const MOVIE_GENRES = [
  { id: 28, name: 'Action' },
  { id: 18, name: 'Drama' },
  { id: 35, name: 'Comedy' },
  { id: 878, name: 'Science Fiction' },
  { id: 27, name: 'Horror' },
]
const TV_GENRES = [
  { id: 18, name: 'Drama' },
  { id: 35, name: 'Comedy' },
  { id: 10759, name: 'Action & Adventure' },
]

const MOVIE_NAMES = [
  'The Last Lighthouse', 'Midnight Orchard', 'Paper Moons', 'Iron Meridian', 'Quiet Harbour', 'Velvet Static',
  'The Glass Cartographer', 'Neon Savannah', 'Hollow Crown Road', 'Salt and Ember', 'Thirteen Winters', 'The Copper Sky',
  'Echo Chamber Blues', 'Wild Signal', 'Northbound Ghosts', 'The Tin Orchestra', 'Silver Thread', 'Fault Line Summer',
  'Lantern District', 'The Borrowed Hour', 'Static Bloom', 'Marrow Lake', 'Atlas of Small Things', 'Daybreak Protocol',
]
const TV_NAMES = [
  'Harbor Lights', 'The Long Table', 'Signal Fires', 'Paper Kingdoms', 'Low Tide', 'Cinder Court', 'Night Shift Radio',
  'The Quiet Orchard', 'Open Circuit', 'Brass Hearts', 'Glasshouse', 'Winter Cartel', 'Pale Meridian', 'Second Sunrise',
  'Tidewater', 'Kindling',
]
const ACTORS = [
  ['Ananya Rao', 'Lead'], ['Marcus Hale', 'The Stranger'], ['Priya Menon', 'Dr. Iyer'], ['Jonas Weber', 'Captain Voss'], ['Lena Park', 'Mina'],
]
const PEOPLE = [
  { id: 3001, name: 'Mara Whitfield', profile_path: '/person-3001.jpg', known_for: [{ title: 'The Last Lighthouse' }, { title: 'Paper Moons' }] },
  { id: 3002, name: 'Theo Marsh', profile_path: null, known_for: [{ title: 'Iron Meridian' }] },
]

const pad = (n) => String(n).padStart(2, '0')
const movies = MOVIE_NAMES.map((title, i) => ({
  id: 1001 + i,
  title,
  overview: `The story of ${title}: a quiet town, a loud secret and one very long night.`,
  poster_path: `/poster-${1001 + i}.jpg`,
  backdrop_path: `/backdrop-${1001 + i}.jpg`,
  release_date: `${2019 + (i % 8)}-${pad(1 + (i % 9))}-15`,
  vote_average: Math.round((5 + ((i * 7) % 41) / 10) * 10) / 10,
  vote_count: 100 + i * 50,
  popularity: 1000 - i * 7,
  genre: MOVIE_GENRES[i % MOVIE_GENRES.length],
}))
// The franchise tracker's fake series: three released films (the first three movies) and one not out yet (id 1090, added in the route).
const SAGA_ID = 9001
const SAGA = [1001, 1002, 1003]
const shows = TV_NAMES.map((name, i) => ({
  id: 2001 + i,
  name,
  overview: `${name} follows a family through five unforgettable seasons.`,
  poster_path: `/poster-${2001 + i}.jpg`,
  backdrop_path: `/backdrop-${2001 + i}.jpg`,
  first_air_date: `${2015 + (i % 11)}-${pad(1 + (i % 9))}-10`,
  vote_average: Math.round((5.5 + ((i * 11) % 36) / 10) * 10) / 10,
  vote_count: 80 + i * 40,
  popularity: 990 - i * 11,
  genre: TV_GENRES[i % TV_GENRES.length],
}))

// ---- shaping ----
const asMovie = (m) => ({ id: m.id, title: m.title, overview: m.overview, poster_path: m.poster_path, backdrop_path: m.backdrop_path, release_date: m.release_date, vote_average: m.vote_average, genre_ids: [m.genre.id] })
const asShow = (s) => ({ id: s.id, name: s.name, overview: s.overview, poster_path: s.poster_path, backdrop_path: s.backdrop_path, first_air_date: s.first_air_date, vote_average: s.vote_average, genre_ids: [s.genre.id] })
const withType = (item, type) => ({ ...item, media_type: type })

function page(items, pageNo) {
  const total = items.length
  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE))
  const p = Math.min(Math.max(1, pageNo), totalPages)
  return { page: p, total_pages: totalPages, total_results: total, results: items.slice((p - 1) * PAGE_SIZE, p * PAGE_SIZE) }
}

const byPopularity = (list) => [...list].sort((a, b) => b.popularity - a.popularity)
const byRating = (list) => [...list].sort((a, b) => b.vote_average - a.vote_average)

function listFor(type, category) {
  const base = type === 'movie' ? movies : shows
  const shape = type === 'movie' ? asMovie : asShow
  let chosen = base
  if (category === 'now_playing' || category === 'on_the_air') chosen = base.filter((_, i) => i % 2 === 0)
  if (category === 'upcoming') chosen = base.filter((_, i) => i % 2 === 1)
  const sorted = category === 'top_rated' ? byRating(chosen) : byPopularity(chosen)
  return sorted.map(shape)
}

function trending() {
  const mixed = byPopularity([...movies.map((m) => ({ ...m, kind: 'movie' })), ...shows.map((s) => ({ ...s, kind: 'tv' }))])
  return mixed.map((x) => (x.kind === 'movie' ? withType(asMovie(x), 'movie') : withType(asShow(x), 'tv')))
}

function discover(type, q) {
  const base = type === 'movie' ? movies : shows
  const shape = type === 'movie' ? asMovie : asShow
  const dateField = type === 'movie' ? 'release_date' : 'first_air_date'
  // with_genres: one id (browse) or several joined by "|" / "," (the tonight recommender's moods): any of them matches
  const wanted = (q.get('with_genres') ?? '').split(/[|,]/).filter(Boolean).map(Number)
  let items = base.filter((x) => (wanted.length ? wanted.includes(x.genre.id) : true))
  // the recommender's other wishes (see service/RecommendationService): the position of an id in its series decides
  const position = (x) => (x.id % 1000) - 1
  const maxMinutes = q.get('with_runtime.lte')
  if (maxMinutes && type === 'movie') items = items.filter((x) => 90 + position(x) <= Number(maxMinutes)) // runtime = 90 + position, as in detail()
  const language = q.get('with_original_language')
  if (language) items = items.filter((x) => (position(x) % 3 === 0 ? 'hi' : 'en') === language) // as in detail()
  if (q.get('watch_region') === 'IN') items = items.filter((x) => { const india = providersFor(x.id).IN; return !!india && !!(india.flatrate || india.free || india.ads) })
  const year = q.get(type === 'movie' ? 'primary_release_year' : 'first_air_date_year')
  if (year) items = items.filter((x) => x[dateField].startsWith(year))
  if (q.get('vote_average.gte')) items = items.filter((x) => x.vote_average >= Number(q.get('vote_average.gte')))
  const sort = q.get('sort_by') ?? 'popularity.desc'
  if (sort.startsWith('vote_average')) items = byRating(items)
  else if (sort.includes('date')) items = [...items].sort((a, b) => b[dateField].localeCompare(a[dateField]))
  else items = byPopularity(items)
  return items.map(shape)
}

function search(text) {
  const q = text.toLowerCase()
  const titles = [
    ...movies.filter((m) => m.title.toLowerCase().includes(q)).map((m) => withType(asMovie(m), 'movie')),
    ...shows.filter((s) => s.name.toLowerCase().includes(q)).map((s) => withType(asShow(s), 'tv')),
  ]
  const people = PEOPLE.filter((p) => p.name.toLowerCase().includes(q)).map((p) => ({ ...p, media_type: 'person' }))
  // like TMDB: a person whose name starts with the text is the best hit and comes first
  const personFirst = people.some((p) => p.name.toLowerCase().startsWith(q))
  return personFirst ? [...people, ...titles] : [...titles, ...people]
}

function detail(type, id) {
  const base = type === 'movie' ? movies : shows
  const index = base.findIndex((x) => x.id === id)
  if (index < 0) return null
  const item = base[index]
  const recommendations = [1, 2, 3].map((k) => base[(index + k) % base.length]).map(type === 'movie' ? asMovie : asShow)
  const common = {
    id: item.id,
    overview: item.overview,
    poster_path: item.poster_path,
    backdrop_path: item.backdrop_path,
    vote_average: item.vote_average,
    vote_count: item.vote_count,
    status: 'Released',
    original_language: index % 3 === 0 ? 'hi' : 'en',
    homepage: index % 2 === 0 ? `https://example.test/${item.id}` : '',
    genres: [item.genre],
    credits: {
      cast: ACTORS.map(([name, character], k) => ({ id: 5000 + k, name, character, profile_path: k === 1 ? null : `/actor-${k}.jpg` })),
      crew: [{ id: 6001, name: 'Ishaan Verma', job: 'Director' }, { id: 6002, name: 'Someone Else', job: 'Producer' }],
    },
    videos: {
      results: [
        { key: `trailer${item.id}`, name: `${item.title ?? item.name} - Official Trailer`, site: 'YouTube', type: 'Trailer', official: true },
        { key: `teaser${item.id}`, name: 'Teaser', site: 'YouTube', type: 'Teaser', official: false },
      ],
    },
    recommendations: { results: recommendations },
    similar: { results: [] },
  }
  if (type === 'movie') {
    return {
      ...common,
      title: item.title,
      tagline: `Tagline of ${item.title}`,
      release_date: item.release_date,
      runtime: 90 + index,
      budget: 1_000_000 * (index + 1),
      revenue: 2_500_000 * (index + 1),
      // every fifth film (1005, 1010 ...) is rated U in India, so "safe for the whole family" has something to find; the rest are UA / PG-13
      release_dates: { results: [{ iso_3166_1: 'IN', release_dates: [{ certification: '' }, { certification: item.id % 5 === 0 ? 'U' : 'UA' }] }, { iso_3166_1: 'US', release_dates: [{ certification: item.id % 5 === 0 ? 'G' : 'PG-13' }] }] },
    }
  }
  return {
    ...common,
    name: item.name,
    tagline: '',
    first_air_date: item.first_air_date,
    episode_run_time: [42],
    number_of_seasons: (index % 5) + 1,
    seasons: [{ season_number: 0, name: 'Specials', episode_count: 2 }, ...Array.from({ length: (index % 5) + 1 }, (_, k) => ({ season_number: k + 1, name: `Season ${k + 1}`, episode_count: 8 }))],
    number_of_episodes: ((index % 5) + 1) * 8,
    created_by: [{ id: 6100, name: 'Rhea Kapoor' }],
    content_ratings: { results: [{ iso_3166_1: 'US', rating: item.id % 5 === 0 ? 'TV-Y7' : 'TV-14' }] }, // shows 2005, 2010, 2015 are for children
  }
}

// Episodes of a season, for the episode-rating heatmap: eight per season, with ratings from 6.5 to 8.4 that follow a fixed pattern.
// Show 2003 (three seasons) has the memorable ones the tests look for: S1E3 "The Turn" 9.6 (best), S2E4 "The Dud" 4.2 (worst),
// S2E6 with no votes at all (not rated), and S3E8 not aired until 2999. Specials (season 0) are never asked for by the app.
function seasonEpisodes(showId, season) {
  return Array.from({ length: 8 }, (_, k) => {
    const episode = k + 1
    let name = `Episode ${episode}`
    let rating = Math.round((6.5 + ((season * 7 + episode * 3) % 20) / 10) * 10) / 10
    let votes = 40 + episode * 10
    let airDate = `${2018 + season}-0${episode}-01`
    if (showId === 2003) {
      if (season === 1 && episode === 3) [name, rating, votes] = ['The Turn', 9.6, 500]
      if (season === 2 && episode === 4) [name, rating, votes] = ['The Dud', 4.2, 300]
      if (season === 2 && episode === 6) [rating, votes] = [0, 0]
      if (season === 3 && episode === 8) [rating, votes, airDate] = [0, 0, '2999-01-01']
    }
    return { id: showId * 100 + season * 10 + episode, episode_number: episode, season_number: season, name, vote_average: rating, vote_count: votes, air_date: airDate }
  })
}

// ---- server ----
const counts = {}
let lastAuth = null

function send(res, status, body) {
  res.writeHead(status, { 'Content-Type': 'application/json' })
  res.end(JSON.stringify(body))
}


// ---- where to watch: /{movie|tv}/{id}/watch/providers ----
// Availability depends on the position of the id in its series (movies start at 1001, shows at 2001), so tests can
// reach every case: k = (id % 1000 - 1) % 3
//   k = 0  India has all four groups (and the US has a subscription service): 1001, 1004, 2001 ...
//   k = 1  only the US has data, so India shows the "nothing listed" message: 1002, 1005, 2002 ...
//   k = 2  India has rent only: 1003, 1006, 2003 ...
// Movie 1006 is special: its detail page works but this lookup fails (a failure that must not break the page).
// Movies 666 / 667 fail everywhere (see the detail route).
const prov = (id, name, priority) => ({ provider_id: id, provider_name: name, logo_path: `/logo-${id}.jpg`, display_priority: priority })
function providersFor(id) {
  const link = (cc) => `https://www.themoviedb.org/title/${id}/watch?locale=${cc}`
  const k = ((id % 1000) - 1) % 3
  if (k === 0) {
    return {
      IN: {
        link: link('IN'),
        flatrate: [prov(122, 'JioHotstar', 5), prov(8, 'Netflix', 1)],
        free: [prov(301, 'MX Player', 1)],
        ads: [prov(301, 'MX Player', 1), prov(300, 'Pluto', 2)],
        rent: [prov(2, 'Apple TV', 3)],
        buy: [prov(3, 'Google Play Movies', 4)],
      },
      US: { link: link('US'), flatrate: [prov(15, 'Hulu', 1)] },
    }
  }
  if (k === 1) return { US: { link: link('US'), flatrate: [prov(15, 'Hulu', 1)] } }
  return { IN: { link: link('IN'), rent: [prov(2, 'Apple TV', 3)] } }
}


// ---- people: /person/{id}?append_to_response=combined_credits ----
// 3001 Mara Whitfield: full profile (long biography, 40 acting credits so "Show all" is needed, crew credits, a talk-show
//      appearance that must be hidden); 3002 Theo Marsh: nearly empty (no photo, biography, birthday or credits);
// 3003: died, to check the "Died" line; 5000-5004 (cast of every title) and 6001 / 6100 (directors / creators): simple profiles.
// Any other id: 404.
const BIO = Array.from({ length: 14 }, (_, i) => `Paragraph ${i + 1}: Mara grew up beside a lighthouse and learned to act by reading to the sea.`).join('\n\n')
const credit = (n, extra = {}) => ({
  id: 1000 + n,
  media_type: 'movie',
  title: movies[n % movies.length].title,
  poster_path: `/poster-${1001 + (n % movies.length)}.jpg`,
  release_date: `${2024 - n}-06-01`,
  vote_average: 7,
  vote_count: 1000 - n,
  genre_ids: [],
  ...extra,
})
function person(id) {
  if (id === 3001) {
    return {
      id, name: 'Mara Whitfield', biography: BIO, birthday: '1975-04-12', deathday: null, place_of_birth: 'Leeds, England',
      known_for_department: 'Acting', profile_path: '/person-3001.jpg', homepage: '',
      combined_credits: {
        cast: [
          ...Array.from({ length: 40 }, (_, n) => credit(n, { character: `Role ${n + 1}` })),
          { id: 9999, media_type: 'tv', name: 'Late Night With Guests', first_air_date: '2023-01-01', popularity: 999, genre_ids: [10767], character: 'Self' },
        ],
        crew: [credit(0, { job: 'Director' }), credit(0, { job: 'Writer' }), credit(3, { job: 'Producer' })],
      },
    }
  }
  if (id === 3002) return { id, name: 'Theo Marsh', biography: '', birthday: null, deathday: null, place_of_birth: null, known_for_department: 'Directing', profile_path: null, combined_credits: { cast: [], crew: [] } }
  if (id === 3003) return { id, name: 'Old Timer', biography: 'A veteran.', birthday: '1930-02-01', deathday: '2005-02-01', place_of_birth: 'Pune, India', known_for_department: 'Acting', profile_path: null, combined_credits: { cast: [credit(1, { character: 'Grandfather' })], crew: [] } }
  const actor = ACTORS[id - 5000]
  if (actor) return { id, name: actor[0], biography: `${actor[0]} is an actor.`, birthday: '1985-01-01', deathday: null, place_of_birth: 'Mumbai, India', known_for_department: 'Acting', profile_path: id === 5001 ? null : `/actor-${id - 5000}.jpg`, combined_credits: { cast: [credit(2, { character: actor[1] })], crew: [] } }
  if (id === 6001) return { id, name: 'Ishaan Verma', biography: 'A director.', birthday: '1970-09-09', deathday: null, place_of_birth: 'Delhi, India', known_for_department: 'Directing', profile_path: null, combined_credits: { cast: [], crew: [credit(4, { job: 'Director' })] } }
  if (id === 6100) return { id, name: 'Rhea Kapoor', biography: 'A creator.', birthday: null, deathday: null, place_of_birth: null, known_for_department: 'Writing', profile_path: null, combined_credits: { cast: [], crew: [credit(5, { job: 'Creator' })] } }
  return null
}


// ---- the sign-in carousel (backend ShowcaseService): every title that has a famous line ----
// Read from the same seed file the backend uses (backend/src/main/resources/seed/quotes.psv), so the stub knows every film and show
// the carousel can ask for. Real TMDB ids and titles; the stub gives each a backdrop and a poster. Deewaar (movie 22460) always
// fails, to show that one missing title is simply left out of the carousel. Keyed "type:id".
const CLASSICS = {}
for (const row of readFileSync(new URL('../../backend/src/main/resources/seed/quotes.psv', import.meta.url), 'utf8').split(/\r?\n/)) {
  if (!row.trim() || row.startsWith('#')) continue
  const [, type, id, title, year] = row.trim().split('|')
  CLASSICS[`${type}:${id}`] = [title, Number(year)]
}

http
  .createServer((req, res) => {
    const url = new URL(req.url ?? '/', `http://localhost:${PORT}`)
    const path = url.pathname

    if (path === '/__health') return send(res, 200, { ok: true })
    if (path === '/__stats') return send(res, 200, { counts, lastAuth })
    if (path === '/__reset') {
      for (const key of Object.keys(counts)) delete counts[key]
      return send(res, 200, { ok: true })
    }
    if (!path.startsWith('/3/')) return send(res, 404, { status_message: 'Not found' })

    lastAuth = req.headers.authorization ?? null
    counts[path] = (counts[path] ?? 0) + 1
    if (lastAuth !== `Bearer ${TOKEN}`) return send(res, 401, { status_message: 'Invalid API key' })

    const q = url.searchParams
    const pageNo = Number(q.get('page') ?? 1)
    const route = path.slice(2) // "/trending/all/week"

    let m
    // "today" keeps the week's top title first but orders the next ones differently and drops the last four,
    // as the real daily and weekly lists differ, so the two home rows are not the same row twice
    const trendingFor = (period) => {
      const week = trending()
      return period === 'day' ? [week[0], week[5], week[4], week[3], week[2], week[1], ...week.slice(6, -4)] : week
    }
    if ((m = route.match(/^\/trending\/all\/(week|day)$/))) return send(res, 200, page(trendingFor(m[1]), pageNo))
    if ((m = route.match(/^\/trending\/(movie|tv)\/(week|day)$/))) return send(res, 200, page(trendingFor(m[2]).filter((x) => x.media_type === m[1]), pageNo))
    if ((m = route.match(/^\/(movie|tv)\/(popular|top_rated|now_playing|upcoming|on_the_air)$/))) return send(res, 200, page(listFor(m[1], m[2]), pageNo))
    if ((m = route.match(/^\/discover\/(movie|tv)$/))) return send(res, 200, page(discover(m[1], q), pageNo))
    if (route === '/search/multi') return send(res, 200, page(search(q.get('query') ?? ''), pageNo))
    if ((m = route.match(/^\/genre\/(movie|tv)\/list$/))) return send(res, 200, { genres: m[1] === 'movie' ? MOVIE_GENRES : TV_GENRES })
    if ((m = route.match(/^\/person\/(\d+)$/))) {
      const data = person(Number(m[1]))
      return data ? send(res, 200, data) : send(res, 404, { status_message: 'The resource you requested could not be found.' })
    }
    if ((m = route.match(/^\/(movie|tv)\/(\d+)\/watch\/providers$/))) {
      const id = Number(m[2])
      if (m[1] === 'movie' && (id === 666 || id === 1006)) return send(res, 500, { status_message: 'Internal error' })
      if (m[1] === 'movie' && id === 667) return send(res, 429, { status_message: 'Too many requests' })
      return detail(m[1], id)
        ? send(res, 200, { id, results: providersFor(id) })
        : send(res, 404, { status_message: 'The resource you requested could not be found.' })
    }
    if ((m = route.match(/^\/collection\/(\d+)$/))) {
      // one series of three released films and one that is not out yet, for the franchise tracker
      if (Number(m[1]) !== SAGA_ID) return send(res, 404, { status_message: 'The resource you requested could not be found.' })
      const parts = [...SAGA.map((id) => movies.find((x) => x.id === id)).map(asMovie), { id: 1090, title: 'Harbor Saga: Forever', release_date: '2999-01-01', poster_path: '/poster-1090.jpg' }]
      return send(res, 200, { id: SAGA_ID, name: 'Harbor Saga Collection', overview: 'A saga.', poster_path: '/saga.jpg', parts })
    }
    if ((m = route.match(/^\/tv\/(\d+)\/season\/(\d+)$/))) {
      const id = Number(m[1])
      const season = Number(m[2])
      const data = detail('tv', id)
      if (!data || season < 1 || season > data.number_of_seasons) return send(res, 404, { status_message: 'The resource you requested could not be found.' })
      return send(res, 200, { id: id * 100 + season, season_number: season, name: `Season ${season}`, episodes: seasonEpisodes(id, season) })
    }
    if ((m = route.match(/^\/(movie|tv)\/(\d+)$/))) {
      const id = Number(m[2])
      if (CLASSICS[`${m[1]}:${id}`]) {
        if (m[1] === 'movie' && id === 22460) return send(res, 500, { status_message: 'Internal error' })
        const [title, year] = CLASSICS[`${m[1]}:${id}`]
        const named = m[1] === 'tv' ? { name: title, first_air_date: `${year}-06-01` } : { title, release_date: `${year}-06-01` }
        // For the cast and poster games: five actors named "Actor {type}-{id} {n}" and a poster at /classic-poster-{type}-{id}.jpg, so a test can
        // tell which title a clue belongs to. Every seventh id has neither, so the games must skip those titles.
        const bare = id % 7 === 0
        const cast = bare ? [] : [1, 2, 3, 4, 5].map((n) => ({ id: n, name: `Actor ${m[1]}-${id} ${n}`, character: 'Someone', profile_path: null }))
        return send(res, 200, {
          id, ...named, overview: `${title}, a classic.`, poster_path: bare ? null : `/classic-poster-${m[1]}-${id}.jpg`, backdrop_path: `/classic-backdrop-${id}.jpg`,
          vote_average: 8.2, genres: [], credits: { cast, crew: [] },
        })
      }
      if (m[1] === 'movie' && id === 666) return send(res, 500, { status_message: 'Internal error' })
      if (m[1] === 'movie' && id === 667) return send(res, 429, { status_message: 'Too many requests' })
      const data = detail(m[1], id)
      if (data && m[1] === 'movie' && SAGA.includes(id)) data.belongs_to_collection = { id: SAGA_ID, name: 'Harbor Saga Collection', poster_path: '/saga.jpg' }
      return data ? send(res, 200, data) : send(res, 404, { status_message: 'The resource you requested could not be found.' })
    }
    return send(res, 404, { status_message: 'Not found' })
  })
  .listen(PORT, () => console.log(`fake TMDB listening on ${PORT}`))
