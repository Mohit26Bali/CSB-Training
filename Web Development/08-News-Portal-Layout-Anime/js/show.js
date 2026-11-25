// Panel & Pixel - show page: details from AniList, next episode countdown, characters, trailer and related news

const showElement = document.querySelector('#show');
const showId = Number(new URLSearchParams(window.location.search).get('id'));

const STATUS_TEXT = { RELEASING: 'Airing', FINISHED: 'Finished', NOT_YET_RELEASED: 'Not yet aired', CANCELLED: 'Cancelled', HIATUS: 'On hiatus' };
const FORMAT_TEXT = { TV: 'TV series', TV_SHORT: 'TV short', MOVIE: 'Film', OVA: 'OVA', ONA: 'Web series', SPECIAL: 'Special' };

function savedMedia(id) {
    const lists = Object.values(SNAPSHOT.anilist).map((group) => group.media).flat();
    const found = lists.find((media) => media.id === id && media.title && media.studios);
    return found ? normalizeMedia(found) : null;
}

async function loadShow(id) {
    try {
        const data = await anilist(SHOW_QUERY, { id }, `panel:show:${id}`, 30);
        const raw = data.Media;
        const media = normalizeMedia(raw);
        media.duration = raw.duration;
        media.source = raw.source;
        media.characters = raw.characters.edges.map((edge) => ({ name: edge.node.name.full, image: edge.node.image.medium, role: edge.role }));
        return { media, live: true };
    } catch (error) {
        const media = savedMedia(id);
        return media ? { media, live: false } : null;
    }
}

function titleWords(media) {
    return [media.title, media.romaji]
        .filter(Boolean)
        .map((title) => title.split(':')[0].replace(/\s+(season\s*\d+|\d+(st|nd|rd|th) season)$/i, '').trim().toLowerCase())
        .filter((title) => title.length >= 4);
}

async function relatedNews(media) {
    let stories = SNAPSHOT.news.stories;
    try {
        stories = (await loadNews()).stories;
    } catch (error) {
        // use the saved headlines
    }
    const words = titleWords(media);
    return stories.filter((story) => words.some((word) => story.title.toLowerCase().includes(word))).slice(0, 5);
}

function factsHTML(media) {
    const facts = [
        ['Score', media.score ? `${media.score}%` : 'Not yet rated'],
        ['Fans', number(media.popularity)],
        ['Format', FORMAT_TEXT[media.format] || media.format],
        ['Episodes', media.episodes ?? 'Unknown'],
        ['Status', STATUS_TEXT[media.status] || media.status],
        ['Season', media.season ? seasonName(media.season, media.year) : 'Unknown'],
        ['Studio', media.studio || 'Unknown']
    ];
    if (media.duration) {
        facts.push(['Episode length', `${media.duration} min`]);
    }
    if (media.source) {
        facts.push(['Based on', media.source.replace(/_/g, ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase())]);
    }
    if (media.favourites) {
        facts.push(['Favourited', number(media.favourites)]);
    }
    return `<dl class="show-facts">${facts.map(([term, value]) => `<div><dt>${term}</dt><dd>${escapeHTML(value)}</dd></div>`).join('')}</dl>`;
}

