// Panel & Pixel - front page: saved data first, then live news (server.py) and live show data (AniList)

const liveStatus = document.querySelector('#live-status');
let currentStories = SNAPSHOT.news.stories;
let currentAnime = prepareAnime(SNAPSHOT.anilist, new Date(SNAPSHOT.savedAt));
let selectedDay = 0;

function prepareAnime(data, date) {
    const now = seasonOf(date);
    const next = nextSeasonOf(date);
    return {
        season: now,
        next,
        trending: data.trending.media.map(normalizeMedia),
        seasonal: data.season.media.map(normalizeMedia),
        upcoming: data.upcoming.media.map(normalizeMedia),
        favourites: data.favourites.media.map(normalizeMedia).sort((a, b) => b.score - a.score),
        airing: data.airing.media.map(normalizeMedia).filter((media) => media.next)
    };
}

function scoreHTML(media) {
    return media.score
        ? `<span class="score" aria-label="Average score ${media.score} out of 100">${media.score}<span aria-hidden="true">%</span></span>`
        : '<span class="score score-none">New</span>';
}

// news parts of the page
function newsParts(stories) {
    const withImage = stories.filter((story) => story.image);
    const top = withImage.slice(0, 3);
    const rest = stories.filter((story) => !top.includes(story));
    return { lead: top[0], seconds: top.slice(1, 3), latest: rest.slice(0, 6), briefing: rest.slice(6, 12) };
}

function latestHTML(stories) {
    const items = stories.map((story) => `<li>
        <time datetime="${story.published}" data-relative>${timeAgo(story.published)}</time>
        <a href="${escapeHTML(story.url)}">${escapeHTML(story.title)}</a>
        <span class="source">${escapeHTML(story.source)}</span>
    </li>`).join('');
    return `<section class="latest" aria-labelledby="latest-title">
    <h2 class="section-label" id="latest-title">Latest</h2>
    <ol class="latest-list">${items}</ol>
</section>`;
}

function nextEpisodeHTML(airing) {
    const now = Date.now();
    const popular = airing.slice(0, 15).filter((media) => new Date(media.next.at) > now);
    const media = popular.sort((a, b) => new Date(a.next.at) - new Date(b.next.at))[0];
    if (!media) {
        return '';
    }
    return `<section class="next-episode" aria-labelledby="next-episode-title">
    <h2 class="section-label" id="next-episode-title">Next big episode</h2>
    <a class="next-episode-link" href="${showLink(media)}">
        ${image(media.cover, '', 'next-cover', true)}
        <span class="next-text">
            <span class="next-title">${escapeHTML(media.title)}</span>
            <span class="next-meta">Episode ${media.next.episode} · <time datetime="${media.next.at}">${formatDayTime(media.next.at)}</time></span>
        </span>
    </a>
    <p class="countdown" role="timer" data-countdown="${media.next.at}">${countdownText(media.next.at)}</p>
    <a class="more-link" href="#schedule">Full airing schedule</a>
</section>`;
}

function renderTicker(stories) {
    const items = stories.slice(0, 10);
    const list = (hidden) => items.map((story) => `<li${hidden ? ' aria-hidden="true"' : ''}><a href="${escapeHTML(story.url)}"${hidden ? ' tabindex="-1"' : ''}>${escapeHTML(story.title)}</a></li>`).join('');
    document.querySelector('#ticker-track').innerHTML = list(false) + list(true);
}

function renderFront() {
    const parts = newsParts(currentStories);
    document.querySelector('#front').innerHTML = [
        storyHTML(parts.lead, 'lead', 2),
        ...parts.seconds.map((story) => storyHTML(story, 'second', 3)),
        latestHTML(parts.latest),
        nextEpisodeHTML(currentAnime.airing)
    ].join('\n');
    document.querySelector('#briefing-columns').innerHTML = parts.briefing.map((story) => `<article class="brief">
    <p class="kicker">${escapeHTML(story.source)}</p>
    <h3 class="headline"><a href="${escapeHTML(story.url)}">${escapeHTML(story.title)}</a></h3>
    <p>${escapeHTML(story.summary)}</p>
    <p class="byline">${story.author ? `By ${escapeHTML(story.author)} · ` : ''}<time datetime="${story.published}" data-relative>${timeAgo(story.published)}</time></p>
</article>`).join('\n');
    renderTicker(currentStories);
}

