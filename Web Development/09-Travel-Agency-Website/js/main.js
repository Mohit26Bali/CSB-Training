// Wanderlane Travel - menu, live destination weather and time, trip search, package filters, saved trips, enquiries and reviews

const MONTH_NAMES = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
const money = (value) => `£${Number(value).toLocaleString('en-GB')}`;

// mobile menu
const menuButton = document.querySelector('.menu-toggle');
const siteNav = document.querySelector('#site-nav');
menuButton.hidden = false;

function setMenu(open) {
    menuButton.setAttribute('aria-expanded', String(open));
    siteNav.classList.toggle('is-open', open);
}

menuButton.addEventListener('click', () => setMenu(menuButton.getAttribute('aria-expanded') !== 'true'));
siteNav.addEventListener('click', (event) => {
    if (event.target.closest('a')) {
        setMenu(false);
    }
});
document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && siteNav.classList.contains('is-open')) {
        setMenu(false);
        menuButton.focus();
    }
});

// destinations: local time (from each place's time zone) and live weather (Open-Meteo)
const destinations = Array.from(document.querySelectorAll('.destination'));

function updateLocalTimes() {
    destinations.forEach((card) => {
        const time = new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit', timeZone: card.dataset.tz }).format(new Date());
        card.querySelector('.dest-time').textContent = `${time} local time`;
    });
}

const WEATHER = [
    [[0], 'Clear', '☀️', '🌙'],
    [[1, 2], 'Partly cloudy', '⛅', '☁️'],
    [[3], 'Cloudy', '☁️', '☁️'],
    [[45, 48], 'Fog', '🌫️', '🌫️'],
    [[51, 53, 55, 56, 57], 'Drizzle', '🌦️', '🌧️'],
    [[61, 63, 65, 66, 67, 80, 81, 82], 'Rain', '🌧️', '🌧️'],
    [[71, 73, 75, 77, 85, 86], 'Snow', '🌨️', '🌨️'],
    [[95, 96, 99], 'Thunderstorm', '⛈️', '⛈️']
];

function describeWeather(code, isDay) {
    const match = WEATHER.find(([codes]) => codes.includes(code)) || [[], 'Mixed', '🌤️', '☁️'];
    return { label: match[1], icon: isDay ? match[2] : match[3] };
}

async function loadWeather() {
    const key = 'wanderlane:weather';
    try {
        const saved = JSON.parse(sessionStorage.getItem(key));
        if (saved && Date.now() - saved.time < 15 * 60000) {
            return saved.data;
        }
    } catch (error) {
        // storage blocked; fetch instead
    }
    const params = new URLSearchParams({
        latitude: destinations.map((card) => card.dataset.lat).join(','),
        longitude: destinations.map((card) => card.dataset.lon).join(','),
        current: 'temperature_2m,weather_code,is_day',
        timezone: 'auto'
    });
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 10000);
    try {
        const response = await fetch(`https://api.open-meteo.com/v1/forecast?${params}`, { signal: controller.signal });
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }
        const data = await response.json();
        try {
            sessionStorage.setItem(key, JSON.stringify({ time: Date.now(), data }));
        } catch (error) {
            // not cached; still shown
        }
        return data;
    } finally {
        clearTimeout(timer);
    }
}

async function showWeather() {
    try {
        const results = await loadWeather();
        results.forEach((place, index) => {
            const now = place.current;
            const weather = describeWeather(now.weather_code, now.is_day === 1);
            const element = destinations[index].querySelector('.dest-weather');
            element.innerHTML = `<span aria-hidden="true">${weather.icon}</span> ${Math.round(now.temperature_2m)}°C, ${weather.label}`;
        });
        document.querySelector('#weather-note').innerHTML = `Live weather from <a href="https://open-meteo.com/">Open-Meteo</a>, updated ${new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit' }).format(new Date())}.`;
    } catch (error) {
        document.querySelector('#weather-note').textContent = 'Live weather is unavailable right now. Local times are still shown.';
    }
}

updateLocalTimes();
setInterval(updateLocalTimes, 30000);
showWeather();

