// Wanderlane Admin - calculates every widget from the booking data, and handles the sidebar, menus, filters and table

const today = new Date();
const bookings = buildBookings(today);
const packageById = Object.fromEntries(PACKAGES.map((pack) => [pack.id, pack]));
const root = document.documentElement;

const money = new Intl.NumberFormat('en-GB', { style: 'currency', currency: 'GBP', maximumFractionDigits: 0 });
const moneyShort = new Intl.NumberFormat('en-GB', { style: 'currency', currency: 'GBP', notation: 'compact', maximumFractionDigits: 1 });
const dayLong = new Intl.DateTimeFormat('en-GB', { weekday: 'short', day: 'numeric', month: 'short' });
const dayShort = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short' });
const monthLong = new Intl.DateTimeFormat('en-GB', { month: 'long', year: 'numeric' });
const monthShort = new Intl.DateTimeFormat('en-GB', { month: 'short' });
const dateTime = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });

function load(key, fallback) {
    try {
        const value = localStorage.getItem(key);
        return value === null ? fallback : JSON.parse(value);
    } catch (error) {
        return fallback;
    }
}

function save(key, value) {
    try {
        localStorage.setItem(key, JSON.stringify(value));
    } catch (error) {
        // not saved; works for this visit
    }
}

function ago(minutes) {
    if (minutes < 60) {
        return `${minutes} min ago`;
    }
    if (minutes < 1440) {
        return `${Math.round(minutes / 60)} h ago`;
    }
    return `${Math.round(minutes / 1440)} d ago`;
}

