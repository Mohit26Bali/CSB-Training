// Panel & Pixel - shared code: live data (AniList and the news server), saved snapshot, formatting, theme, countdowns

const PLACEHOLDER = 'images/placeholder.svg';
const SNAPSHOT = window.PANEL_SNAPSHOT;

// cached fetch: a copy is kept in sessionStorage so refreshes stay inside the rate limits
function readCache(key, maxAgeMinutes) {
    try {
        const saved = JSON.parse(sessionStorage.getItem(key));
        if (saved && Date.now() - saved.time < maxAgeMinutes * 60000) {
            return saved.data;
        }
    } catch (error) {
        // storage blocked; fetch instead
    }
    return null;
}

function writeCache(key, data) {
    try {
        sessionStorage.setItem(key, JSON.stringify({ time: Date.now(), data }));
    } catch (error) {
        // storage full or blocked; the data is still used
    }
}

async function fetchJSON(url, options, cacheKey, maxAgeMinutes) {
    const cached = readCache(cacheKey, maxAgeMinutes);
    if (cached) {
        return cached;
    }
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 12000);
    try {
        const response = await fetch(url, { ...options, signal: controller.signal });
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }
        const data = await response.json();
        if (data.errors) {
            throw new Error(data.errors[0].message);
        }
        writeCache(cacheKey, data);
        return data;
    } finally {
        clearTimeout(timer);
    }
}

// AniList (GraphQL) is called straight from the browser
function anilist(query, variables, cacheKey, maxAgeMinutes) {
    return fetchJSON(ANILIST_URL, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
        body: JSON.stringify({ query, variables })
    }, cacheKey, maxAgeMinutes).then((result) => result.data);
}

// news comes from our own server (server.py), which reads the publishers' feeds
function newsAvailable() {
    return window.location.protocol.startsWith('http');
}

function loadNews() {
    if (!newsAvailable()) {
        return Promise.reject(new Error('Opened as a file: start server.py for live news'));
    }
    return fetchJSON('/api/news', {}, 'panel:news', 5);
}

// seasons: Winter (Jan-Mar), Spring, Summer, Fall
const SEASONS = ['WINTER', 'SPRING', 'SUMMER', 'FALL'];

function seasonOf(date) {
    return { season: SEASONS[Math.floor(date.getMonth() / 3)], year: date.getFullYear() };
}

function nextSeasonOf(date) {
    const index = Math.floor(date.getMonth() / 3);
    return index === 3
        ? { season: 'WINTER', year: date.getFullYear() + 1 }
        : { season: SEASONS[index + 1], year: date.getFullYear() };
}

function seasonName(season, year) {
    return `${season.charAt(0)}${season.slice(1).toLowerCase()} ${year}`;
}

// AniList records in a simpler shape
function normalizeMedia(media) {
    const studio = media.studios && media.studios.nodes.length ? media.studios.nodes[0].name : '';
    return {
        id: media.id,
        title: media.title.english || media.title.romaji,
        romaji: media.title.romaji,
        native: media.title.native || '',
        cover: media.coverImage ? media.coverImage.large : PLACEHOLDER,
        color: media.coverImage && media.coverImage.color ? media.coverImage.color : '',
        banner: media.bannerImage || '',
        format: media.format || '',
        status: media.status || '',
        episodes: media.episodes,
        season: media.season,
        year: media.seasonYear,
        score: media.averageScore,
        popularity: media.popularity || 0,
        genres: media.genres || [],
        url: media.siteUrl,
        studio,
        next: media.nextAiringEpisode
            ? { episode: media.nextAiringEpisode.episode, at: new Date(media.nextAiringEpisode.airingAt * 1000).toISOString() }
            : null,
        trailer: media.trailer && media.trailer.site === 'youtube' ? media.trailer.id : null,
        description: media.description || '',
        rankings: media.rankings || [],
        favourites: media.favourites || 0
    };
}

function showLink(media) {
    return `show.html?id=${encodeURIComponent(media.id)}`;
}

