// Summit Bouldering - page behaviour: menu, scroll effects, timetable, prices, coaches, gallery, reviews, opening hours
// (the booking form is in booking.js)

const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
const DAY_KEYS = ['sun', 'mon', 'tue', 'wed', 'thu', 'fri', 'sat'];

// ---------- toast messages (also used by booking.js) ----------
const toast = document.querySelector('#toast');
let toastTimer = null;

function showToast(message) {
    toast.textContent = message;
    toast.classList.add('is-visible');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => toast.classList.remove('is-visible'), 3500);
}

// ---------- mobile menu ----------
const header = document.querySelector('#site-header');
const menuButton = document.querySelector('#menu-toggle');
const nav = document.querySelector('#site-nav');

function setMenu(open) {
    menuButton.setAttribute('aria-expanded', String(open));
    nav.classList.toggle('is-open', open);
}

menuButton.addEventListener('click', () => {
    setMenu(menuButton.getAttribute('aria-expanded') !== 'true');
});

// choosing a link closes the menu
nav.addEventListener('click', (event) => {
    if (event.target.closest('a')) {
        setMenu(false);
    }
});

// Escape closes the menu and returns focus to its button
document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && menuButton.getAttribute('aria-expanded') === 'true') {
        setMenu(false);
        menuButton.focus();
    }
});

// clicking outside the header closes it too
document.addEventListener('click', (event) => {
    if (!header.contains(event.target)) {
        setMenu(false);
    }
});

// ---------- header shadow once the page scrolls ----------
function updateHeader() {
    header.classList.toggle('is-scrolled', window.scrollY > 10);
}
window.addEventListener('scroll', updateHeader, { passive: true });
updateHeader();

// ---------- highlight the menu link for the section in view ----------
const navLinks = Array.from(nav.querySelectorAll('ul a'));
const pageSections = Array.from(document.querySelectorAll('main > section[id]'));

function markSection() {
    // the section in view is the last one whose top has passed a line a third of the way down the screen;
    // sections without a menu link (such as the gallery) leave no link highlighted
    const line = window.innerHeight * 0.35;
    let current = null;
    pageSections.forEach((section) => {
        if (section.getBoundingClientRect().top <= line) {
            current = section;
        }
    });
    navLinks.forEach((link) => {
        if (current && link.hash === `#${current.id}`) {
            link.setAttribute('aria-current', 'true');
        } else {
            link.removeAttribute('aria-current');
        }
    });
}
window.addEventListener('scroll', markSection, { passive: true });
markSection();

// ---------- scroll reveal and number counters ----------
// blocks in the same row appear one after another (--reveal-i sets a small delay)
document.querySelectorAll('.reveal').forEach((block) => {
    const siblings = Array.from(block.parentElement.children).filter((el) => el.classList.contains('reveal'));
    block.style.setProperty('--reveal-i', Math.min(siblings.indexOf(block), 5));
});

const revealer = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
        if (entry.isIntersecting) {
            entry.target.classList.add('is-visible');
            revealer.unobserve(entry.target);
        }
    });
}, { rootMargin: '0px 0px -8% 0px' });
document.querySelectorAll('.reveal').forEach((block) => revealer.observe(block));

function countUp(element) {
    const target = Number(element.dataset.count);
    const decimals = Number(element.dataset.decimals || 0);
    if (reduceMotion.matches) {
        element.textContent = target.toFixed(decimals);
        return;
    }
    const start = performance.now();
    const duration = 1400;
    function frame(now) {
        const t = Math.min(1, (now - start) / duration);
        const eased = 1 - (1 - t) ** 3;
        element.textContent = (target * eased).toFixed(decimals);
        if (t < 1) {
            requestAnimationFrame(frame);
        }
    }
    requestAnimationFrame(frame);
}

const counter = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
        if (entry.isIntersecting) {
            countUp(entry.target);
            counter.unobserve(entry.target);
        }
    });
});
document.querySelectorAll('.count').forEach((number) => counter.observe(number));