// packages: filters, sorting and saved trips
const grid = document.querySelector('#package-grid');
const packages = Array.from(grid.querySelectorAll('.package'));
packages.forEach((item, index) => {
    item.dataset.order = index;
});
const filters = document.querySelector('#filters');
const countrySelect = document.querySelector('#filter-country');
const nightsSelect = document.querySelector('#filter-nights');
const budgetInput = document.querySelector('#filter-budget');
const budgetOutput = document.querySelector('#budget-output');
const sortSelect = document.querySelector('#sort');
const savedOnly = document.querySelector('#filter-saved');
const resultCount = document.querySelector('#result-count');
const noResults = document.querySelector('#no-results');

const trip = { month: null, travellers: 2 };

function nightsMatch(nights, choice) {
    return choice === 'all'
        || (choice === 'short' && nights <= 7)
        || (choice === 'medium' && nights >= 8 && nights <= 10)
        || (choice === 'long' && nights >= 11);
}

function applyFilters() {
    const type = filters.elements.type.value;
    const budget = Number(budgetInput.value);
    budgetOutput.value = budget >= Number(budgetInput.max) ? `${money(budget)}+` : money(budget);
    let shown = 0;

    packages.forEach((item) => {
        const data = item.dataset;
        const matches = (type === 'all' || data.type === type)
            && (countrySelect.value === 'all' || data.country === countrySelect.value)
            && nightsMatch(Number(data.nights), nightsSelect.value)
            && (budget >= Number(budgetInput.max) || Number(data.price) <= budget)
            && (!savedOnly.checked || savedTrips.has(data.id));
        item.hidden = !matches;
        if (matches) {
            shown += 1;
        }
    });

    noResults.hidden = shown > 0;
    resultCount.textContent = shown === packages.length
        ? `Showing all ${shown} trips`
        : `Showing ${shown} of ${packages.length} trips`;
}

function updateTripDetails() {
    packages.forEach((item) => {
        const price = Number(item.dataset.price);
        item.querySelector('.package-total').textContent = trip.travellers > 1
            ? `${money(price * trip.travellers)} for ${trip.travellers} travellers`
            : '';

        const note = item.querySelector('.season-note');
        if (trip.month) {
            const best = item.dataset.best.split(',').map(Number);
            const good = best.includes(trip.month);
            const bestText = item.querySelector('.package-facts li:last-child').textContent.replace('Best: ', '');
            note.textContent = good
                ? `Great time to go in ${MONTH_NAMES[trip.month - 1]}`
                : `${MONTH_NAMES[trip.month - 1]} is outside the best season (${bestText})`;
            note.className = `season-note ${good ? 'is-good' : 'is-off'}`;
            note.hidden = false;
        } else {
            note.hidden = true;
        }
    });
}

function sortPackages() {
    const key = sortSelect.value;
    const sorted = [...packages].sort((a, b) => {
        if (key === 'price') {
            return a.dataset.price - b.dataset.price;
        }
        if (key === 'nights') {
            return a.dataset.nights - b.dataset.nights || a.dataset.price - b.dataset.price;
        }
        return a.dataset.order - b.dataset.order;
    });
    sorted.forEach((item) => grid.appendChild(item));
}

filters.addEventListener('input', applyFilters);
filters.addEventListener('change', (event) => {
    if (event.target === sortSelect) {
        sortPackages();
    }
    applyFilters();
});
filters.addEventListener('submit', (event) => event.preventDefault());
filters.addEventListener('reset', () => setTimeout(() => {
    sortPackages();
    applyFilters();
}, 0));
document.querySelector('#clear-from-empty').addEventListener('click', () => {
    filters.reset();
    countrySelect.focus();
});

// saved trips (heart buttons), remembered in this browser
let savedTrips = new Set();
try {
    savedTrips = new Set(JSON.parse(localStorage.getItem('wanderlane-saved')) || []);
} catch (error) {
    // start with nothing saved
}

function showSaved() {
    packages.forEach((item) => {
        item.querySelector('.save-btn').setAttribute('aria-pressed', String(savedTrips.has(item.dataset.id)));
    });
    document.querySelector('#saved-count').textContent = savedTrips.size;
}

document.querySelectorAll('.save-btn').forEach((button) => {
    button.hidden = false;
});
document.querySelector('.saved-only').hidden = false;