// formatting
function escapeHTML(text) {
    return String(text).replace(/[&<>"']/g, (char) => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    })[char]);
}

const dateFormat = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
const dayTimeFormat = new Intl.DateTimeFormat('en-GB', { weekday: 'short', day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
const timeFormat = new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit' });
const relativeFormat = new Intl.RelativeTimeFormat('en', { numeric: 'auto' });

function formatDate(iso) {
    return dateFormat.format(new Date(iso));
}

function formatDayTime(iso) {
    return dayTimeFormat.format(new Date(iso));
}

function formatTime(iso) {
    return timeFormat.format(new Date(iso));
}

function timeAgo(iso) {
    const seconds = Math.round((new Date(iso) - Date.now()) / 1000);
    for (const [unit, size] of [['day', 86400], ['hour', 3600], ['minute', 60]]) {
        if (Math.abs(seconds) >= size) {
            return relativeFormat.format(Math.round(seconds / size), unit);
        }
    }
    return 'just now';
}

function number(value) {
    return Number(value).toLocaleString('en-GB');
}

function countdownText(iso) {
    let diff = Math.round((new Date(iso) - Date.now()) / 1000);
    if (diff <= 0) {
        return 'Out now';
    }
    const days = Math.floor(diff / 86400);
    diff %= 86400;
    const hours = String(Math.floor(diff / 3600)).padStart(2, '0');
    const minutes = String(Math.floor((diff % 3600) / 60)).padStart(2, '0');
    const seconds = String(diff % 60).padStart(2, '0');
    return `${days > 0 ? `${days}d ` : ''}${hours}:${minutes}:${seconds}`;
}

function refreshCountdowns() {
    document.querySelectorAll('[data-countdown]').forEach((element) => {
        element.textContent = countdownText(element.dataset.countdown);
    });
}

function refreshRelativeTimes() {
    document.querySelectorAll('time[data-relative]').forEach((element) => {
        element.textContent = timeAgo(element.dateTime);
    });
}

function image(src, alt, className, eager) {
    return `<img class="${className}" src="${escapeHTML(src || PLACEHOLDER)}" alt="${escapeHTML(alt)}" ${eager ? '' : 'loading="lazy" '}decoding="async" data-fallback>`;
}

// AniList descriptions contain <br> and <i>; keep the paragraphs, drop the tags
function descriptionParagraphs(text) {
    return text
        .replace(/<br\s*\/?>/gi, '\n')
        .replace(/<[^>]+>/g, '')
        .replace(/\(Source:[^)]*\)/gi, '')
        .split(/\n\s*\n|\n/)
        .map((paragraph) => paragraph.trim())
        .filter(Boolean);
}

// news story card used on both pages
function storyHTML(story, kind, headingLevel) {
    const summary = kind === 'compact' ? '' : `<p class="standfirst">${escapeHTML(story.summary)}</p>`;
    const author = story.author ? `By ${escapeHTML(story.author)} · ` : '';
    return `<article class="story story-${kind}">
    ${story.image ? image(story.image, '', 'story-image', kind === 'lead') : ''}
    <div class="story-text">
        <p class="kicker">${escapeHTML(story.category && story.category !== 'Latest News' ? story.category : 'News')}</p>
        <h${headingLevel} class="headline"><a href="${escapeHTML(story.url)}">${escapeHTML(story.title)}</a></h${headingLevel}>
        ${summary}
        <p class="byline">${author}<span class="source">${escapeHTML(story.source)}</span> · <time datetime="${story.published}" data-relative>${timeAgo(story.published)}</time></p>
    </div>
</article>`;
}

// broken images are replaced with a neutral placeholder
document.addEventListener('error', (event) => {
    const target = event.target;
    if (target.tagName === 'IMG' && target.hasAttribute('data-fallback') && !target.src.endsWith(PLACEHOLDER)) {
        target.src = PLACEHOLDER;
    }
}, true);

// light and dark theme, remembered between visits
const themeButton = document.querySelector('#theme-toggle');

function currentTheme() {
    return document.documentElement.dataset.theme
        || (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
}

function showTheme() {
    const dark = currentTheme() === 'dark';
    themeButton.setAttribute('aria-pressed', String(dark));
    themeButton.querySelector('.theme-label').textContent = dark ? 'Dark' : 'Light';
}

if (themeButton) {
    themeButton.hidden = false;
    showTheme();
    themeButton.addEventListener('click', () => {
        document.documentElement.dataset.theme = currentTheme() === 'dark' ? 'light' : 'dark';
        try {
            localStorage.setItem('panel-theme', document.documentElement.dataset.theme);
        } catch (error) {
            // not saved; the theme still changes for this visit
        }
        showTheme();
    });
}

// today's date in the masthead
const todayElement = document.querySelector('#today');
if (todayElement) {
    const now = new Date();
    todayElement.dateTime = now.toISOString().slice(0, 10);
    todayElement.textContent = new Intl.DateTimeFormat('en-GB', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' }).format(now);
}

// "back to top" button appears after scrolling down
const backToTop = document.querySelector('#back-to-top');
if (backToTop) {
    const toggleBackToTop = () => backToTop.classList.toggle('is-visible', window.scrollY > 900);
    window.addEventListener('scroll', toggleBackToTop, { passive: true });
    toggleBackToTop();
}

function savedLabel() {
    return `saved ${formatDate(SNAPSHOT.savedAt)}`;
}
