// Employee Directory - search, filters, sorting, card or list view and profile dialog

const grid = document.querySelector('#employee-grid');
const employees = Array.from(grid.querySelectorAll('.employee'));
const form = document.querySelector('.filters');
const searchInput = document.querySelector('#search');
const deptSelect = document.querySelector('#filter-dept');
const locationSelect = document.querySelector('#filter-location');
const availableCheck = document.querySelector('#filter-available');
const sortSelect = document.querySelector('#sort');
const resultCount = document.querySelector('#result-count');
const noResults = document.querySelector('#no-results');
const viewButtons = document.querySelectorAll('.view-btn');

// search and filters
function applyFilters() {
    const words = searchInput.value.trim().toLowerCase().split(/\s+/).filter(Boolean);
    let shown = 0;

    employees.forEach((employee) => {
        const data = employee.dataset;
        const matches =
            words.every((word) => data.search.includes(word)) &&
            (deptSelect.value === 'all' || data.dept === deptSelect.value) &&
            (locationSelect.value === 'all' || data.location === locationSelect.value) &&
            (!availableCheck.checked || data.status === 'available');

        employee.hidden = !matches;
        if (matches) {
            shown += 1;
        }
    });

    noResults.hidden = shown > 0;
    resultCount.textContent = shown === employees.length
        ? `Showing all ${shown} people`
        : `Showing ${shown} of ${employees.length} people`;
}

form.addEventListener('input', applyFilters);
form.addEventListener('submit', (event) => event.preventDefault());

// the reset event fires before the fields clear, so filter on the next tick
form.addEventListener('reset', () => setTimeout(applyFilters, 0));

document.querySelector('#clear-from-empty').addEventListener('click', () => {
    form.reset();
    searchInput.focus();
});

// department cards: "View the team" sets the department filter
document.querySelectorAll('.dept-link').forEach((link) => {
    link.addEventListener('click', () => {
        form.reset();
        deptSelect.value = link.dataset.dept;
        setTimeout(applyFilters, 0);
    });
});

// sorting
sortSelect.addEventListener('change', () => {
    const sorted = [...employees].sort((a, b) => {
        if (sortSelect.value === 'newest') {
            return b.dataset.start.localeCompare(a.dataset.start);
        }
        if (sortSelect.value === 'dept') {
            return a.dataset.dept.localeCompare(b.dataset.dept) || a.dataset.last.localeCompare(b.dataset.last);
        }
        return a.dataset.last.localeCompare(b.dataset.last);
    });
    sorted.forEach((employee) => grid.appendChild(employee));
});

// card or list view
viewButtons.forEach((button) => {
    button.addEventListener('click', () => {
        viewButtons.forEach((other) => other.setAttribute('aria-pressed', String(other === button)));
        grid.classList.toggle('is-list', button.dataset.view === 'list');
    });
});

// profile dialog
const dialog = document.querySelector('#profile');
let openedFrom = null;

function fill(id, text) {
    document.querySelector(`#${id}`).textContent = text;
}

grid.addEventListener('click', (event) => {
    const button = event.target.closest('.btn-profile');
    if (!button) {
        return;
    }
    const card = button.closest('.employee-card');
    const photo = card.querySelector('.emp-head img');
    const email = card.querySelector('.emp-contact a');

    document.querySelector('#profile-photo').src = photo.src;
    document.querySelector('#profile-photo').alt = photo.alt;
    fill('profile-name', card.querySelector('.emp-name').textContent);
    fill('profile-title', card.querySelector('.emp-title').textContent);
    fill('profile-bio', button.dataset.bio);
    fill('profile-dept', card.querySelector('.dept-badge').textContent);
    fill('profile-location', card.querySelector('.emp-location').textContent.trim());
    fill('profile-status', card.querySelector('.status').textContent);
    fill('profile-manager', button.dataset.manager);

    const start = document.querySelector('#profile-start');
    start.dateTime = button.dataset.start;
    start.textContent = button.dataset.startText;

    const contact = document.querySelector('#profile-contact');
    contact.innerHTML = '';
    contact.append(email.cloneNode(true), document.createElement('br'), card.querySelector('.emp-contact span').textContent);

    const skills = document.querySelector('#profile-skills');
    skills.innerHTML = '';
    card.querySelectorAll('.emp-skills li').forEach((skill) => skills.append(skill.cloneNode(true)));

    openedFrom = button;
    dialog.showModal();
});

document.querySelector('#profile-close').addEventListener('click', () => dialog.close());

dialog.addEventListener('click', (event) => {
    if (event.target === dialog) {
        dialog.close();
    }
});

dialog.addEventListener('close', () => {
    if (openedFrom) {
        openedFrom.focus();
    }
});
