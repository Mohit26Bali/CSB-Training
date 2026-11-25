// Long Way Round home page - search, category filter, "Show more", read badges and the newsletter form

const PAGE_SIZE = 6;
const list = document.querySelector('#story-grid');
const items = Array.from(list.children);
const filters = document.querySelector('#story-filters');
const search = document.querySelector('#search');
const resultCount = document.querySelector('#result-count');
const moreButton = document.querySelector('#more-btn');
const emptyState = document.querySelector('#empty-state');
const CATEGORY_NAMES = {};
filters.querySelectorAll('input[name="category"]').forEach((input) => {
    CATEGORY_NAMES[input.value] = input.parentElement.textContent.trim();
});
let limit = PAGE_SIZE;

// each card's searchable text: title, summary, place, category and writer
items.forEach((item) => {
    item.dataset.search = item.textContent.replace(/\s+/g, ' ').toLowerCase();
});

function matching() {
    const category = filters.elements.category.value;
    const words = search.value.trim().toLowerCase().split(/\s+/).filter(Boolean);
    return items.filter((item) => {
        const card = item.firstElementChild;
        return (category === 'all' || card.dataset.category === category)
            && words.every((word) => item.dataset.search.includes(word));
    });
}

function describe(shown, total) {
    const category = filters.elements.category.value;
    const query = search.value.trim();
    const noun = total === 1 ? 'story' : 'stories';
    let text = category === 'all' && !query ? `all ${total} ${noun}` : `${total} ${noun}`;
    if (category !== 'all') {
        text += ` in ${CATEGORY_NAMES[category]}`;
    }
    if (query) {
        text += ` matching “${query}”`;
    }
    return shown < total ? `Showing ${shown} of ${text.replace(/^all /, '')}` : `Showing ${text}`;
}

function render() {
    const found = matching();
    items.forEach((item) => {
        item.hidden = true;
    });
    found.slice(0, limit).forEach((item) => {
        item.hidden = false;
    });
    const shown = Math.min(limit, found.length);
    resultCount.textContent = found.length ? describe(shown, found.length) : 'No stories found';
    emptyState.hidden = found.length > 0;
    moreButton.hidden = shown >= found.length;
    moreButton.textContent = `Show more stories (${found.length - shown} more)`;
}

function rememberCategory(url) {
    try {
        history.replaceState(null, '', url);
    } catch (error) {
        // pages opened from a file may block this; the filter still works
    }
}

function setCategory(value) {
    const input = filters.querySelector(`input[name="category"][value="${value}"]`);
    if (input) {
        input.checked = true;
    }
    limit = PAGE_SIZE;
    render();
}

filters.addEventListener('change', (event) => {
    if (event.target.name === 'category') {
        setCategory(event.target.value);
        const value = event.target.value;
        rememberCategory(value === 'all' ? `${location.pathname}#stories` : `?category=${value}#stories`);
    }
});

let typing = null;
search.addEventListener('input', () => {
    clearTimeout(typing);
    typing = setTimeout(() => {
        limit = PAGE_SIZE;
        render();
    }, 200);
});
filters.addEventListener('submit', (event) => {
    event.preventDefault();
    limit = PAGE_SIZE;
    render();
});

moreButton.addEventListener('click', () => {
    const firstNew = limit;
    limit += PAGE_SIZE;
    render();
    // move focus to the first story that just appeared, so keyboard users carry on from there
    const next = matching()[firstNew];
    if (next) {
        next.querySelector('.card-title a').focus();
    }
});

document.querySelector('#clear-filters').addEventListener('click', () => {
    search.value = '';
    setCategory('all');
    rememberCategory(`${location.pathname}#stories`);
    search.focus();
});

// category tiles filter the list without reloading the page
document.querySelector('#stories-title').tabIndex = -1;
document.querySelectorAll('[data-filter-link]').forEach((link) => {
    link.addEventListener('click', (event) => {
        event.preventDefault();
        search.value = '';
        setCategory(link.dataset.filterLink);
        rememberCategory(link.getAttribute('href'));
        document.querySelector('#stories').scrollIntoView({ block: 'start' });
        document.querySelector('#stories-title').focus({ preventScroll: true });
    });
});

// mark the stories this reader has already finished
const done = readStories();
document.querySelectorAll('.story-card').forEach((card) => {
    if (done.includes(card.dataset.slug)) {
        const badge = document.createElement('span');
        badge.className = 'read-badge';
        badge.textContent = 'Read';
        card.querySelector('.card-meta').append(badge);
    }
});

// a category in the address (from a story's breadcrumb or tag) is applied on arrival
const fromAddress = new URLSearchParams(location.search).get('category');
setCategory(fromAddress && CATEGORY_NAMES[fromAddress] ? fromAddress : 'all');

// ---------- newsletter ----------
const newsletter = document.querySelector('#newsletter-form');
const email = document.querySelector('#email');
const emailError = document.querySelector('#email-error');
const doneMessage = document.querySelector('#newsletter-done');

function showSubscribed(address) {
    newsletter.hidden = true;
    doneMessage.hidden = false;
    doneMessage.textContent = `Thanks! ${address} is on the list for the Sunday letter. (This is a demo, so no email will be sent.)`;
}

function emailProblem() {
    if (email.validity.valueMissing) {
        return 'Enter your email address.';
    }
    if (email.validity.typeMismatch || !/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(email.value.trim())) {
        return 'Enter an email address like name@example.com.';
    }
    return '';
}

newsletter.addEventListener('submit', (event) => {
    event.preventDefault();
    const problem = emailProblem();
    emailError.textContent = problem;
    email.setAttribute('aria-invalid', problem ? 'true' : 'false');
    if (problem) {
        email.focus();
        return;
    }
    const address = email.value.trim();
    save('lwr-newsletter', address);
    showSubscribed(address);
    doneMessage.focus();
});

email.addEventListener('input', () => {
    if (email.getAttribute('aria-invalid') === 'true' && !emailProblem()) {
        emailError.textContent = '';
        email.setAttribute('aria-invalid', 'false');
    }
});

const subscribed = load('lwr-newsletter', '');
if (subscribed) {
    showSubscribed(subscribed);
}
