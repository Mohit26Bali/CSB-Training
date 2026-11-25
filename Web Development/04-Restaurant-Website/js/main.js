// Restaurant Website - menu toggle, cuisine filter, photo viewer, booking form and opening hours

// mobile menu
const navToggle = document.querySelector('.nav-toggle');
const navMenu = document.querySelector('#primary-menu');

function setMenu(isOpen) {
    navToggle.setAttribute('aria-expanded', String(isOpen));
    navMenu.classList.toggle('is-open', isOpen);
}

navToggle.addEventListener('click', () => {
    setMenu(navToggle.getAttribute('aria-expanded') !== 'true');
});

navMenu.addEventListener('click', (event) => {
    if (event.target.closest('a')) {
        setMenu(false);
    }
});

document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && navMenu.classList.contains('is-open')) {
        setMenu(false);
        navToggle.focus();
    }
});

// cuisine filter on the menu
const chips = document.querySelectorAll('.chip');
const dishes = document.querySelectorAll('.dish');
const menuStatus = document.querySelector('#menu-status');

chips.forEach((chip) => {
    chip.addEventListener('click', () => {
        const cuisine = chip.dataset.cuisine;
        chips.forEach((other) => other.setAttribute('aria-pressed', String(other === chip)));

        let shown = 0;
        dishes.forEach((dish) => {
            const matches = cuisine === 'all' || dish.dataset.cuisine === cuisine;
            dish.hidden = !matches;
            if (matches) {
                shown += 1;
            }
        });
        menuStatus.textContent = cuisine === 'all'
            ? `Showing all ${shown} dishes.`
            : `Showing ${shown} ${chip.textContent} dishes.`;
    });
});

// gallery photo viewer
const galleryLinks = Array.from(document.querySelectorAll('.gallery-link'));
const lightbox = document.querySelector('#lightbox');
const lightboxImage = document.querySelector('#lightbox-image');
const lightboxCaption = document.querySelector('#lightbox-caption');
let currentPhoto = 0;

function showPhoto(index) {
    currentPhoto = (index + galleryLinks.length) % galleryLinks.length;
    const link = galleryLinks[currentPhoto];
    const thumb = link.querySelector('img');
    lightboxImage.src = link.href;
    lightboxImage.alt = thumb.alt;
    lightboxCaption.textContent = `${link.closest('figure').querySelector('figcaption').textContent} (${currentPhoto + 1} of ${galleryLinks.length})`;
}

galleryLinks.forEach((link, index) => {
    link.addEventListener('click', (event) => {
        event.preventDefault();
        showPhoto(index);
        lightbox.showModal();
    });
});

lightbox.querySelector('.lightbox-close').addEventListener('click', () => lightbox.close());
lightbox.querySelector('.lightbox-prev').addEventListener('click', () => showPhoto(currentPhoto - 1));
lightbox.querySelector('.lightbox-next').addEventListener('click', () => showPhoto(currentPhoto + 1));

lightbox.addEventListener('keydown', (event) => {
    if (event.key === 'ArrowLeft') {
        showPhoto(currentPhoto - 1);
    } else if (event.key === 'ArrowRight') {
        showPhoto(currentPhoto + 1);
    }
});

// clicking the dark area outside the photo closes the viewer
lightbox.addEventListener('click', (event) => {
    if (event.target === lightbox) {
        lightbox.close();
    }
});

// return focus to the photo that was opened
lightbox.addEventListener('close', () => galleryLinks[currentPhoto].focus());

// booking form
const form = document.querySelector('.reserve-form');
const dateInput = document.querySelector('#date');
const timeSelect = document.querySelector('#time');
const formStatus = document.querySelector('#form-status');

function toInputDate(date) {
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${date.getFullYear()}-${month}-${day}`;
}

dateInput.min = toInputDate(new Date());

function checkOpeningRules() {
    dateInput.setCustomValidity('');
    timeSelect.setCustomValidity('');
    if (!dateInput.value) {
        return;
    }
    const day = new Date(`${dateInput.value}T12:00`).getDay();
    if (day === 1) {
        dateInput.setCustomValidity('We are closed on Mondays. Please choose another day.');
    } else if (day === 0 && timeSelect.value >= '18:00') {
        timeSelect.setCustomValidity('We serve lunch only on Sundays. Please choose a time before 15:00.');
    }
}

dateInput.addEventListener('change', checkOpeningRules);
timeSelect.addEventListener('change', checkOpeningRules);

form.addEventListener('submit', (event) => {
    checkOpeningRules();
    if (!form.checkValidity()) {
        event.preventDefault();
        form.reportValidity();
        return;
    }
    event.preventDefault();

    const name = document.querySelector('#name').value.trim();
    const guests = document.querySelector('#guests').value;
    const date = new Date(`${dateInput.value}T12:00`).toLocaleDateString('en-GB', {
        weekday: 'long', day: 'numeric', month: 'long',
    });
    formStatus.textContent = `Thank you, ${name}. Your request for a table for ${guests} on ${date} at ${timeSelect.value} has been received. We will confirm by email.`;
    form.reset();
    dateInput.min = toInputDate(new Date());
});

// highlight today's row in the opening hours
const today = document.querySelector(`.hours tr[data-day="${new Date().getDay()}"]`);
if (today) {
    today.classList.add('is-today');
}
