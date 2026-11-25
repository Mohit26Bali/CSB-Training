// Event Management Portal - filters, schedule tabs, countdown, seat booking, my tickets and calendar files

const STORAGE_KEY = 'neonframe-bookings';
const VENUE_ADDRESS = 'Lumen Arts Centre, 12 Neon Street, Manchester M1 4NF';

const grid = document.querySelector('#event-grid');
const events = Array.from(grid.querySelectorAll('.event'));
const eventById = new Map(events.map((item) => [item.dataset.id, item]));

// bookings are kept in the browser only
function loadBookings() {
    try {
        return JSON.parse(localStorage.getItem(STORAGE_KEY)) || {};
    } catch (error) {
        return {};
    }
}

function saveBookings() {
    try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(bookings));
    } catch (error) {
        // storage can be blocked; bookings then last until the page is closed
    }
}

let bookings = loadBookings();

// details of an event, read from its card
function details(item) {
    const times = item.querySelectorAll('.event-when time');
    return {
        id: item.dataset.id,
        title: item.querySelector('h3').textContent,
        category: item.dataset.category,
        booking: item.dataset.booking,
        price: Number(item.dataset.price),
        max: Number(item.dataset.max),
        capacity: Number(item.querySelector('.seats-meter')?.max || 0),
        start: times[0].dateTime,
        end: times[1].dateTime,
        when: item.querySelector('.event-when').textContent.replace(/\s+/g, ' ').trim(),
        day: item.querySelector('.date-badge').textContent.replace(/\s+/g, ' ').trim(),
        venue: item.querySelector('.event-where').textContent
    };
}

function bookedCount(booking) {
    return booking.seats ? booking.seats.length : booking.quantity;
}

function seatsLeft(item) {
    const mine = bookings[item.dataset.id];
    const taken = mine && mine.type === 'booking' ? bookedCount(mine) : 0;
    return Number(item.dataset.left) - taken;
}

function money(amount) {
    return amount === 0 ? 'Free' : `£${amount}`;
}

function setButtonText(button, label, title) {
    button.textContent = label;
    const hidden = document.createElement('span');
    hidden.className = 'visually-hidden';
    hidden.textContent = `: ${title}`;
    button.append(hidden);
}

// update one card's seats, status and button
function updateCard(item) {
    const info = details(item);
    const calendarButton = item.querySelector('.btn-calendar');
    calendarButton.hidden = false;
    if (info.booking === 'dropin') {
        return;
    }

    const left = seatsLeft(item);
    const mine = bookings[info.id];
    const meter = item.querySelector('.seats-meter');
    const unit = info.booking === 'seats' ? 'seats' : 'places';
    meter.value = info.capacity - left;
    meter.setAttribute('aria-label', `${info.capacity - left} of ${info.capacity} ${unit} booked`);

    let text = `${left} of ${info.capacity} left`;
    if (left === 0) {
        text = 'Sold out';
    } else if (left <= 10) {
        text = `Only ${left} left`;
    }
    item.querySelector('.seats-text').textContent = text;
    item.classList.toggle('is-low', left > 0 && left <= 10);
    item.classList.toggle('is-sold-out', left === 0);

    const button = item.querySelector('[data-action="book"], [data-action="waitlist"]');
    button.classList.toggle('is-booked', Boolean(mine));
    if (mine && mine.type === 'booking') {
        setButtonText(button, '✓ Booked', info.title);
    } else if (mine) {
        setButtonText(button, '✓ On waiting list', info.title);
    } else if (button.dataset.action === 'waitlist') {
        setButtonText(button, 'Join waiting list', info.title);
    } else {
        const label = info.booking === 'seats' ? 'Choose seats' : (info.price === 0 ? 'Register' : 'Book tickets');
        setButtonText(button, label, info.title);
    }

    if (info.id === 'spirited-away') {
        document.querySelector('#featured-left').textContent = left === 0 ? 'Sold out' : `Only ${left} seats left`;
    }
}

