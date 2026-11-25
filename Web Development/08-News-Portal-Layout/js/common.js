// Orbit Daily - shared code: live data, saved snapshot, formatting, theme and countdowns

const API = {
    articles: 'https://api.spaceflightnewsapi.net/v4/articles/',
    launches: 'https://ll.thespacedevs.com/2.3.0/launches/upcoming/',
    iss: 'https://api.wheretheiss.at/v1/satellites/25544'
};

const PLACEHOLDER = 'images/placeholder.svg';

// fetch JSON with a time limit, keeping a copy in sessionStorage so refreshes stay within rate limits
async function getJSON(url, maxAgeMinutes) {
    const key = `orbit:${url}`;
    try {
        const saved = JSON.parse(sessionStorage.getItem(key));
        if (saved && Date.now() - saved.time < maxAgeMinutes * 60000) {
            return saved.data;
        }
    } catch (error) {
        // storage can be blocked; fetch instead
    }

    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 10000);
    try {
        const response = await fetch(url, { signal: controller.signal });
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }
        const data = await response.json();
        try {
            sessionStorage.setItem(key, JSON.stringify({ time: Date.now(), data }));
        } catch (error) {
            // storage full or blocked; the data is still used
        }
        return data;
    } finally {
        clearTimeout(timer);
    }
}

// turn API records into the same shape as the saved snapshot
function normalizeArticle(item) {
    return {
        id: item.id,
        title: item.title.trim(),
        summary: (item.summary || '').trim(),
        url: item.url,
        image: item.image_url || PLACEHOLDER,
        source: item.news_site,
        authors: (item.authors || []).map((author) => author.name).filter(Boolean),
        published: item.published_at,
        updated: item.updated_at
    };
}

function normalizeLaunch(item) {
    const rocket = item.rocket && item.rocket.configuration;
    return {
        id: item.id,
        name: item.name,
        mission: item.mission ? item.mission.name : item.name.split('|').pop().trim(),
        description: item.mission && item.mission.description ? item.mission.description : '',
        orbit: item.mission && item.mission.orbit ? item.mission.orbit.name : '',
        net: item.net,
        status: item.status ? item.status.abbrev : 'TBD',
        statusName: item.status ? item.status.name : 'To be determined',
        provider: item.launch_service_provider ? item.launch_service_provider.name : '',
        rocket: rocket ? rocket.full_name || rocket.name : item.name.split('|')[0].trim(),
        pad: item.pad ? item.pad.name : '',
        location: item.pad && item.pad.location ? item.pad.location.name : '',
        image: item.image && item.image.image_url ? item.image.image_url : PLACEHOLDER,
        imageCredit: item.image && item.image.credit ? item.image.credit : ''
    };
}

async function loadArticles(query, limit) {
    const url = `${API.articles}?limit=${limit}${query ? `&search=${encodeURIComponent(query)}` : ''}`;
    const data = await getJSON(url, 5);
    return data.results.map(normalizeArticle);
}

async function loadLaunches(limit) {
    const data = await getJSON(`${API.launches}?limit=${limit}&mode=normal`, 30);
    return data.results.map(normalizeLaunch);
}

// a short topic label ("kicker") worked out from the headline and summary
const TOPICS = [
    ['Moon', /\b(moon|lunar|artemis)\b/i],
    ['Mars', /\b(mars|martian|perseverance|curiosity)\b/i],
    ['Space station', /\b(iss|space station|tiangong|crew dragon|starliner)\b/i],
    ['Earth', /\b(earth observation|climate|sentinel|weather|ocean|copernicus)\b/i],
    ['Astronomy', /\b(telescope|webb|hubble|galaxy|nebula|star|solar|sun|asteroid|comet|black hole)\b/i],
    ['Launches', /\b(launch|rocket|falcon|starship|ariane|booster|vulcan|electron|long march)\b/i],
    ['Satellites', /\b(satellite|constellation|starlink|kuiper|orbit)\b/i],
    ['Business', /\b(funding|contract|company|startup|million|billion|market)\b/i]
];

function topicOf(article) {
    const text = `${article.title} ${article.summary}`;
    const match = TOPICS.find(([, pattern]) => pattern.test(text));
    return match ? match[0] : 'Space';
}