grid.addEventListener('click', (event) => {
    const button = event.target.closest('.save-btn');
    if (!button) {
        return;
    }
    const id = button.closest('.package').dataset.id;
    if (savedTrips.has(id)) {
        savedTrips.delete(id);
    } else {
        savedTrips.add(id);
    }
    try {
        localStorage.setItem('wanderlane-saved', JSON.stringify([...savedTrips]));
    } catch (error) {
        // saved for this visit only
    }
    showSaved();
    applyFilters();
});

// "See trips" on a destination filters the packages to that country
document.querySelectorAll('.dest-link[data-country]').forEach((link) => {
    link.addEventListener('click', () => {
        filters.reset();
        setTimeout(() => {
            countrySelect.value = link.dataset.country;
            applyFilters();
        }, 0);
    });
});

// hero trip search
const searchForm = document.querySelector('#trip-search');
searchForm.noValidate = true;
const searchTravellers = document.querySelector('#search-travellers');
const searchMonth = document.querySelector('#search-month');
const thisMonth = new Date().toISOString().slice(0, 7);
searchMonth.min = thisMonth;
document.querySelector('#enq-month').min = thisMonth;

searchForm.addEventListener('submit', (event) => {
    event.preventDefault();
    const error = document.querySelector('#travellers-error');
    if (!searchTravellers.validity.valid) {
        error.textContent = 'Travellers must be a whole number from 1 to 8.';
        searchTravellers.setAttribute('aria-invalid', 'true');
        searchTravellers.focus();
        return;
    }
    error.textContent = '';
    searchTravellers.removeAttribute('aria-invalid');

    trip.travellers = Number(searchTravellers.value);
    trip.month = searchMonth.value ? Number(searchMonth.value.slice(5, 7)) : null;
    filters.reset();
    setTimeout(() => {
        countrySelect.value = document.querySelector('#search-country').value;
        applyFilters();
        updateTripDetails();
        const heading = document.querySelector('#packages-title');
        heading.setAttribute('tabindex', '-1');
        heading.scrollIntoView({ block: 'start' });
        heading.focus({ preventScroll: true });
    }, 0);
});

// enquiry dialog
const dialog = document.querySelector('#enquiry');
const enquiryForm = document.querySelector('#enquiry-form');
const enquiryStep = document.querySelector('#enquiry-step');
const enquiryDone = document.querySelector('#enquiry-done');
const enqTravellers = document.querySelector('#enq-travellers');
const enqMonth = document.querySelector('#enq-month');
let currentPackage = null;
let openedFrom = null;

function updateEnquiryTotal() {
    const price = Number(currentPackage.dataset.price);
    const travellers = enqTravellers.validity.valid ? Number(enqTravellers.value) : 0;
    document.querySelector('#enq-summary').textContent = travellers
        ? `${money(price)} × ${travellers} ${travellers === 1 ? 'traveller' : 'travellers'}`
        : 'Enter the number of travellers';
    document.querySelector('#enq-total').value = money(price * travellers);
}

grid.addEventListener('click', (event) => {
    const button = event.target.closest('[data-enquire]');
    if (!button) {
        return;
    }
    currentPackage = button.closest('.package');
    openedFrom = button;
    enquiryForm.reset();
    enquiryForm.querySelectorAll('.field-error').forEach((element) => {
        element.textContent = '';
    });
    enquiryForm.querySelectorAll('[aria-invalid]').forEach((element) => element.removeAttribute('aria-invalid'));
    enquiryStep.hidden = false;
    enquiryDone.hidden = true;
    document.querySelector('#enquiry-title').textContent = currentPackage.querySelector('h3').textContent;
    enqTravellers.value = searchTravellers.validity.valid ? searchTravellers.value : trip.travellers;
    if (searchMonth.value) {
        enqMonth.value = searchMonth.value;
    }
    updateEnquiryTotal();
    dialog.showModal();
});

enqTravellers.addEventListener('input', updateEnquiryTotal);