function renderShow(media, live) {
    document.title = `${media.title} | Panel & Pixel`;
    const allTime = media.rankings.filter((ranking) => ranking.allTime).slice(0, 2);
    const paragraphs = descriptionParagraphs(media.description);
    const next = media.next && new Date(media.next.at) > Date.now()
        ? `<section class="show-next" aria-labelledby="next-title">
            <h2 class="section-label" id="next-title">Next episode</h2>
            <p>Episode ${media.next.episode} · <time datetime="${media.next.at}">${formatDayTime(media.next.at)}</time></p>
            <p class="countdown" role="timer" data-countdown="${media.next.at}">${countdownText(media.next.at)}</p>
        </section>`
        : '';
    const characters = media.characters && media.characters.length
        ? `<section class="show-section" aria-labelledby="characters-title">
            <h2 id="characters-title">Characters</h2>
            <ul class="character-grid">${media.characters.map((character) => `<li>${image(character.image, '', '', false)}<p>${escapeHTML(character.name)}<span>${character.role === 'MAIN' ? 'Main character' : 'Supporting'}</span></p></li>`).join('')}</ul>
        </section>`
        : '';
    const trailer = media.trailer
        ? `<section class="show-section" aria-labelledby="trailer-title">
            <h2 id="trailer-title">Trailer</h2>
            <a class="trailer-link" href="https://www.youtube.com/watch?v=${encodeURIComponent(media.trailer)}">
                <img src="https://i.ytimg.com/vi/${encodeURIComponent(media.trailer)}/hqdefault.jpg" alt="" loading="lazy" data-fallback>
                <span>Watch the trailer on YouTube <span aria-hidden="true">&rarr;</span></span>
            </a>
        </section>`
        : '';

    showElement.innerHTML = `<section class="show-hero" aria-labelledby="show-title">
    ${media.banner ? image(media.banner, '', 'show-banner', true) : ''}
    <div class="container show-hero-inner">
        ${image(media.cover, `Cover art for ${media.title}`, 'show-cover', true)}
        <div>
            <nav class="breadcrumb" aria-label="Breadcrumb">
                <ol>
                    <li><a href="index.html">Home</a></li>
                    <li>Shows</li>
                    <li><span aria-current="page">${escapeHTML(media.title)}</span></li>
                </ol>
            </nav>
            <p class="kicker">${escapeHTML(FORMAT_TEXT[media.format] || media.format)}${media.season ? ` · ${seasonName(media.season, media.year)}` : ''}</p>
            <h1 id="show-title">${escapeHTML(media.title)}</h1>
            ${media.native ? `<p class="native-title" lang="ja">${escapeHTML(media.native)}</p>` : ''}
            ${allTime.length ? `<ul class="rank-badges">${allTime.map((ranking) => `<li>${escapeHTML(rankingText(ranking))}</li>`).join('')}</ul>` : ''}
        </div>
    </div>
</section>
<div class="container show-layout">
    <div>
        <section class="show-synopsis" aria-labelledby="synopsis-title">
            <h2 class="section-label" id="synopsis-title">Synopsis</h2>
            ${paragraphs.length ? paragraphs.map((paragraph) => `<p>${escapeHTML(paragraph)}</p>`).join('') : '<p>No synopsis yet.</p>'}
        </section>
        ${characters}
        ${trailer}
        <section class="show-section" id="related-news" aria-labelledby="news-title" hidden>
            <h2 id="news-title">In the news</h2>
            <ul class="news-list" id="news-list"></ul>
        </section>
    </div>
    <aside aria-label="Show facts">
        ${next}
        ${factsHTML(media)}
        <p class="byline">${live ? 'Live from AniList' : `Showing saved data, ${escapeHTML(savedLabel())}`}</p>
        <a class="more-link" href="${escapeHTML(media.url)}">See this show on AniList&nbsp;<span aria-hidden="true">&rarr;</span></a>
    </aside>
</div>`;
    refreshCountdowns();
}

function rankingText(ranking) {
    return `#${ranking.rank} ${ranking.context.replace(' all time', '')} of all time`;
}

async function start() {
    const result = showId ? await loadShow(showId) : null;
    if (!result) {
        document.title = 'Show not found | Panel & Pixel';
        showElement.innerHTML = `<div class="container not-found">
    <h1>Show not found</h1>
    <p>This show could not be loaded. AniList may be unreachable right now.</p>
    <p><a href="index.html">Go to the front page</a></p>
</div>`;
        return;
    }
    renderShow(result.media, result.live);
    const stories = await relatedNews(result.media);
    if (stories.length) {
        document.querySelector('#news-list').innerHTML = stories.map((story) => `<li>
            <a href="${escapeHTML(story.url)}">${escapeHTML(story.title)}</a>
            <p class="byline"><span class="source">${escapeHTML(story.source)}</span> · <time datetime="${story.published}" data-relative>${timeAgo(story.published)}</time></p>
        </li>`).join('');
        document.querySelector('#related-news').hidden = false;
    }
    setInterval(refreshCountdowns, 1000);
}

start();