// formatting
function escapeHTML(text) {
    return String(text).replace(/[&<>"']/g, (char) => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    })[char]);
}

const dateFormat = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
const longDateFormat = new Intl.DateTimeFormat('en-GB', { weekday: 'short', day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit', timeZoneName: 'short' });
const relativeFormat = new Intl.RelativeTimeFormat('en', { numeric: 'auto' });

function formatDate(iso) {
    return dateFormat.format(new Date(iso));
}

function formatLongDate(iso) {
    return longDateFormat.format(new Date(iso));
}

function timeAgo(iso) {
    const seconds = Math.round((new Date(iso) - Date.now()) / 1000);
    const units = [['day', 86400], ['hour', 3600], ['minute', 60]];
    for (const [unit, size] of units) {
        if (Math.abs(seconds) >= size) {
            return relativeFormat.format(Math.round(seconds / size), unit);
        }
    }
    return 'just now';
}

function byline(article) {
    const people = article.authors.filter((name) => name.toLowerCase() !== article.source.toLowerCase());
    const authors = people.length ? `By ${escapeHTML(people.join(' and '))} · ` : '';
    return `${authors}<span class="source">${escapeHTML(article.source)}</span> · <time datetime="${article.published}" data-relative title="${escapeHTML(formatDate(article.published))}">${timeAgo(article.published)}</time>`;
}

function articleLink(article) {
    return `article.html?id=${encodeURIComponent(article.id)}`;
}

function image(src, alt, className, eager) {
    return `<img class="${className}" src="${escapeHTML(src)}" alt="${escapeHTML(alt)}" ${eager ? '' : 'loading="lazy" '}decoding="async" data-fallback>`;
}

// story card used on both pages
function storyHTML(article, kind, headingLevel) {
    const eager = kind === 'lead';
    const summary = kind === 'compact' ? '' : `<p class="standfirst">${escapeHTML(article.summary)}</p>`;
    return `<article class="story story-${kind}">
    ${image(article.image, '', 'story-image', eager)}
    <div class="story-text">
        <p class="kicker">${topicOf(article)}</p>
        <h${headingLevel} class="headline"><a href="${articleLink(article)}">${escapeHTML(article.title)}</a></h${headingLevel}>
        ${summary}
        <p class="byline">${byline(article)}</p>
    </div>
</article>`;
}

// keep "3 hours ago" labels current
function refreshRelativeTimes() {
    document.querySelectorAll('time[data-relative]').forEach((element) => {
        element.textContent = timeAgo(element.dateTime);
    });
}

// countdowns: "T- 1d 04:12:09" before launch, "T+ ..." after
function countdownText(iso) {
    let diff = Math.round((new Date(iso) - Date.now()) / 1000);
    const sign = diff >= 0 ? 'T−' : 'T+';
    diff = Math.abs(diff);
    const days = Math.floor(diff / 86400);
    const hours = String(Math.floor((diff % 86400) / 3600)).padStart(2, '0');
    const minutes = String(Math.floor((diff % 3600) / 60)).padStart(2, '0');
    const seconds = String(diff % 60).padStart(2, '0');
    return `${sign} ${days > 0 ? `${days}d ` : ''}${hours}:${minutes}:${seconds}`;
}

function refreshCountdowns() {
    document.querySelectorAll('[data-countdown]').forEach((element) => {
        element.textContent = countdownText(element.dataset.countdown);
    });
}

// broken or missing images are replaced with a neutral placeholder
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
        const next = currentTheme() === 'dark' ? 'light' : 'dark';
        document.documentElement.dataset.theme = next;
        try {
            localStorage.setItem('orbit-theme', next);
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

// the saved snapshot, used when the live services cannot be reached
const SNAPSHOT = window.ORBIT_SNAPSHOT;

function savedLabel() {
    return `Showing saved stories from ${formatDate(SNAPSHOT.savedAt)}`;
}

// "back to top" button appears after scrolling down
const backToTop = document.querySelector('#back-to-top');
if (backToTop) {
    const toggleBackToTop = () => backToTop.classList.toggle('is-visible', window.scrollY > 900);
    window.addEventListener('scroll', toggleBackToTop, { passive: true });
    toggleBackToTop();
}
