// Orbit Daily - article page: loads one story by its id, with text size control and related stories

const storyElement = document.querySelector('#story');
const storyId = new URLSearchParams(window.location.search).get('id');

// search words for the related-stories box, based on the story's topic
const TOPIC_SEARCH = {
    Moon: 'moon', Mars: 'mars', 'Space station': 'space station', Earth: 'climate', Astronomy: 'telescope',
    Launches: 'launch', Satellites: 'satellite', Business: 'funding', Space: 'space'
};

function savedArticles() {
    return [...SNAPSHOT.articles, ...Object.values(SNAPSHOT.topics).flat()];
}

function findSaved(id) {
    return savedArticles().find((article) => String(article.id) === String(id));
}

async function loadStory(id) {
    try {
        const data = await getJSON(`${API.articles}${encodeURIComponent(id)}/`, 30);
        return { article: normalizeArticle(data), live: true };
    } catch (error) {
        const saved = findSaved(id);
        return saved ? { article: saved, live: false } : null;
    }
}

function renderStory(article, live) {
    const topic = topicOf(article);
    document.title = `${article.title} | Orbit Daily`;
    const people = article.authors.filter((name) => name.toLowerCase() !== article.source.toLowerCase());
    const authors = people.length ? people.join(' and ') : `the ${article.source} team`;
    const updated = article.updated && article.updated.slice(0, 16) !== article.published.slice(0, 16)
        ? ` · Updated <time datetime="${article.updated}">${formatDate(article.updated)}</time>`
        : '';

    storyElement.innerHTML = `<nav class="breadcrumb" aria-label="Breadcrumb">
    <ol>
        <li><a href="index.html">Home</a></li>
        <li>${topic}</li>
        <li><span aria-current="page">This story</span></li>
    </ol>
</nav>
<header class="article-header">
    <p class="kicker">${topic}</p>
    <h1>${escapeHTML(article.title)}</h1>
    <p class="byline">${byline(article)} · <time datetime="${article.published}">${formatDate(article.published)}</time>${updated}</p>
</header>
<div class="article-tools">
    <p class="byline">${live ? 'Live from the Spaceflight News API' : escapeHTML(savedLabel())}</p>
    <div class="text-size" role="group" aria-label="Text size">
        <p aria-hidden="true">Text size</p>
        <button type="button" data-size="1.15rem" aria-pressed="false" aria-label="Smaller text">A&minus;</button>
        <button type="button" data-size="1.3rem" aria-pressed="false" aria-label="Normal text">A</button>
        <button type="button" data-size="1.5rem" aria-pressed="false" aria-label="Larger text">A+</button>
    </div>
</div>
<figure class="article-figure">
    ${image(article.image, '', '', true)}
    <figcaption>Image: ${escapeHTML(article.source)}</figcaption>
</figure>
<div class="article-body">
    <p>${escapeHTML(article.summary)}</p>
</div>
<a class="read-full" href="${escapeHTML(article.url)}">Read the full story on ${escapeHTML(article.source)} <span aria-hidden="true">&rarr;</span></a>
<p class="article-note">This is the publisher's own summary. The full article, by ${escapeHTML(authors)}, is on ${escapeHTML(article.source)}'s website.</p>`;

    setupTextSize();
    refreshRelativeTimes();
}

// text size, remembered between stories
function setupTextSize() {
    const buttons = storyElement.querySelectorAll('.text-size button');
    let size = '1.3rem';
    try {
        size = localStorage.getItem('orbit-text-size') || size;
    } catch (error) {
        // use the default size
    }

    function apply(value) {
        document.documentElement.style.setProperty('--article-size', value);
        buttons.forEach((button) => button.setAttribute('aria-pressed', String(button.dataset.size === value)));
    }

    buttons.forEach((button) => button.addEventListener('click', () => {
        apply(button.dataset.size);
        try {
            localStorage.setItem('orbit-text-size', button.dataset.size);
        } catch (error) {
            // not saved; the size still changes
        }
    }));
    apply(size);
}

function renderRelated(sectionId, title, articles) {
    const section = document.querySelector(`#${sectionId}`);
    if (articles.length === 0) {
        return;
    }
    section.querySelector('h2').textContent = title;
    document.querySelector(`#${sectionId}-grid`).innerHTML = articles.slice(0, 4).map((article) => storyHTML(article, 'card', 3)).join('\n');
    section.hidden = false;
}

async function loadRelated(article, live) {
    const others = (list) => list.filter((item) => String(item.id) !== String(article.id));
    const topic = topicOf(article);
    let fromSource = [];
    let fromTopic = [];

    if (live) {
        try {
            const data = await getJSON(`${API.articles}?limit=6&news_site=${encodeURIComponent(article.source)}`, 15);
            fromSource = others(data.results.map(normalizeArticle));
            fromTopic = others(await loadArticles(TOPIC_SEARCH[topic], 8))
                .filter((item) => !fromSource.slice(0, 4).some((shown) => shown.id === item.id));
        } catch (error) {
            live = false;
        }
    }
    if (!live) {
        fromSource = others(savedArticles().filter((item) => item.source === article.source));
        fromTopic = others(savedArticles().filter((item) => topicOf(item) === topic && item.source !== article.source));
    }

    renderRelated('related-source', `More from ${article.source}`, fromSource);
    renderRelated('related-topic', `More on ${topic === 'Space' ? 'space' : topic}`, fromTopic);
}

async function start() {
    const result = storyId ? await loadStory(storyId) : null;
    if (!result) {
        document.title = 'Story not found | Orbit Daily';
        storyElement.innerHTML = `<div class="not-found">
    <h1>Story not found</h1>
    <p>This story may have been removed, or the live news service cannot be reached right now.</p>
    <p><a href="index.html">Go to the front page</a></p>
</div>`;
        return;
    }
    renderStory(result.article, result.live);
    loadRelated(result.article, result.live);
    setInterval(refreshRelativeTimes, 30000);
}

start();
