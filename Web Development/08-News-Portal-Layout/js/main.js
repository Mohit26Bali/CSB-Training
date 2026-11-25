// Orbit Daily - front page: renders saved stories, then replaces them with live news, launches and the ISS position

const liveStatus = document.querySelector('#live-status');

function setStatus(state, html) {
    liveStatus.className = `live-status is-${state}`;
    liveStatus.innerHTML = html;
}

function latestHTML(articles) {
    const items = articles.map((article) => `<li>
        <time datetime="${article.published}" data-relative>${timeAgo(article.published)}</time>
        <a href="${articleLink(article)}">${escapeHTML(article.title)}</a>
        <span class="source">${escapeHTML(article.source)}</span>
    </li>`).join('');
    return `<section class="latest" aria-labelledby="latest-title">
    <h2 class="section-label" id="latest-title">Latest</h2>
    <ol class="latest-list">${items}</ol>
</section>`;
}

function statusClass(launch) {
    const known = { Go: 'go', TBD: 'tbd', TBC: 'tbd', Hold: 'hold', Success: 'go', 'In Flight': 'go' };
    return `status-${known[launch.status] || 'tbd'}`;
}

function nextLaunchHTML(launch) {
    if (!launch) {
        return '';
    }
    return `<section class="next-launch" aria-labelledby="next-launch-title">
    <h2 class="section-label" id="next-launch-title">Next launch</h2>
    ${image(launch.image, '', 'launch-image', true)}
    <p class="countdown" role="timer" data-countdown="${launch.net}">${countdownText(launch.net)}</p>
    <h3 class="launch-mission">${escapeHTML(launch.mission)}</h3>
    <p class="launch-rocket">${escapeHTML(launch.rocket)}${launch.provider ? ` · ${escapeHTML(launch.provider)}` : ''}</p>
    <p class="launch-time"><time datetime="${launch.net}">${formatLongDate(launch.net)}</time></p>
    <p class="status ${statusClass(launch)}">${escapeHTML(launch.statusName)}</p>
    <a class="more-link" href="#launches">Full launch schedule</a>
</section>`;
}

function launchCardHTML(launch) {
    return `<li>
    <article class="launch-card">
        ${image(launch.image, '', 'launch-image', false)}
        <div class="launch-body">
            <p class="status ${statusClass(launch)}">${escapeHTML(launch.statusName)}</p>
            <h3 class="launch-mission">${escapeHTML(launch.mission)}</h3>
            <p class="launch-rocket">${escapeHTML(launch.rocket)}${launch.provider ? ` · ${escapeHTML(launch.provider)}` : ''}</p>
            <p class="launch-time"><time datetime="${launch.net}">${formatLongDate(launch.net)}</time> <span class="countdown-small" data-countdown="${launch.net}">${countdownText(launch.net)}</span></p>
            <p class="launch-pad">${escapeHTML([launch.pad, launch.location].filter(Boolean).join(', '))}</p>
            ${launch.description ? `<p class="launch-description">${escapeHTML(launch.description)}</p>` : ''}
        </div>
    </article>
</li>`;
}

// renderers for each part of the page
function renderTicker(articles) {
    const items = articles.slice(0, 10);
    const list = (hidden) => items.map((article) => `<li${hidden ? ' aria-hidden="true"' : ''}><a href="${articleLink(article)}"${hidden ? ' tabindex="-1"' : ''}>${escapeHTML(article.title)}</a></li>`).join('');
    document.querySelector('#ticker-track').innerHTML = list(false) + list(true);
}

function renderFront(articles, launches) {
    document.querySelector('#front').innerHTML = [
        storyHTML(articles[0], 'lead', 2),
        storyHTML(articles[1], 'second', 3),
        storyHTML(articles[2], 'second', 3),
        latestHTML(articles.slice(3, 9)),
        nextLaunchHTML(launches[0])
    ].join('\n');
}

function renderBriefing(articles) {
    document.querySelector('#briefing-columns').innerHTML = articles.slice(9, 15).map((article) => `<article class="brief">
    <p class="kicker">${topicOf(article)}</p>
    <h3 class="headline"><a href="${articleLink(article)}">${escapeHTML(article.title)}</a></h3>
    <p>${escapeHTML(article.summary)}</p>
    <p class="byline">${byline(article)}</p>
</article>`).join('\n');
}

function renderLaunches(launches) {
    document.querySelector('#launch-list').innerHTML = launches.slice(0, 6).map(launchCardHTML).join('\n');
}

function renderTopic(name, articles) {
    const grid = document.querySelector(`#${name}-grid`);
    grid.innerHTML = articles.slice(0, 4).map((article, index) => storyHTML(article, index === 0 && name === 'moon' ? 'feature' : 'card', 3)).join('\n');
}

function renderFrontPage(data) {
    renderTicker(data.articles);
    renderFront(data.articles, data.launches);
    renderBriefing(data.articles);
    renderLaunches(data.launches);
    Object.keys(data.topics).forEach((name) => renderTopic(name, data.topics[name]));
}

// ticker pause button (the ticker also pauses on hover and keyboard focus)
const tickerButton = document.querySelector('#ticker-pause');
tickerButton.hidden = false;
tickerButton.addEventListener('click', () => {
    const paused = tickerButton.getAttribute('aria-pressed') !== 'true';
    tickerButton.setAttribute('aria-pressed', String(paused));
    tickerButton.textContent = paused ? 'Play' : 'Pause';
    document.querySelector('#ticker').classList.toggle('is-paused', paused);
});

// live data
let lastArticleIds = '';