// airing schedule: seven day tabs starting today
function renderSchedule() {
    const days = Array.from({ length: 7 }, (unused, i) => {
        const date = new Date();
        date.setHours(0, 0, 0, 0);
        date.setDate(date.getDate() + i);
        return date;
    });
    const label = (date, i) => (i === 0 ? 'Today' : i === 1 ? 'Tomorrow' : new Intl.DateTimeFormat('en-GB', { weekday: 'long' }).format(date));
    const tabs = days.map((date, i) => `<button class="tab" type="button" role="tab" id="day-tab-${i}" aria-controls="day-panel-${i}" aria-selected="${i === selectedDay}" tabindex="${i === selectedDay ? 0 : -1}">${label(date, i)} <span class="tab-date">${new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short' }).format(date)}</span></button>`).join('');

    const panels = days.map((date, i) => {
        const end = new Date(date);
        end.setDate(end.getDate() + 1);
        const shows = currentAnime.airing
            .filter((media) => new Date(media.next.at) >= date && new Date(media.next.at) < end)
            .sort((a, b) => new Date(a.next.at) - new Date(b.next.at));
        const items = shows.map((media) => `<li>
            <time class="slot-time" datetime="${media.next.at}">${formatTime(media.next.at)}</time>
            ${image(media.cover, '', 'slot-cover', false)}
            <span class="slot-text"><a href="${showLink(media)}">${escapeHTML(media.title)}</a><span>Episode ${media.next.episode}</span></span>
            <span class="countdown-small" data-countdown="${media.next.at}">${countdownText(media.next.at)}</span>
        </li>`).join('');
        return `<div class="tab-panel" id="day-panel-${i}" role="tabpanel" aria-labelledby="day-tab-${i}" tabindex="0"${i === selectedDay ? '' : ' hidden'}>
    <h3 class="panel-title">${label(date, i)}</h3>
    ${shows.length ? `<ol class="schedule-list">${items}</ol>` : `<p class="empty-day">${i === 0 ? 'No more episodes of the 50 most popular shows today. Try Tomorrow.' : 'None of the 50 most popular shows air on this day.'}</p>`}
</div>`;
    }).join('\n');

    document.querySelector('#schedule-days').innerHTML = `<div class="tabs" role="tablist" aria-label="Days of the week">${tabs}</div>\n${panels}`;
}

function selectDay(index, moveFocus) {
    selectedDay = index;
    document.querySelectorAll('#schedule-days .tab').forEach((tab, i) => {
        tab.setAttribute('aria-selected', String(i === index));
        tab.tabIndex = i === index ? 0 : -1;
        document.querySelector(`#day-panel-${i}`).hidden = i !== index;
    });
    if (moveFocus) {
        document.querySelector(`#day-tab-${index}`).focus();
    }
}

// one listener for the tabs, so it keeps working when the schedule is re-drawn
const scheduleDays = document.querySelector('#schedule-days');
scheduleDays.addEventListener('click', (event) => {
    const tab = event.target.closest('.tab');
    if (tab) {
        selectDay(Number(tab.id.split('-').pop()), false);
    }
});
scheduleDays.addEventListener('keydown', (event) => {
    const tab = event.target.closest('.tab');
    if (!tab) {
        return;
    }
    const index = Number(tab.id.split('-').pop());
    const moves = { ArrowRight: (index + 1) % 7, ArrowLeft: (index + 6) % 7, Home: 0, End: 6 };
    if (event.key in moves) {
        event.preventDefault();
        selectDay(moves[event.key], true);
    }
});