const ENQUIRY_MESSAGES = {
    'enq-name': { valueMissing: 'Enter your name' },
    'enq-email': { valueMissing: 'Enter your email address', typeMismatch: 'Enter an email address like name@example.com', patternMismatch: 'Enter an email address like name@example.com' },
    'enq-month': { valueMissing: 'Choose the month you would like to travel', rangeUnderflow: 'Choose this month or a later one' },
    'enq-travellers': { valueMissing: 'Enter the number of travellers', rangeUnderflow: 'At least 1 traveller', rangeOverflow: 'For groups larger than 8, please call us', stepMismatch: 'Enter a whole number', badInput: 'Enter a number' }
};

function checkField(input, messages) {
    const rule = Object.keys(messages).find((key) => input.validity[key]);
    const error = document.querySelector(`#${input.id}-error`);
    if (rule || !input.validity.valid) {
        error.textContent = messages[rule] || input.validationMessage;
        input.setAttribute('aria-invalid', 'true');
        return false;
    }
    error.textContent = '';
    input.removeAttribute('aria-invalid');
    return true;
}

enquiryForm.addEventListener('submit', (event) => {
    event.preventDefault();
    const invalid = Object.entries(ENQUIRY_MESSAGES)
        .map(([id, messages]) => [document.querySelector(`#${id}`), messages])
        .filter(([input, messages]) => !checkField(input, messages))
        .map(([input]) => input);
    if (invalid.length) {
        invalid[0].focus();
        return;
    }
    const month = enqMonth.value;
    const monthName = `${MONTH_NAMES[Number(month.slice(5, 7)) - 1]} ${month.slice(0, 4)}`;
    const reference = `WL-${Math.random().toString(36).slice(2, 7).toUpperCase()}`;
    document.querySelector('#done-text').textContent =
        `We've received your enquiry about ${document.querySelector('#enquiry-title').textContent} for ${enqTravellers.value} in ${monthName} (reference ${reference}). An expert would email ${document.querySelector('#enq-email').value} within one working day. This is a demo, so nothing was sent.`;
    enquiryStep.hidden = true;
    enquiryDone.hidden = false;
    document.querySelector('#done-title').focus();
});

dialog.addEventListener('click', (event) => {
    if (event.target === dialog || event.target.closest('[data-close]')) {
        dialog.close();
    }
});
dialog.addEventListener('close', () => {
    if (openedFrom) {
        openedFrom.focus();
    }
});

// general enquiry form
const generalForm = document.querySelector('#general-form');
generalForm.addEventListener('submit', (event) => {
    event.preventDefault();
    const name = document.querySelector('#general-name');
    const email = document.querySelector('#general-email');
    const nameOk = checkField(name, { valueMissing: 'Enter your name' });
    const emailOk = checkField(email, ENQUIRY_MESSAGES['enq-email']);
    const message = document.querySelector('#general-message');
    if (!nameOk || !emailOk) {
        message.textContent = '';
        (nameOk ? email : name).focus();
        return;
    }
    message.textContent = `Thanks, ${name.value.trim()}! An expert would call you back within one working day. (Demo: nothing was sent.)`;
    generalForm.reset();
});

// reviews carousel: buttons scroll by one card; the list also scrolls by swipe, trackpad or arrow keys
const reviewList = document.querySelector('#review-list');
const prevButton = document.querySelector('#review-prev');
const nextButton = document.querySelector('#review-next');
prevButton.hidden = false;
nextButton.hidden = false;

function cardStep() {
    const card = reviewList.querySelector('li');
    return card.getBoundingClientRect().width + parseFloat(getComputedStyle(reviewList).columnGap || 0);
}

function updateCarouselButtons() {
    const max = reviewList.scrollWidth - reviewList.clientWidth - 8;
    prevButton.disabled = reviewList.scrollLeft <= 8;
    nextButton.disabled = reviewList.scrollLeft >= max;
}

prevButton.addEventListener('click', () => reviewList.scrollBy({ left: -cardStep() }));
nextButton.addEventListener('click', () => reviewList.scrollBy({ left: cardStep() }));
reviewList.addEventListener('scroll', updateCarouselButtons, { passive: true });
window.addEventListener('resize', updateCarouselButtons);

showSaved();
applyFilters();
updateTripDetails();
updateCarouselButtons();