async function refreshNews() {
    const shown = new Set();
    try {
        const articles = await loadArticles('', 24);
        const ids = articles.map((article) => article.id).join(',');
        if (ids !== lastArticleIds) {
            renderTicker(articles);
            renderBriefing(articles);
            const launches = currentLaunches;
            renderFront(articles, launches);
            lastArticleIds = ids;
        }
        articles.slice(0, 15).forEach((article) => shown.add(article.id));
        setStatus('live', `<span class="live-dot" aria-hidden="true"></span>Live · updated <time datetime="${new Date().toISOString()}" data-relative>just now</time>`);
    } catch (error) {
        setStatus('saved', escapeHTML(savedLabel()));
        return;
    }

    // topic sections: newest stories on each topic that are not already on the front page
    for (const [name, query] of [['moon', 'moon'], ['mars', 'mars'], ['earth', 'climate']]) {
        try {
            const results = (await loadArticles(query, 12)).filter((article) => !shown.has(article.id));
            results.slice(0, 4).forEach((article) => shown.add(article.id));
            if (results.length >= 4) {
                renderTopic(name, results);
            }
        } catch (error) {
            // keep the saved stories for this topic
        }
    }
}

let currentLaunches = SNAPSHOT.launches;

async function refreshLaunches() {
    try {
        const launches = (await loadLaunches(8)).filter((launch) => new Date(launch.net) > Date.now() - 3600000);
        if (launches.length) {
            currentLaunches = launches;
            renderLaunches(launches);
            const next = document.querySelector('.next-launch');
            if (next) {
                next.outerHTML = nextLaunchHTML(launches[0]);
            }
        }
    } catch (error) {
        // the saved schedule stays on the page
    }
}

// live ISS tracker
const issDot = document.querySelector('#iss-dot');
const issTrail = document.querySelector('#iss-trail');
const issTrack = [];

function mapPoint(position) {
    return { x: position.longitude + 180, y: 90 - position.latitude };
}

function drawTrail() {
    let path = '';
    issTrack.forEach((position, index) => {
        const point = mapPoint(position);
        const jump = index > 0 && Math.abs(position.longitude - issTrack[index - 1].longitude) > 180;
        path += `${index === 0 || jump ? 'M' : 'L'}${point.x.toFixed(2)} ${point.y.toFixed(2)} `;
    });
    issTrail.setAttribute('d', path.trim());
}

function showIss(position) {
    const point = mapPoint(position);
    issDot.style.left = `${(point.x / 360) * 100}%`;
    issDot.style.top = `${(point.y / 180) * 100}%`;
    const lat = `${Math.abs(position.latitude).toFixed(2)}° ${position.latitude >= 0 ? 'N' : 'S'}`;
    const lon = `${Math.abs(position.longitude).toFixed(2)}° ${position.longitude >= 0 ? 'E' : 'W'}`;
    document.querySelector('#iss-lat').textContent = lat;
    document.querySelector('#iss-lon').textContent = lon;
    document.querySelector('#iss-alt').textContent = `${Math.round(position.altitude)} km`;
    document.querySelector('#iss-speed').textContent = `${Math.round(position.velocity).toLocaleString('en-GB')} km/h`;
    document.querySelector('#iss-light').textContent = position.visibility === 'eclipsed' ? "In Earth's shadow" : 'In sunlight';
    const updated = document.querySelector('#iss-updated');
    updated.dateTime = new Date(position.timestamp * 1000).toISOString();
    updated.textContent = timeAgo(updated.dateTime);
    document.querySelector('#iss-caption').textContent = `The International Space Station is above ${lat}, ${lon}.`;
}

async function startIss() {
    const issStatus = document.querySelector('#iss-status');
    try {
        const now = Math.floor(Date.now() / 1000);
        const stamps = Array.from({ length: 10 }, (unused, i) => now - (45 - i * 5) * 60).join(',');
        const history = await getJSON(`${API.iss}/positions?timestamps=${stamps}&units=kilometers`, 0);
        issTrack.length = 0;
        issTrack.push(...history);
        await updateIss();
        issStatus.textContent = 'Live position, updated every 5 seconds. The line shows the last 45 minutes.';
        setInterval(() => {
            if (!document.hidden) {
                updateIss().catch(() => {});
            }
        }, 5000);
    } catch (error) {
        issStatus.textContent = 'Live position unavailable. Showing the position when this page was saved.';
    }
}

async function updateIss() {
    const position = await getJSON(`${API.iss}?units=kilometers`, 0);
    issTrack.push(position);
    while (issTrack.length > 600) {
        issTrack.shift();
    }
    drawTrail();
    showIss(position);
}

// newsletter form (demo: nothing is sent)
const newsletterForm = document.querySelector('#newsletter-form');
newsletterForm.addEventListener('submit', (event) => {
    event.preventDefault();
    const email = document.querySelector('#newsletter-email');
    const message = document.querySelector('#newsletter-message');
    if (email.validity.valid) {
        message.textContent = `Thanks! The briefing would go to ${email.value}. (Demo: nothing was sent.)`;
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

// start: the saved snapshot is already in the HTML; live data replaces it when it arrives
if (!document.querySelector('#front .story')) {
    renderFrontPage(SNAPSHOT);
}
issTrack.push(...SNAPSHOT.iss);
drawTrail();
showIss(SNAPSHOT.iss[SNAPSHOT.iss.length - 1]);
refreshRelativeTimes();
refreshCountdowns();

if (!window.ORBIT_BUILD) {
    setStatus('loading', 'Connecting to live news…');
    refreshLaunches().then(refreshNews);
    startIss();
    setInterval(refreshNews, 5 * 60000);
    setInterval(refreshLaunches, 30 * 60000);
    setInterval(refreshRelativeTimes, 30000);
    setInterval(refreshCountdowns, 1000);
} else {
    setStatus('saved', escapeHTML(savedLabel()));
}