function escapeHTML(text) {
    return String(text).replace(/[&<>"']/g, (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char]);
}

// ---------- the selected period ----------
const state = {
    range: [7, 30, 365].includes(load('wl-admin-range', 30)) ? load('wl-admin-range', 30) : 30,
    query: '',
    status: 'all',
    sort: { key: 'date', direction: 'descending' },
    page: 0,
    perPage: 8
};

function period(days, back) {
    const end = addDays(startOfDay(today), 1 - days * back);
    const start = addDays(end, -days);
    return { start, end };
}

function inPeriod(booking, range) {
    return booking.date >= range.start && booking.date < range.end;
}

function stats(list) {
    const confirmed = list.filter((booking) => booking.status === 'Confirmed');
    const revenue = confirmed.reduce((sum, booking) => sum + booking.value, 0);
    const count = list.filter((booking) => booking.status !== 'Cancelled').length;
    return { revenue, count, average: confirmed.length ? revenue / confirmed.length : 0 };
}

// one point per day (7 or 30 days) or per month (12 months)
function series(days) {
    if (days === 365) {
        return Array.from({ length: 12 }, (unused, i) => {
            const first = new Date(today.getFullYear(), today.getMonth() - 11 + i, 1);
            const next = new Date(first.getFullYear(), first.getMonth() + 1, 1);
            const list = bookings.filter((booking) => booking.date >= first && booking.date < next);
            const current = i === 11;
            return { label: `${monthLong.format(first)}${current ? ' (so far)' : ''}`, short: monthShort.format(first), ...stats(list) };
        });
    }
    return Array.from({ length: days }, (unused, i) => {
        const day = addDays(startOfDay(today), i - days + 1);
        const list = bookings.filter((booking) => booking.date >= day && booking.date < addDays(day, 1));
        return { label: dayLong.format(day), short: dayShort.format(day), ...stats(list) };
    });
}

function rangeName(days) {
    return days === 365 ? 'last 12 months' : `last ${days} days`;
}

// ---------- KPI cards ----------
function changeHTML(now, before, days) {
    if (!before) {
        return '<p class="kpi-change">No earlier data</p>';
    }
    const change = ((now - before) / before) * 100;
    const up = change >= 0;
    return `<p class="kpi-change ${up ? 'is-up' : 'is-down'}"><span aria-hidden="true">${up ? '▲' : '▼'}</span><span class="visually-hidden">${up ? 'Up' : 'Down'}</span> ${Math.abs(change).toFixed(1)}% <span class="kpi-vs">vs previous ${days === 365 ? '12 months' : `${days} days`}</span></p>`;
}

function renderKpis() {
    const days = state.range;
    const current = stats(bookings.filter((booking) => inPeriod(booking, period(days, 0))));
    const previous = stats(bookings.filter((booking) => inPeriod(booking, period(days, 1))));
    const points = series(days);
    const open = openEnquiries();
    const cards = [
        { title: 'Revenue', value: money.format(current.revenue), change: changeHTML(current.revenue, previous.revenue, days), spark: points.map((p) => p.revenue), up: current.revenue >= previous.revenue },
        { title: 'Bookings', value: current.count.toLocaleString('en-GB'), change: changeHTML(current.count, previous.count, days), spark: points.map((p) => p.count), up: current.count >= previous.count },
        { title: 'Average booking', value: money.format(current.average), change: changeHTML(current.average, previous.average, days), spark: points.map((p) => p.average), up: current.average >= previous.average },
        { title: 'Open enquiries', value: String(open.length), change: `<p class="kpi-change">${open.filter((e) => e.hoursAgo >= 24).length} waiting over 24 hours · <a href="#enquiries">Answer</a></p>`, spark: null }
    ];
    document.querySelector('#kpi-grid').innerHTML = cards.map((card) => `<li class="kpi">
        <h3>${card.title}</h3>
        <p class="kpi-value">${card.value}</p>
        ${card.change}
        ${card.spark ? sparkline(card.spark, card.up) : ''}
    </li>`).join('');
}

// ---------- revenue chart ----------
let revenueChart = null;
const chartArea = document.querySelector('#revenue-chart');
const chartLive = document.createElement('p');
chartLive.className = 'visually-hidden';
chartLive.setAttribute('role', 'status');
chartArea.after(chartLive);

function renderRevenue() {
    const days = state.range;
    const points = series(days).map((point) => ({ ...point, value: point.revenue }));
    const total = points.reduce((sum, point) => sum + point.value, 0);
    const best = points.reduce((a, b) => (b.value > a.value ? b : a));
    document.querySelector('#revenue-sub').textContent = `${money.format(total)} confirmed in the ${rangeName(days)}`;
    document.querySelector('#revenue-summary').textContent =
        `Revenue chart for the ${rangeName(days)}. Total ${money.format(total)}. Highest: ${best.label}, ${money.format(best.value)}.`;
    revenueChart = drawAreaChart(chartArea, points, (value) => money.format(value), (value) => moneyShort.format(value));
    revenueChart.points = points;
    document.querySelector('#revenue-table').innerHTML = `<table>
        <caption class="visually-hidden">Revenue by ${days === 365 ? 'month' : 'day'}</caption>
        <thead><tr><th scope="col">${days === 365 ? 'Month' : 'Day'}</th><th scope="col" class="num">Revenue</th><th scope="col" class="num">Bookings</th></tr></thead>
        <tbody>${points.map((point) => `<tr><th scope="row">${point.label}</th><td class="num">${money.format(point.value)}</td><td class="num">${point.count}</td></tr>`).join('')}</tbody>
    </table>`;
}

chartArea.tabIndex = 0;
chartArea.setAttribute('role', 'group');
chartArea.setAttribute('aria-label', 'Revenue chart. Use the left and right arrow keys to read each point.');
chartArea.setAttribute('aria-describedby', 'revenue-summary');
chartArea.addEventListener('keydown', (event) => {
    if (!revenueChart || !['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) {
        return;
    }
    event.preventDefault();
    const last = revenueChart.count - 1;
    const current = chartArea.dataset.index === undefined ? last + 1 : Number(chartArea.dataset.index);
    const next = { ArrowLeft: current - 1, ArrowRight: current + 1, Home: 0, End: last }[event.key];
    const point = revenueChart.show(Math.min(Math.max(next, 0), last));
    chartLive.textContent = `${point.label}: ${money.format(point.value)}`;
});
chartArea.addEventListener('blur', () => revenueChart && revenueChart.hide());

// redraw the chart when its widget changes width
new ResizeObserver(() => {
    if (revenueChart) {
        const index = chartArea.dataset.index;
        renderRevenue();
        delete chartArea.dataset.index;
        if (document.activeElement === chartArea && index !== undefined) {
            revenueChart.show(Number(index));
        }
    }
}).observe(chartArea);

// ---------- destinations donut and package bars ----------
function renderBreakdowns() {
    const list = bookings.filter((booking) => inPeriod(booking, period(state.range, 0)));
    const live = list.filter((booking) => booking.status !== 'Cancelled');
    const byPackage = PACKAGES.map((pack) => {
        const own = live.filter((booking) => booking.packageId === pack.id);
        return {
            ...pack,
            count: own.length,
            revenue: own.filter((booking) => booking.status === 'Confirmed').reduce((sum, booking) => sum + booking.value, 0)
        };
    });

    const byCount = [...byPackage].sort((a, b) => b.count - a.count);
    drawDonut(document.querySelector('#donut'), byCount.map((pack) => ({ value: pack.count, colour: pack.colour })),
        { value: live.length.toLocaleString('en-GB'), text: 'bookings' });
    document.querySelector('#legend').innerHTML = byCount.map((pack) => `<li>
        <span class="swatch" style="--swatch: ${pack.colour}" aria-hidden="true"></span>
        <span class="legend-name">${pack.destination}</span>
        <span class="legend-value">${pack.count} <span>(${live.length ? Math.round((pack.count / live.length) * 100) : 0}%)</span></span>
    </li>`).join('');
    document.querySelector('#destinations-sub').textContent = `${live.length.toLocaleString('en-GB')} bookings in the ${rangeName(state.range)}`;

    const byRevenue = [...byPackage].sort((a, b) => b.revenue - a.revenue);
    const top = byRevenue[0].revenue || 1;
    document.querySelector('#bars').innerHTML = byRevenue.map((pack) => `<li>
        <span class="bar-name">${pack.name}</span>
        <span class="bar-value">${moneyShort.format(pack.revenue)}</span>
        <span class="bar-track" aria-hidden="true"><span class="bar-fill" style="--size: ${((pack.revenue / top) * 100).toFixed(1)}%; --colour: ${pack.colour}"></span></span>
    </li>`).join('');
}

// ---------- bookings table: search, status filter, sorting, pages ----------
const tableBody = document.querySelector('#bookings-body');

function tableRows() {
    const words = state.query.toLowerCase().split(/\s+/).filter(Boolean);
    const rows = bookings.filter((booking) => inPeriod(booking, period(state.range, 0)))
        .filter((booking) => state.status === 'all' || booking.status === state.status)
        .filter((booking) => {
            const pack = packageById[booking.packageId];
            const text = `${booking.id} ${booking.customer} ${pack.name} ${pack.destination} ${booking.status}`.toLowerCase();
            return words.every((word) => text.includes(word));
        });
    const { key, direction } = state.sort;
    const value = (booking) => (key === 'package' ? packageById[booking.packageId].name : booking[key]);
    rows.sort((a, b) => {
        const first = value(a);
        const second = value(b);
        const order = first instanceof Date || typeof first === 'number' ? first - second : String(first).localeCompare(String(second));
        return direction === 'ascending' ? order : -order;
    });
    return rows;
}

function renderTable() {
    const rows = tableRows();
    const pages = Math.max(1, Math.ceil(rows.length / state.perPage));
    state.page = Math.min(state.page, pages - 1);
    const shown = rows.slice(state.page * state.perPage, (state.page + 1) * state.perPage);

    tableBody.innerHTML = shown.map((booking) => {
        const pack = packageById[booking.packageId];
        return `<tr>
            <th scope="row" data-label="Booking">${booking.id}</th>
            <td data-label="Customer">${escapeHTML(booking.customer)}</td>
            <td data-label="Package">${escapeHTML(pack.name)}<span class="cell-sub">${booking.travellers} ${booking.travellers === 1 ? 'traveller' : 'travellers'}</span></td>
            <td data-label="Date"><time datetime="${booking.date.toISOString()}">${dateTime.format(booking.date)}</time></td>
            <td data-label="Value" class="num">${money.format(booking.value)}</td>
            <td data-label="Status"><span class="status status-${booking.status.toLowerCase()}">${booking.status}</span></td>
        </tr>`;
    }).join('');

    document.querySelector('#bookings-empty').hidden = rows.length > 0;
    const statusText = state.status === 'all' ? 'all statuses, including cancelled' : state.status.toLowerCase();
    document.querySelector('#bookings-sub').textContent = `${rows.length.toLocaleString('en-GB')} in the ${rangeName(state.range)} (${statusText})${state.query ? `, matching "${state.query}"` : ''}`;
    document.querySelector('#page-info').textContent = rows.length
        ? `Showing ${state.page * state.perPage + 1}–${state.page * state.perPage + shown.length} of ${rows.length.toLocaleString('en-GB')}`
        : 'No results';
    document.querySelector('#prev-page').disabled = state.page === 0;
    document.querySelector('#next-page').disabled = state.page >= pages - 1;

    document.querySelectorAll('.bookings-table th[aria-sort]').forEach((header) => {
        const key = header.querySelector('button').dataset.sort;
        header.setAttribute('aria-sort', key === state.sort.key ? state.sort.direction : 'none');
    });
}

document.querySelector('.bookings-table thead').addEventListener('click', (event) => {
    const button = event.target.closest('button[data-sort]');
    if (!button) {
        return;
    }
    const key = button.dataset.sort;
    const sameKey = state.sort.key === key;
    state.sort = {
        key,
        direction: sameKey && state.sort.direction === 'ascending' ? 'descending' : (sameKey ? 'ascending' : (['date', 'value'].includes(key) ? 'descending' : 'ascending'))
    };
    state.page = 0;
    renderTable();
});

document.querySelector('#status-filter').addEventListener('change', (event) => {
    state.status = event.target.value;
    state.page = 0;
    renderTable();
});

document.querySelector('#prev-page').addEventListener('click', () => {
    state.page -= 1;
    renderTable();
});
document.querySelector('#next-page').addEventListener('click', () => {
    state.page += 1;
    renderTable();
});

const searchInput = document.querySelector('#search-input');
searchInput.addEventListener('input', () => {
    state.query = searchInput.value.trim();
    state.page = 0;
    renderTable();
});
document.querySelector('#top-search').addEventListener('submit', (event) => {
    event.preventDefault();
    document.querySelector('#bookings').scrollIntoView({ block: 'start' });
});

// ---------- enquiries ----------
let answered = new Set(load('wl-admin-answered', []));

function openEnquiries() {
    return ENQUIRIES.filter((enquiry) => !answered.has(enquiry.id));
}

function renderEnquiries() {
    const open = openEnquiries();
    document.querySelector('#enquiry-list').innerHTML = open.map((enquiry) => `<li data-id="${enquiry.id}">
        <span class="avatar avatar-small" aria-hidden="true">${enquiry.name.split(' ').map((part) => part[0]).join('')}</span>
        <div class="enquiry-text">
            <p class="enquiry-name">${escapeHTML(enquiry.name)} <span class="${enquiry.hoursAgo >= 24 ? 'late' : ''}">${enquiry.hoursAgo} h ago</span></p>
            <p class="enquiry-topic">${escapeHTML(enquiry.topic)}</p>
        </div>
        <button class="btn-small" type="button" data-answer="${enquiry.id}">Mark answered<span class="visually-hidden">: ${escapeHTML(enquiry.name)}</span></button>
    </li>`).join('');
    document.querySelector('#enquiries-empty').hidden = open.length > 0;
    document.querySelector('#enquiries-sub').textContent = open.length ? `${open.length} waiting for a reply` : 'Nothing waiting';
    document.querySelector('#nav-enquiry-count').textContent = open.length;
    document.querySelector('#nav-enquiry-count').parentElement.hidden = open.length === 0;
}

document.querySelector('#enquiry-list').addEventListener('click', (event) => {
    const button = event.target.closest('[data-answer]');
    if (!button) {
        return;
    }
    const items = Array.from(document.querySelectorAll('#enquiry-list li'));
    const index = items.indexOf(button.closest('li'));
    answered.add(button.dataset.answer);
    save('wl-admin-answered', [...answered]);
    renderEnquiries();
    renderKpis();
    showToast('Enquiry marked as answered.');
    const remaining = document.querySelectorAll('#enquiry-list [data-answer]');
    (remaining[Math.min(index, remaining.length - 1)] || document.querySelector('#enquiries-title')).focus();
});

// ---------- team activity ----------
document.querySelector('#timeline').innerHTML = ACTIVITY.map((item) => `<li>
    <span class="avatar avatar-small" aria-hidden="true">${item.who.split(' ').map((part) => part[0]).join('')}</span>
    <p><strong>${item.who}</strong> ${item.what} <strong>${item.target}</strong><span class="timeline-time">${ago(item.minutesAgo)}</span></p>
</li>`).join('');

// ---------- notifications and profile dropdowns ----------
const pending = bookings.filter((booking) => booking.status === 'Pending').pop();
if (pending) {
    NOTIFICATIONS[0].text = `Booking ${pending.id} for ${pending.customer} is waiting for a deposit`;
}
let readNotes = new Set(load('wl-admin-read', []));

function renderNotifications() {
    const unread = NOTIFICATIONS.filter((note) => !readNotes.has(note.id)).length;
    const count = document.querySelector('#notify-count');
    count.textContent = unread || '';
    count.hidden = unread === 0;
    document.querySelector('#notify-btn .visually-hidden').textContent = unread ? `Notifications, ${unread} unread` : 'Notifications';
    document.querySelector('#notify-list').innerHTML = NOTIFICATIONS.map((note) => `<li class="${readNotes.has(note.id) ? '' : 'is-unread'}">
        <p>${escapeHTML(note.text)}</p><span>${ago(note.minutesAgo)}${readNotes.has(note.id) ? '' : '<span class="visually-hidden"> (unread)</span>'}</span>
    </li>`).join('');
}

document.querySelector('#mark-read').addEventListener('click', () => {
    NOTIFICATIONS.forEach((note) => readNotes.add(note.id));
    save('wl-admin-read', [...readNotes]);
    renderNotifications();
});

const dropdowns = [
    { button: document.querySelector('#notify-btn'), panel: document.querySelector('#notify-panel') },
    { button: document.querySelector('#profile-btn'), panel: document.querySelector('#profile-panel') }
];

function closeDropdowns(except) {
    dropdowns.forEach(({ button, panel }) => {
        if (button !== except) {
            button.setAttribute('aria-expanded', 'false');
            panel.hidden = true;
        }
    });
}

dropdowns.forEach(({ button, panel }) => {
    button.hidden = false;
    button.addEventListener('click', () => {
        const open = button.getAttribute('aria-expanded') !== 'true';
        closeDropdowns(button);
        button.setAttribute('aria-expanded', String(open));
        panel.hidden = !open;
    });
});

document.addEventListener('click', (event) => {
    if (!event.target.closest('.dropdown')) {
        closeDropdowns(null);
    }
});

document.querySelector('#sign-out').addEventListener('click', () => {
    closeDropdowns(null);
    showToast('Demo: signing out is not available.');
});

// ---------- toast messages ----------
const toast = document.createElement('p');
toast.className = 'toast';
toast.setAttribute('role', 'status');
document.body.append(toast);
let toastTimer = null;

function showToast(message) {
    toast.textContent = message;
    toast.classList.add('is-visible');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => toast.classList.remove('is-visible'), 3000);
}

// ---------- theme ----------
const themeButton = document.querySelector('#theme-btn');
themeButton.hidden = false;

function currentTheme() {
    return root.dataset.theme || (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
}

function showTheme() {
    themeButton.setAttribute('aria-pressed', String(currentTheme() === 'dark'));
}

themeButton.addEventListener('click', () => {
    root.dataset.theme = currentTheme() === 'dark' ? 'light' : 'dark';
    try {
        localStorage.setItem('wl-admin-theme', root.dataset.theme);
    } catch (error) {
        // not saved
    }
    showTheme();
});
showTheme();

// ---------- sidebar: collapse on wide screens, drawer on phones ----------
const sidebar = document.querySelector('#sidebar');
const collapseButton = document.querySelector('#collapse-btn');
const menuButton = document.querySelector('#menu-btn');
const drawerClose = document.querySelector('#drawer-close');
const backdrop = document.querySelector('#drawer-backdrop');
const phone = window.matchMedia('(max-width: 767px)');
collapseButton.hidden = false;
menuButton.hidden = false;
drawerClose.hidden = false;

function setCollapsed(collapsed, remember) {
    root.classList.toggle('sidebar-collapsed', collapsed);
    collapseButton.setAttribute('aria-pressed', String(collapsed));
    collapseButton.querySelector('.nav-label').textContent = collapsed ? 'Expand' : 'Collapse';
    if (remember) {
        save('wl-admin-collapsed', collapsed);
    }
}

// tablets start with the narrow sidebar unless the person chose otherwise
const savedCollapse = load('wl-admin-collapsed', null);
setCollapsed(savedCollapse === null ? window.innerWidth < 1200 : savedCollapse, false);
collapseButton.addEventListener('click', () => setCollapsed(!root.classList.contains('sidebar-collapsed'), true));

function setDrawer(open) {
    sidebar.classList.toggle('is-open', open);
    backdrop.hidden = !open;
    menuButton.setAttribute('aria-expanded', String(open));
    document.querySelector('.topbar').inert = open;
    document.querySelector('#main').inert = open;
    if (open) {
        sidebar.querySelector('.side-nav a').focus();
    } else {
        menuButton.focus();
    }
}

menuButton.addEventListener('click', () => setDrawer(true));
drawerClose.addEventListener('click', () => setDrawer(false));
backdrop.addEventListener('click', () => setDrawer(false));
sidebar.addEventListener('click', (event) => {
    if (event.target.closest('a') && sidebar.classList.contains('is-open')) {
        setDrawer(false);
    }
});
phone.addEventListener('change', () => {
    if (!phone.matches && sidebar.classList.contains('is-open')) {
        setDrawer(false);
    }
});

// keep focus inside the open drawer; Escape closes the drawer or a dropdown
document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') {
        if (sidebar.classList.contains('is-open')) {
            setDrawer(false);
            return;
        }
        const open = dropdowns.find(({ button }) => button.getAttribute('aria-expanded') === 'true');
        if (open) {
            closeDropdowns(null);
            open.button.focus();
        }
    }
    if (event.key === 'Tab' && sidebar.classList.contains('is-open')) {
        const focusable = Array.from(sidebar.querySelectorAll('a, button:not([hidden])')).filter((element) => element.offsetParent !== null);
        const first = focusable[0];
        const last = focusable[focusable.length - 1];
        if (event.shiftKey && document.activeElement === first) {
            event.preventDefault();
            last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault();
            first.focus();
        }
    }
});