function updateAllCards() {
    events.forEach(updateCard);
    const count = Object.keys(bookings).length;
    const ticketsButton = document.querySelector('#open-tickets');
    ticketsButton.hidden = false;
    ticketsButton.classList.toggle('has-tickets', count > 0);
    document.querySelector('#ticket-count').textContent = count;
}

// filters and sorting
const form = document.querySelector('.filters');
const searchInput = document.querySelector('#search');
const daySelect = document.querySelector('#filter-day');
const sortSelect = document.querySelector('#sort');
const availableCheck = document.querySelector('#filter-available');
const freeCheck = document.querySelector('#filter-free');
const resultCount = document.querySelector('#result-count');
const noResults = document.querySelector('#no-results');

function applyFilters() {
    const words = searchInput.value.trim().toLowerCase().split(/\s+/).filter(Boolean);
    const category = form.elements.category.value;
    let shown = 0;

    events.forEach((item) => {
        const data = item.dataset;
        const soldOut = data.booking !== 'dropin' && seatsLeft(item) === 0 && !bookings[data.id];
        const matches =
            words.every((word) => data.search.includes(word)) &&
            (category === 'all' || data.category === category) &&
            (daySelect.value === 'all' || data.day === daySelect.value) &&
            (!availableCheck.checked || !soldOut) &&
            (!freeCheck.checked || data.price === '0');

        item.hidden = !matches;
        if (matches) {
            shown += 1;
        }
    });

    noResults.hidden = shown > 0;
    resultCount.textContent = shown === events.length
        ? `Showing all ${shown} events`
        : `Showing ${shown} of ${events.length} events`;
}

function sortEvents() {
    const sorted = [...events].sort((a, b) => {
        if (sortSelect.value === 'price-low') {
            return Number(a.dataset.price) - Number(b.dataset.price) || a.dataset.start.localeCompare(b.dataset.start);
        }
        if (sortSelect.value === 'seats') {
            const leftA = a.dataset.booking === 'dropin' ? Infinity : seatsLeft(a);
            const leftB = b.dataset.booking === 'dropin' ? Infinity : seatsLeft(b);
            return leftA - leftB || a.dataset.start.localeCompare(b.dataset.start);
        }
        return a.dataset.start.localeCompare(b.dataset.start);
    });
    sorted.forEach((item) => grid.appendChild(item));
}

form.addEventListener('input', applyFilters);
form.addEventListener('change', (event) => {
    if (event.target === sortSelect) {
        sortEvents();
    }
    applyFilters();
});
form.addEventListener('submit', (event) => event.preventDefault());

// the reset event fires before the fields clear, so update on the next tick
form.addEventListener('reset', () => setTimeout(() => {
    sortEvents();
    applyFilters();
}, 0));

document.querySelector('#clear-from-empty').addEventListener('click', () => {
    form.reset();
    searchInput.focus();
});

// schedule links: show the card even if the filters hide it, then move focus to it
document.querySelector('#schedule').addEventListener('click', (event) => {
    const link = event.target.closest('.slot a');
    if (!link) {
        return;
    }
    event.preventDefault();
    const item = document.querySelector(link.hash);
    if (item.hidden) {
        form.reset();
        searchInput.value = '';
        form.elements.category.value = 'all';
        daySelect.value = 'all';
        availableCheck.checked = false;
        freeCheck.checked = false;
        applyFilters();
    }
    const card = item.querySelector('.event-card');
    card.scrollIntoView({ block: 'start' });
    card.focus({ preventScroll: true });
});

// schedule tabs, with arrow-key support
const tabs = Array.from(document.querySelectorAll('.tab'));

function selectTab(tab, moveFocus) {
    tabs.forEach((other) => {
        const selected = other === tab;
        other.setAttribute('aria-selected', String(selected));
        other.tabIndex = selected ? 0 : -1;
        document.querySelector(`#${other.getAttribute('aria-controls')}`).hidden = !selected;
    });
    if (moveFocus) {
        tab.focus();
    }
}

