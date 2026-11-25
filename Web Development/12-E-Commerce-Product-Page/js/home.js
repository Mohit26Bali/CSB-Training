// Green Corner home page - product filters, sorting and "Shop by need" links (the basket is in basket.js)

const grid = document.querySelector('#plant-grid');
const cards = Array.from(grid.children);
const filters = document.querySelector('#shop-filters');
const sortSelect = document.querySelector('#sort');
const resultCount = document.querySelector('#result-count');
const FILTER_NAMES = { indoor: 'indoor plants', succulents: 'succulents', 'pet-safe': 'pet-safe plants', 'low-light': 'low-light plants', accessories: 'pots and care products' };

function matches(card, filter) {
    const data = card.dataset;
    return filter === 'all'
        || (filter === 'indoor' && data.category === 'indoor')
        || (filter === 'succulents' && data.category === 'succulents')
        || (filter === 'pet-safe' && data.pets === 'true')
        || (filter === 'low-light' && data.light === 'low')
        || (filter === 'accessories' && data.category === 'accessories');
}

function applyFilters() {
    const filter = filters.elements.filter.value;
    let shown = 0;
    cards.forEach((card) => {
        card.hidden = !matches(card, filter);
        shown += card.hidden ? 0 : 1;
    });
    resultCount.textContent = filter === 'all'
        ? `Showing all ${cards.length} products`
        : `Showing ${shown} ${FILTER_NAMES[filter]}`;
}

function sortCards() {
    const key = sortSelect.value;
    [...cards].sort((a, b) => {
        if (key === 'price-low') {
            return a.dataset.price - b.dataset.price;
        }
        if (key === 'price-high') {
            return b.dataset.price - a.dataset.price;
        }
        if (key === 'rating') {
            return b.dataset.rating - a.dataset.rating || a.dataset.order - b.dataset.order;
        }
        return a.dataset.order - b.dataset.order;
    }).forEach((card) => grid.appendChild(card));
}

// remember the filter in the address so it can be shared; some browsers block this for pages opened from a file
function rememberFilter(url) {
    try {
        history.replaceState(null, '', url);
    } catch (error) {
        // the filter still works; only the address is not updated
    }
}

function setFilter(value) {
    const input = filters.querySelector(`input[name="filter"][value="${value}"]`);
    if (input) {
        input.checked = true;
        applyFilters();
    }
}

filters.addEventListener('change', (event) => {
    if (event.target === sortSelect) {
        sortCards();
    } else {
        applyFilters();
        const value = filters.elements.filter.value;
        rememberFilter(value === 'all' ? `${location.pathname}#plants` : `?filter=${value}#plants`);
    }
});
filters.addEventListener('submit', (event) => event.preventDefault());

// "Shop by need" tiles and hero buttons choose a filter without reloading the page
document.querySelectorAll('[data-filter-link]').forEach((link) => {
    link.addEventListener('click', (event) => {
        event.preventDefault();
        setFilter(link.dataset.filterLink);
        rememberFilter(link.getAttribute('href'));
        document.querySelector('#plants').scrollIntoView({ block: 'start' });
        document.querySelector('#plants-title').focus({ preventScroll: true });
    });
});
document.querySelector('#plants-title').tabIndex = -1;

// a filter in the address (from a product page breadcrumb, for example) is applied on arrival
const fromAddress = new URLSearchParams(location.search).get('filter');
setFilter(fromAddress && FILTER_NAMES[fromAddress] ? fromAddress : 'all');