// highlight the sidebar link for the section in view
const navLinks = Array.from(document.querySelectorAll('.side-nav a'));
const sections = navLinks.map((link) => document.querySelector(link.getAttribute('href') === '#main' ? '.kpis' : link.getAttribute('href')));
const sectionObserver = new IntersectionObserver((entries) => {
    entries.filter((entry) => entry.isIntersecting).forEach((entry) => {
        const index = sections.indexOf(entry.target);
        navLinks.forEach((link, i) => {
            link.classList.toggle('is-active', i === index);
            if (i === index) {
                link.setAttribute('aria-current', 'true');
            } else {
                link.removeAttribute('aria-current');
            }
        });
    });
}, { rootMargin: '-35% 0px -55% 0px' });
sections.forEach((section) => sectionObserver.observe(section));

// ---------- date range ----------
const rangeInputs = document.querySelectorAll('input[name="range"]');
rangeInputs.forEach((input) => {
    input.checked = Number(input.value) === state.range;
});
document.querySelector('#range').addEventListener('change', (event) => {
    state.range = Number(event.target.value);
    state.page = 0;
    save('wl-admin-range', state.range);
    document.querySelector('#main').classList.add('is-updating');
    renderAll();
    setTimeout(() => document.querySelector('#main').classList.remove('is-updating'), 250);
    document.querySelector('#range-status').textContent = `Showing the ${rangeName(state.range)}`;
});

// ---------- greeting and first draw ----------
const hour = today.getHours();
document.querySelector('#greeting').textContent = `${hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening'}, Amira`;

function renderAll() {
    renderKpis();
    renderRevenue();
    renderBreakdowns();
    renderTable();
    renderEnquiries();
}

renderNotifications();
renderAll();