tabs.forEach((tab, index) => {
    tab.addEventListener('click', () => selectTab(tab, false));
    tab.addEventListener('keydown', (event) => {
        const keys = {
            ArrowRight: tabs[(index + 1) % tabs.length],
            ArrowLeft: tabs[(index - 1 + tabs.length) % tabs.length],
            Home: tabs[0],
            End: tabs[tabs.length - 1]
        };
        if (keys[event.key]) {
            event.preventDefault();
            selectTab(keys[event.key], true);
        }
    });
});

selectTab(tabs[0], false);

// countdown to opening night (November in the UK is GMT, the same as UTC)
const opening = new Date('2026-11-20T19:00:00Z');
const countdown = document.querySelector('#countdown');

function updateCountdown() {
    const diff = opening - new Date();
    if (diff <= 0) {
        countdown.querySelector('.countdown-label').textContent = 'The festival has started. See you there!';
        countdown.querySelector('.countdown-units').hidden = true;
        return;
    }
    const minutesLeft = Math.floor(diff / 60000);
    document.querySelector('#cd-days').textContent = Math.floor(minutesLeft / 1440);
    document.querySelector('#cd-hours').textContent = Math.floor((minutesLeft % 1440) / 60);
    document.querySelector('#cd-minutes').textContent = minutesLeft % 60;
}

countdown.hidden = false;
updateCountdown();
setInterval(updateCountdown, 30000);