// show cards
function trendingHTML(media, rank) {
    const next = media.next && new Date(media.next.at) > Date.now()
        ? `<p class="anime-next">Ep ${media.next.episode} in <span data-countdown="${media.next.at}">${countdownText(media.next.at)}</span></p>`
        : '';
    return `<li>
    <article class="anime-card">
        <p class="rank" aria-label="Trending number ${rank}">${rank}</p>
        ${image(media.cover, '', 'anime-cover', false)}
        <div class="anime-text">
            <h3 class="anime-title"><a href="${showLink(media)}">${escapeHTML(media.title)}</a></h3>
            <p class="anime-meta">${scoreHTML(media)} ${escapeHTML(media.studio || media.format)}</p>
            ${next}
        </div>
    </article>
</li>`;
}

function posterHTML(media) {
    return `<li>
    <article class="poster">
        <div class="poster-image">
            ${image(media.cover, '', 'anime-cover', false)}
            ${scoreHTML(media)}
        </div>
        <h3 class="anime-title"><a href="${showLink(media)}">${escapeHTML(media.title)}</a></h3>
        <p class="anime-meta">${escapeHTML([media.studio, media.format === 'TV' ? 'TV series' : media.format].filter(Boolean).join(' · '))}</p>
        <p class="genres">${media.genres.slice(0, 2).map(escapeHTML).join(', ')}</p>
    </article>
</li>`;
}

function rankingText(ranking) {
    return `#${ranking.rank} ${ranking.context.replace(' all time', '')} of all time`;
}

function favouriteHTML(media) {
    const allTime = media.rankings.filter((ranking) => ranking.allTime).slice(0, 2);
    const synopsis = descriptionParagraphs(media.description)[0] || '';
    return `<article class="favourite">
    ${media.banner ? image(media.banner, '', 'favourite-banner', false) : ''}
    <div class="favourite-inner">
        ${image(media.cover, '', 'favourite-cover', false)}
        <div class="favourite-text">
            <p class="kicker">Fan favourite</p>
            <h3><a href="${showLink(media)}">${escapeHTML(media.title)}</a></h3>
            ${media.native ? `<p class="native-title" lang="ja">${escapeHTML(media.native)}</p>` : ''}
            <ul class="rank-badges">${allTime.map((ranking) => `<li>${escapeHTML(rankingText(ranking))}</li>`).join('')}</ul>
        </div>
        <dl class="favourite-stats">
            <div><dt>Score</dt><dd>${media.score ?? '–'}%</dd></div>
            <div><dt>Fans</dt><dd>${number(media.popularity)}</dd></div>
            <div><dt>Episodes</dt><dd>${media.episodes ?? '–'}</dd></div>
            <div><dt>Studio</dt><dd>${escapeHTML(media.studio)}</dd></div>
        </dl>
        <p class="favourite-synopsis">${escapeHTML(synopsis)}</p>
        <p class="favourite-links">
            <a class="btn" href="${showLink(media)}">More about this show</a>
            ${media.trailer ? `<a class="btn btn-ghost" href="https://www.youtube.com/watch?v=${encodeURIComponent(media.trailer)}">Watch the trailer<span class="visually-hidden"> for ${escapeHTML(media.title)} on YouTube</span></a>` : ''}
        </p>
    </div>
</article>`;
}

function renderShows() {
    const anime = currentAnime;
    document.querySelector('#trending-list').innerHTML = anime.trending.map((media, i) => trendingHTML(media, i + 1)).join('\n');
    document.querySelector('#season-title').textContent = `${seasonName(anime.season.season, anime.season.year)} season`;
    document.querySelector('#season-grid').innerHTML = anime.seasonal.map(posterHTML).join('\n');
    document.querySelector('#favourites-grid').innerHTML = anime.favourites.map(favouriteHTML).join('\n');
    document.querySelector('#upcoming-title').textContent = `Coming in ${seasonName(anime.next.season, anime.next.year)}`;
    document.querySelector('#upcoming-grid').innerHTML = anime.upcoming.map(posterHTML).join('\n');
    renderSchedule();
}