// ---------- timetable: filter by type, and one day at a time on small screens ----------
const filters = document.querySelector('#timetable-filters');
const days = Array.from(document.querySelectorAll('.day'));
const timetableCount = document.querySelector('#timetable-count');
const dayChips = document.querySelector('#day-chips');
const smallScreen = window.matchMedia('(max-width: 47.99rem)');
const today = DAY_KEYS[new Date().getDay()];
const TYPE_NAMES = {};
filters.querySelectorAll('input[name="type"]').forEach((input) => {
    TYPE_NAMES[input.value] = input.parentElement.textContent.trim();
});

document.querySelector(`#day-${today}`).classList.add('is-today');
dayChips.hidden = false;
filters.elements.day.value = today;

function updateTimetable() {
    const type = filters.elements.type.value;
    const chosenDay = filters.elements.day.value;
    let shown = 0;
    days.forEach((day) => {
        let inDay = 0;
        day.querySelectorAll('.session').forEach((session) => {
            const match = type === 'all' || session.dataset.type === type;
            session.hidden = !match;
            inDay += match ? 1 : 0;
        });
        day.querySelector('.day-empty').hidden = inDay > 0;
        day.classList.toggle('is-shown', day.dataset.day === chosenDay);
        if (!smallScreen.matches || day.dataset.day === chosenDay) {
            shown += inDay;
        }
    });
    const dayName = smallScreen.matches ? ` on ${document.querySelector(`#day-${chosenDay}-title`).textContent}` : ' this week';
    const noun = shown === 1 ? 'class' : 'classes';
    const what = type === 'all' ? noun : `${TYPE_NAMES[type]} ${noun}`;
    timetableCount.textContent = `Showing ${shown} ${what}${dayName}`;
}

filters.addEventListener('change', updateTimetable);
filters.addEventListener('submit', (event) => event.preventDefault());
smallScreen.addEventListener('change', updateTimetable);
updateTimetable();

// ---------- prices: monthly or yearly memberships ----------
const billingButtons = document.querySelectorAll('.billing-option');

function setBilling(period) {
    billingButtons.forEach((button) => {
        button.setAttribute('aria-pressed', String(button.dataset.billing === period));
    });
    document.querySelectorAll('.member-price').forEach((price) => {
        const value = price.dataset[period];
        price.value = value;
        price.textContent = `£${value}`;
        price.classList.remove('is-changing');
        void price.offsetWidth; // restart the flip animation
        price.classList.add('is-changing');
    });
    document.querySelectorAll('.member-per').forEach((per) => {
        per.textContent = period === 'yearly' ? 'per year' : 'per month';
    });
    document.querySelectorAll('.price-card-featured .member-note').forEach((note) => {
        note.textContent = period === 'yearly' ? 'Pay once, save £112 a year' : "Cancel any time with 30 days' notice";
    });
}

billingButtons.forEach((button) => {
    button.addEventListener('click', () => setBilling(button.dataset.billing));
});

// ---------- coaches: show and hide each bio ----------
document.querySelectorAll('.more-toggle').forEach((button) => {
    const panel = document.getElementById(button.getAttribute('aria-controls'));
    const name = button.textContent.replace('More about ', '');
    button.addEventListener('click', () => {
        const open = button.getAttribute('aria-expanded') !== 'true';
        button.setAttribute('aria-expanded', String(open));
        panel.classList.toggle('is-open', open);
        button.textContent = open ? `Less about ${name}` : `More about ${name}`;
    });
});

// ---------- gallery lightbox ----------
const galleryButtons = Array.from(document.querySelectorAll('.gallery-btn'));
const lightbox = document.querySelector('#lightbox');
const lightboxImage = document.querySelector('#lightbox-image');
const lightboxCaption = document.querySelector('#lightbox-caption');
const lightboxCount = document.querySelector('#lightbox-count');
let photoIndex = 0;
let photoOpener = null;

galleryButtons.forEach((button) => {
    const img = button.querySelector('img');
    button.setAttribute('aria-label', `View larger: ${img.alt}`);
});

function showPhoto(index) {
    photoIndex = (index + galleryButtons.length) % galleryButtons.length;
    const img = galleryButtons[photoIndex].querySelector('img');
    lightboxImage.src = img.src;
    lightboxImage.alt = img.alt;
    lightboxImage.width = img.width;
    lightboxImage.height = img.height;
    lightboxCaption.textContent = img.alt;
    lightboxCount.textContent = `${photoIndex + 1} / ${galleryButtons.length}`;
}