// seat map: the same seats are always taken for each event, so the map is stable between visits
function seededRandom(text) {
    let seed = 0;
    for (const char of text) {
        seed = (seed * 31 + char.charCodeAt(0)) >>> 0;
    }
    return () => {
        seed = (seed + 0x6d2b79f5) >>> 0;
        let t = seed;
        t = Math.imul(t ^ (t >>> 15), t | 1);
        t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
        return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
}

const ROWS = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H'];
const WHEELCHAIR = ['H1', 'H12'];

function takenSeats(info, left) {
    const all = ROWS.flatMap((row) => Array.from({ length: 12 }, (unused, i) => `${row}${i + 1}`));
    const random = seededRandom(info.id);
    for (let i = all.length - 1; i > 0; i -= 1) {
        const j = Math.floor(random() * (i + 1));
        [all[i], all[j]] = [all[j], all[i]];
    }
    return new Set(all.slice(0, info.capacity - left));
}

const seatMap = document.querySelector('#seat-map');

function buildSeatMap(info, left) {
    const taken = takenSeats(info, left);
    seatMap.innerHTML = '';
    ROWS.forEach((row) => {
        const labelLeft = document.createElement('span');
        labelLeft.className = 'row-label';
        labelLeft.setAttribute('aria-hidden', 'true');
        labelLeft.textContent = row;
        seatMap.append(labelLeft);

        for (let number = 1; number <= 12; number += 1) {
            if (number === 7) {
                const aisle = document.createElement('span');
                aisle.setAttribute('aria-hidden', 'true');
                seatMap.append(aisle);
            }
            const id = `${row}${number}`;
            const wheelchair = WHEELCHAIR.includes(id);
            const seat = document.createElement('label');
            seat.className = wheelchair ? 'seat is-wheelchair' : 'seat';

            const input = document.createElement('input');
            input.type = 'checkbox';
            input.name = 'seat';
            input.value = id;
            input.disabled = taken.has(id);

            const hidden = document.createElement('span');
            hidden.className = 'visually-hidden';
            hidden.textContent = `Row ${row}, ${wheelchair ? 'wheelchair space' : 'seat'} ${number}${taken.has(id) ? ', taken' : ''}`;

            const visible = document.createElement('span');
            visible.setAttribute('aria-hidden', 'true');
            visible.textContent = wheelchair ? '♿' : number;

            seat.append(input, hidden, visible);
            seatMap.append(seat);
        }

        const labelRight = labelLeft.cloneNode(true);
        seatMap.append(labelRight);
    });
}

// booking dialog
const bookingDialog = document.querySelector('#booking');
const bookingForm = document.querySelector('#booking-form');
const seatPicker = document.querySelector('#seat-picker');
const quantityField = document.querySelector('#quantity-field');
const quantityInput = document.querySelector('#quantity');
const nameInput = document.querySelector('#booker-name');
const emailInput = document.querySelector('#booker-email');
const bookingTotal = document.querySelector('#booking-total');
const bookingSummary = document.querySelector('#booking-summary');
const bookingStep = document.querySelector('#booking-step');
const bookingDone = document.querySelector('#booking-done');
let current = null;
let openedFrom = null;

function chosenSeats() {
    return Array.from(seatMap.querySelectorAll('input:checked')).map((input) => input.value);
}

function updateTotal() {
    if (current.mode === 'waitlist') {
        return;
    }
    let count = Number(quantityInput.value) || 0;
    if (current.mode === 'seats') {
        const seats = chosenSeats();
        count = seats.length;
        bookingSummary.textContent = count === 0 ? 'No seats chosen' : `${count} ${count === 1 ? 'seat' : 'seats'}: ${seats.join(', ')}`;
    } else {
        bookingSummary.textContent = `${count} ${count === 1 ? 'ticket' : 'tickets'} × ${money(current.price)}`;
    }
    bookingTotal.value = money(count * current.price);
}

function openBooking(item, mode, opener) {
    const info = details(item);
    const left = seatsLeft(item);
    current = { ...info, mode };
    openedFrom = opener;

    bookingForm.reset();
    clearErrors();
    bookingStep.hidden = false;
    bookingDone.hidden = true;

    document.querySelector('#booking-title').textContent = info.title;
    document.querySelector('#booking-meta').textContent = `${info.when} · ${info.venue}`;
    document.querySelector('#booking-type').textContent = mode === 'waitlist' ? 'Join the waiting list' : 'Book tickets';
    document.querySelector('#booking-submit').textContent = mode === 'waitlist' ? 'Join waiting list' : 'Confirm booking';

    seatPicker.hidden = mode !== 'seats';
    quantityField.hidden = mode !== 'tickets';
    document.querySelector('.booking-total').hidden = mode === 'waitlist';

    if (mode === 'seats') {
        buildSeatMap(info, left);
        document.querySelector('#seat-help').textContent =
            `Up to ${Math.min(info.max, left)} seats. Seats H1 and H12 are wheelchair spaces.`;
    }
    if (mode === 'tickets') {
        quantityInput.max = Math.min(info.max, left);
        quantityInput.value = 1;
        document.querySelector('#quantity-hint').textContent = info.max === 1
            ? 'One entry per player.'
            : `Up to ${quantityInput.max} per booking.`;
    }

    updateTotal();
    bookingDialog.showModal();
}

seatMap.addEventListener('change', (event) => {
    const limit = Math.min(current.max, seatsLeft(eventById.get(current.id)));
    const error = document.querySelector('#seat-error');
    if (chosenSeats().length > limit) {
        event.target.checked = false;
        error.textContent = `You can choose up to ${limit} seats.`;
    } else {
        error.textContent = '';
    }
    updateTotal();
});

quantityInput.addEventListener('input', updateTotal);

// validation for the booking form, using the browser's own checks
function showError(input, message) {
    const error = document.querySelector(`#${input.id}-error`);
    error.textContent = message;
    if (message) {
        input.setAttribute('aria-invalid', 'true');
    } else {
        input.removeAttribute('aria-invalid');
    }
}

function clearErrors() {
    [nameInput, emailInput, quantityInput].forEach((input) => showError(input, ''));
    document.querySelector('#seat-error').textContent = '';
}

function validateBooking() {
    const problems = [];

    if (nameInput.validity.valueMissing) {
        showError(nameInput, 'Enter your name');
        problems.push(nameInput);
    } else {
        showError(nameInput, '');
    }

    if (emailInput.validity.valueMissing) {
        showError(emailInput, 'Enter your email address');
        problems.push(emailInput);
    } else if (!emailInput.validity.valid) {
        showError(emailInput, 'Enter an email address like name@example.com');
        problems.push(emailInput);
    } else {
        showError(emailInput, '');
    }

    if (current.mode === 'tickets') {
        const max = quantityInput.max;
        if (!quantityInput.validity.valid) {
            showError(quantityInput, `Enter a whole number from 1 to ${max}`);
            problems.push(quantityInput);
        } else {
            showError(quantityInput, '');
        }
    }

    if (current.mode === 'seats' && chosenSeats().length === 0) {
        document.querySelector('#seat-error').textContent = 'Choose at least one seat';
        problems.push(seatMap.querySelector('input:not(:disabled)'));
    }

    if (problems.length > 0) {
        problems[0].focus();
        return false;
    }
    return true;
}

function bookingReference() {
    const letters = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    let reference = 'NF-';
    for (let i = 0; i < 5; i += 1) {
        reference += letters[Math.floor(Math.random() * letters.length)];
    }
    return reference;
}

bookingForm.addEventListener('submit', (event) => {
    event.preventDefault();
    if (!validateBooking()) {
        return;
    }

    const booking = {
        type: current.mode === 'waitlist' ? 'waitlist' : 'booking',
        name: nameInput.value.trim(),
        email: emailInput.value.trim(),
        reference: bookingReference()
    };
    if (current.mode === 'seats') {
        booking.seats = chosenSeats();
    }
    if (current.mode === 'tickets') {
        booking.quantity = Number(quantityInput.value);
    }
    if (booking.type === 'booking') {
        booking.total = bookedCount(booking) * current.price;
    }

    bookings[current.id] = booking;
    saveBookings();
    updateAllCards();
    applyFilters();
    showDone(booking);
});

function showDone(booking) {
    const doneTitle = document.querySelector('#done-title');
    const doneText = document.querySelector('#done-text');
    if (booking.type === 'waitlist') {
        doneTitle.textContent = "You're on the waiting list";
        doneText.textContent = `If a place comes up for ${current.title}, we will email ${booking.email}. Reference ${booking.reference}.`;
    } else {
        const what = booking.seats
            ? `${booking.seats.length === 1 ? 'Seat' : 'Seats'} ${booking.seats.join(', ')}`
            : `${booking.quantity} ${booking.quantity === 1 ? 'ticket' : 'tickets'}`;
        doneTitle.textContent = "You're booked!";
        doneText.textContent = `${what} for ${current.title}, ${current.when}. Total ${money(booking.total)}. Reference ${booking.reference}.`;
    }
    bookingStep.hidden = true;
    bookingDone.hidden = false;
    doneTitle.focus();
}

document.querySelector('#done-calendar').addEventListener('click', () => {
    downloadCalendar(eventById.get(current.id));
});

// buttons on the cards
grid.addEventListener('click', (event) => {
    const button = event.target.closest('[data-action]');
    if (!button) {
        return;
    }
    const item = eventById.get(button.dataset.id);
    if (button.dataset.action === 'calendar') {
        downloadCalendar(item);
    } else if (bookings[button.dataset.id]) {
        openTickets(button);
    } else if (button.dataset.action === 'waitlist') {
        openBooking(item, 'waitlist', button);
    } else {
        openBooking(item, item.dataset.booking, button);
    }
});

// my tickets
const ticketsDialog = document.querySelector('#tickets');
const ticketList = document.querySelector('#ticket-list');

function renderTickets() {
    ticketList.innerHTML = '';
    const ids = Object.keys(bookings).filter((id) => eventById.has(id))
        .sort((a, b) => eventById.get(a).dataset.start.localeCompare(eventById.get(b).dataset.start));

    ids.forEach((id) => {
        const info = details(eventById.get(id));
        const booking = bookings[id];
        let what = 'Waiting list';
        if (booking.type === 'booking') {
            what = booking.seats
                ? `${booking.seats.length === 1 ? 'Seat' : 'Seats'} ${booking.seats.join(', ')}`
                : `${booking.quantity} ${booking.quantity === 1 ? 'ticket' : 'tickets'}`;
            what += ` · ${money(booking.total)}`;
        }

        const item = document.createElement('li');
        item.className = `ticket cat-${info.category}`;
        const title = document.createElement('h3');
        title.textContent = info.title;
        const when = document.createElement('p');
        when.textContent = `${info.day}, ${info.when.replace(/^\w+ /, '')} · ${info.venue}`;
        const extra = document.createElement('p');
        extra.textContent = `${what} · Ref ${booking.reference}`;
        const cancel = document.createElement('button');
        cancel.type = 'button';
        cancel.className = 'btn-text';
        cancel.dataset.id = id;
        setButtonText(cancel, booking.type === 'booking' ? 'Cancel' : 'Leave list', info.title);

        item.append(title, cancel, when, extra);
        ticketList.append(item);
    });

    document.querySelector('#tickets-empty').hidden = ids.length > 0;
}

function openTickets(opener) {
    openedFrom = opener;
    renderTickets();
    ticketsDialog.showModal();
}

document.querySelector('#open-tickets').addEventListener('click', (event) => openTickets(event.currentTarget));

ticketList.addEventListener('click', (event) => {
    const button = event.target.closest('button');
    if (!button) {
        return;
    }
    const title = details(eventById.get(button.dataset.id)).title;
    if (!window.confirm(`Cancel your booking for ${title}?`)) {
        return;
    }
    delete bookings[button.dataset.id];
    saveBookings();
    updateAllCards();
    applyFilters();
    renderTickets();
    const next = ticketList.querySelector('button') || ticketsDialog.querySelector('.dialog-close');
    next.focus();
});

// closing dialogs: close buttons, the backdrop, and focus back to the button that opened them
[bookingDialog, ticketsDialog].forEach((dialog) => {
    dialog.addEventListener('click', (event) => {
        if (event.target === dialog || event.target.closest('[data-close]')) {
            dialog.close();
        }
    });
    dialog.addEventListener('close', () => {
        if (openedFrom && openedFrom.isConnected) {
            openedFrom.focus();
        }
    });
});

// calendar file (.ics) that opens in Google, Outlook or Apple Calendar
function icsDate(value) {
    return `${value.replace(/[-:]/g, '')}00Z`;
}

function icsText(text) {
    return text.replace(/[\\,;]/g, (char) => `\\${char}`);
}

function downloadCalendar(item) {
    const info = details(item);
    const stamp = new Date().toISOString().replace(/[-:]/g, '').replace(/\.\d+/, '');
    const lines = [
        'BEGIN:VCALENDAR',
        'VERSION:2.0',
        'PRODID:-//Neon Frame Festival//Events//EN',
        'BEGIN:VEVENT',
        `UID:${info.id}@neonframe.example`,
        `DTSTAMP:${stamp}`,
        `DTSTART:${icsDate(info.start)}`,
        `DTEND:${icsDate(info.end)}`,
        `SUMMARY:${icsText(info.title)} (Neon Frame Festival)`,
        `LOCATION:${icsText(`${info.venue}, ${VENUE_ADDRESS}`)}`,
        `DESCRIPTION:${icsText(item.querySelector('.event-desc').textContent)}`,
        'END:VEVENT',
        'END:VCALENDAR'
    ];
    const file = new Blob([lines.join('\r\n')], { type: 'text/calendar' });
    const link = document.createElement('a');
    link.href = URL.createObjectURL(file);
    link.download = `neon-frame-${info.id}.ics`;
    document.body.append(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(link.href);
}

updateAllCards();
applyFilters();