// status line in the masthead
function setStatus(newsLive, showsLive) {
    let text;
    if (newsLive && showsLive) {
        text = '<span class="live-dot" aria-hidden="true"></span>Live · updated <time datetime="' + new Date().toISOString() + '" data-relative>just now</time>';
    } else if (showsLive) {
        text = `<span class="live-dot" aria-hidden="true"></span>Shows live · news ${escapeHTML(savedLabel())}${newsAvailable() ? '' : ' (run server.py for live news)'}`;
    } else if (newsLive) {
        text = `<span class="live-dot" aria-hidden="true"></span>News live · shows ${escapeHTML(savedLabel())}`;
    } else {
        text = `Showing data ${escapeHTML(savedLabel())}`;
    }
    liveStatus.className = `live-status ${newsLive || showsLive ? 'is-live' : 'is-saved'}`;
    liveStatus.innerHTML = text;
}

let newsLive = false;
let showsLive = false;

async function refreshNews() {
    try {
        const data = await loadNews();
        currentStories = data.stories;
        newsLive = true;
        renderFront();
    } catch (error) {
        newsLive = false;
    }
    setStatus(newsLive, showsLive);
}

async function refreshShows() {
    const now = new Date();
    const season = seasonOf(now);
    const next = nextSeasonOf(now);
    try {
        const data = await anilist(FRONT_QUERY, { season: season.season, year: season.year, nextSeason: next.season, nextYear: next.year }, 'panel:front', 10);
        currentAnime = prepareAnime(data, now);
        showsLive = true;
        renderShows();
        renderFront();
    } catch (error) {
        showsLive = false;
    }
    setStatus(newsLive, showsLive);
}

// ticker pause button
const tickerButton = document.querySelector('#ticker-pause');
tickerButton.hidden = false;
tickerButton.addEventListener('click', () => {
    const paused = tickerButton.getAttribute('aria-pressed') !== 'true';
    tickerButton.setAttribute('aria-pressed', String(paused));
    tickerButton.textContent = paused ? 'Play' : 'Pause';
    document.querySelector('#ticker').classList.toggle('is-paused', paused);
});

// newsletter form (demo: nothing is sent)
const newsletterForm = document.querySelector('#newsletter-form');
newsletterForm.addEventListener('submit', (event) => {
    event.preventDefault();
    const email = document.querySelector('#newsletter-email');
    const message = document.querySelector('#newsletter-message');
    if (email.validity.valid) {
        message.textContent = `Thanks! The weekly round-up would go to ${email.value}. (Demo: nothing was sent.)`;
        message.classList.remove('is-error');
        email.removeAttribute('aria-invalid');
        newsletterForm.reset();
    } else {
        message.textContent = email.validity.valueMissing ? 'Enter your email address.' : 'Enter an email address like name@example.com.';
        message.classList.add('is-error');
        email.setAttribute('aria-invalid', 'true');
        email.focus();
    }
});

// start: saved data is already in the HTML; live data replaces it when it arrives
if (!document.querySelector('#front .story')) {
    renderFront();
    renderShows();
}
refreshRelativeTimes();
refreshCountdowns();

if (window.PANEL_BUILD) {
    setStatus(false, false);
} else {
    // the schedule's days depend on today's date, so it is always re-drawn in the browser
    renderSchedule();
    setStatus(false, false);
    refreshShows().then(refreshNews);
    setInterval(refreshNews, 5 * 60000);
    setInterval(refreshShows, 10 * 60000);
    setInterval(refreshCountdowns, 1000);
    setInterval(refreshRelativeTimes, 30000);
}