galleryButtons.forEach((button, index) => {
    button.addEventListener('click', () => {
        photoOpener = button;
        showPhoto(index);
        lightbox.showModal();
    });
});

document.querySelector('#lightbox-prev').addEventListener('click', () => showPhoto(photoIndex - 1));
document.querySelector('#lightbox-next').addEventListener('click', () => showPhoto(photoIndex + 1));
document.querySelector('#lightbox-close').addEventListener('click', () => lightbox.close());

lightbox.addEventListener('keydown', (event) => {
    if (event.key === 'ArrowLeft') {
        showPhoto(photoIndex - 1);
    } else if (event.key === 'ArrowRight') {
        showPhoto(photoIndex + 1);
    }
});

// a click on the dark backdrop (outside the photo) closes it
lightbox.addEventListener('click', (event) => {
    if (event.target === lightbox) {
        lightbox.close();
    }
});

lightbox.addEventListener('close', () => {
    if (photoOpener) {
        photoOpener.focus();
    }
});

// ---------- reviews carousel ----------
const track = document.querySelector('#review-track');
const prevReview = document.querySelector('#reviews-prev');
const nextReview = document.querySelector('#reviews-next');

function updateReviewButtons() {
    const max = track.scrollWidth - track.clientWidth - 2;
    prevReview.disabled = track.scrollLeft <= 2;
    nextReview.disabled = track.scrollLeft >= max;
}

function scrollReviews(direction) {
    const card = track.firstElementChild;
    const step = card.getBoundingClientRect().width + parseFloat(getComputedStyle(track).columnGap || 0);
    track.scrollBy({ left: direction * step, behavior: reduceMotion.matches ? 'auto' : 'smooth' });
}

prevReview.addEventListener('click', () => scrollReviews(-1));
nextReview.addEventListener('click', () => scrollReviews(1));
track.addEventListener('scroll', updateReviewButtons, { passive: true });
window.addEventListener('resize', updateReviewButtons);
updateReviewButtons();

// ---------- opening hours: today's row and open/closed now ----------
const HOURS = { weekday: [7, 22], weekend: [9, 20] };

function openStatus(now) {
    const day = now.getDay();
    const [opens, closes] = day === 0 || day === 6 ? HOURS.weekend : HOURS.weekday;
    const hour = now.getHours() + now.getMinutes() / 60;
    if (hour >= opens && hour < closes) {
        return { open: true, text: `Open now until ${closes}:00` };
    }
    if (hour < opens) {
        return { open: false, text: `Closed now. Opens today at ${String(opens).padStart(2, '0')}:00` };
    }
    const next = (day + 1) % 7;
    const nextOpens = next === 0 || next === 6 ? HOURS.weekend[0] : HOURS.weekday[0];
    return { open: false, text: `Closed now. Opens tomorrow at ${String(nextOpens).padStart(2, '0')}:00` };
}

function showOpenStatus() {
    const now = new Date();
    const status = openStatus(now);
    const badge = document.querySelector('#open-status');
    badge.textContent = status.text;
    badge.classList.toggle('is-closed', !status.open);
    document.querySelectorAll('#hours-table tbody tr').forEach((row) => {
        row.classList.toggle('is-today', Number(row.dataset.day) === now.getDay());
    });
}
showOpenStatus();
setInterval(showOpenStatus, 60000);

// ---------- footer newsletter ----------
const newsForm = document.querySelector('#news-form');
const newsEmail = document.querySelector('#news-email');
const newsMessage = document.querySelector('#news-message');

newsForm.addEventListener('submit', (event) => {
    event.preventDefault();
    const value = newsEmail.value.trim();
    const valid = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(value);
    newsEmail.setAttribute('aria-invalid', String(!valid));
    newsMessage.classList.toggle('is-error', !valid);
    if (!valid) {
        newsMessage.textContent = value ? 'Enter an email address like name@example.com.' : 'Enter your email address to sign up.';
        newsEmail.focus();
        return;
    }
    newsMessage.textContent = `Thanks! ${value} is signed up. (Demo: no email will be sent.)`;
    newsForm.reset();
});
