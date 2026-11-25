// Portfolio - theme switch, menu highlight, project filters, case studies, contact form, placeholders

const root = document.documentElement;
const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
const prefersDark = window.matchMedia('(prefers-color-scheme: dark)');

// ---------- light / dark theme ----------
const themeButton = document.querySelector('#theme-toggle');

function isDark() {
    return root.dataset.theme ? root.dataset.theme === 'dark' : prefersDark.matches;
}

function showThemeState() {
    themeButton.setAttribute('aria-pressed', String(isDark()));
}

themeButton.addEventListener('click', () => {
    root.dataset.theme = isDark() ? 'light' : 'dark';
    try {
        localStorage.setItem('portfolio-theme', root.dataset.theme);
    } catch (error) {
        // not saved; the theme still changes for this visit
    }
    showThemeState();
});
prefersDark.addEventListener('change', showThemeState);
showThemeState();

// ---------- highlight the menu link for the section in view ----------
const navLinks = Array.from(document.querySelectorAll('.site-nav a'));
const sections = Array.from(document.querySelectorAll('main > section[id]'));

function markSection() {
    const line = window.innerHeight * 0.3;
    let current = null;
    sections.forEach((section) => {
        if (section.getBoundingClientRect().top <= line) {
            current = section.id;
        }
    });
    navLinks.forEach((link) => {
        if (link.hash === `#${current}`) {
            link.setAttribute('aria-current', 'true');
        } else {
            link.removeAttribute('aria-current');
        }
    });
}
window.addEventListener('scroll', markSection, { passive: true });
markSection();

// ---------- project filters: by type (chips) or by skill (from the Skills section) ----------
const filters = document.querySelector('#filters');
const projects = Array.from(document.querySelectorAll('#project-grid > li'));
const resultCount = document.querySelector('#result-count');
const skillFilter = document.querySelector('#skill-filter');
const skillFilterText = document.querySelector('#skill-filter-text');
let skill = null;

function categoryName(value) {
    return filters.querySelector(`input[value="${value}"]`).parentElement.firstChild.nextSibling.textContent.trim();
}

function applyFilters() {
    const category = filters.elements.category.value;
    let shown = 0;
    projects.forEach((item) => {
        const match = (category === 'all' || item.dataset.categories.split(' ').includes(category))
            && (!skill || item.dataset.skills.split(' ').includes(skill.key));
        item.hidden = !match;
        shown += match ? 1 : 0;
    });
    const noun = shown === 1 ? 'project' : 'projects';
    let text = category === 'all' && !skill ? `Showing all ${shown} ${noun}` : `Showing ${shown} ${noun}`;
    if (category !== 'all') {
        text += `: ${categoryName(category)}`;
    }
    if (skill) {
        text += ` using ${skill.name}`;
    }
    resultCount.textContent = text;
    skillFilter.hidden = !skill;
    if (skill) {
        skillFilterText.textContent = `Projects that use ${skill.name}`;
    }
}

filters.addEventListener('change', applyFilters);

// choosing a skill shows the projects that use it
document.querySelectorAll('.skill').forEach((button) => {
    button.addEventListener('click', () => {
        skill = { key: button.dataset.skill, name: button.querySelector('.skill-name').textContent };
        filters.elements.category.value = 'all';
        applyFilters();
        document.querySelector('#work').scrollIntoView({ behavior: reduceMotion.matches ? 'auto' : 'smooth' });
        document.querySelector('#work-title').focus({ preventScroll: true });
    });
});
document.querySelector('#work-title').tabIndex = -1;

document.querySelector('#clear-skill').addEventListener('click', () => {
    skill = null;
    applyFilters();
    filters.querySelector('input[value="all"]').focus();
});

// ---------- case studies ----------
const cases = JSON.parse(document.querySelector('#case-data').textContent);
const dialog = document.querySelector('#case');
let opener = null;

function fillList(list, items, className) {
    list.replaceChildren(...items.map((text) => {
        const li = document.createElement('li');
        li.textContent = text;
        if (className) {
            li.className = className;
        }
        return li;
    }));
}

document.querySelectorAll('[data-case]').forEach((button) => {
    button.addEventListener('click', () => {
        const data = cases[button.dataset.case];
        opener = button;
        document.querySelector('#case-kind').textContent = data.kind;
        document.querySelector('#case-title').textContent = data.title;
        const image = document.querySelector('#case-image');
        image.hidden = !data.image;
        if (data.image) {
            image.src = data.image;
            image.alt = `Screenshot of ${data.title}`;
        }
        document.querySelector('#case-summary').textContent = data.summary;
        fillList(document.querySelector('#case-built'), data.built);
        document.querySelector('#case-decision').textContent = data.decision;
        document.querySelector('#case-result').textContent = data.result;
        fillList(document.querySelector('#case-skills'), data.skills);
        document.querySelector('#case-link').href = data.link;
        dialog.showModal();
        dialog.scrollTop = 0;
    });
});

document.querySelector('#case-close').addEventListener('click', () => dialog.close());
// a click on the dimmed backdrop closes the case study
dialog.addEventListener('click', (event) => {
    if (event.target === dialog) {
        dialog.close();
    }
});
dialog.addEventListener('close', () => {
    if (opener) {
        opener.focus();
    }
});

// ---------- contact form: checks the fields, then opens the visitor's email app with the message ----------
const form = document.querySelector('#contact-form');
const status = document.querySelector('#form-status');
const OWNER_EMAIL = document.querySelector('.contact-links a[href^="mailto:"]').getAttribute('href').replace('mailto:', '');

function setError(id, text) {
    const input = document.getElementById(id);
    document.getElementById(`${id}-error`).textContent = text;
    if (text) {
        input.setAttribute('aria-invalid', 'true');
    } else {
        input.removeAttribute('aria-invalid');
    }
    return !text;
}

form.addEventListener('submit', (event) => {
    event.preventDefault();
    const name = form.elements.name.value.trim();
    const email = form.elements.email.value.trim();
    const message = form.elements.message.value.trim();
    const ok = [
        setError('c-name', name ? '' : 'Enter your name.'),
        setError('c-email', !email ? 'Enter your email address.' : /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(email) ? '' : 'Enter an email address like name@example.com.'),
        setError('c-message', message.length >= 10 ? '' : 'Write a message of at least 10 characters.'),
    ];
    const firstBad = ['c-name', 'c-email', 'c-message'][ok.indexOf(false)];
    if (firstBad) {
        status.textContent = '';
        document.getElementById(firstBad).focus();
        return;
    }
    const subject = encodeURIComponent(`Portfolio enquiry from ${name}`);
    const body = encodeURIComponent(`${message}\n\n${name}\n${email}`);
    status.textContent = 'Opening your email app with your message. If nothing happens, email me directly at the address on the left.';
    window.location.href = `mailto:${OWNER_EMAIL}?subject=${subject}&body=${body}`;
});

form.addEventListener('input', (event) => {
    if (event.target.getAttribute('aria-invalid') === 'true') {
        setError(event.target.id, '');
    }
});

// ---------- gentle reveal as sections scroll into view ----------
const revealer = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
        if (entry.isIntersecting) {
            entry.target.classList.add('is-visible');
            revealer.unobserve(entry.target);
        }
    });
}, { rootMargin: '0px 0px -5% 0px' });
document.querySelectorAll('.achievement, .skill-group, .journey li').forEach((el) => {
    el.classList.add('reveal');
    revealer.observe(el);
});

// ---------- open the page as index.html?placeholders to outline every detail still to replace ----------
if (new URLSearchParams(location.search).has('placeholders')) {
    root.classList.add('show-placeholders');
}
