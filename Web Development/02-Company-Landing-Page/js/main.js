// Company Landing Page - menu, header shadow, active link and contact form message

const siteHeader = document.querySelector('.site-header');
const navToggle = document.querySelector('.nav-toggle');
const navMenu = document.querySelector('#primary-menu');
const navLinks = navMenu.querySelectorAll('a[href^="#"]:not(.btn)');
const contactForm = document.querySelector('.contact-form');
const formStatus = document.querySelector('.form-status');

// mobile menu
function setMenu(isOpen) {
    navToggle.setAttribute('aria-expanded', String(isOpen));
    navMenu.classList.toggle('is-open', isOpen);
}

navToggle.addEventListener('click', () => {
    const isOpen = navToggle.getAttribute('aria-expanded') === 'true';
    setMenu(!isOpen);
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

// header shadow after scrolling
function updateHeaderShadow() {
    siteHeader.classList.toggle('is-scrolled', window.scrollY > 8);
}

window.addEventListener('scroll', updateHeaderShadow, { passive: true });
updateHeaderShadow();

// highlight the menu link for the section on screen
const sectionObserver = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
        if (!entry.isIntersecting) {
            return;
        }
        navLinks.forEach((link) => {
            if (link.getAttribute('href') === `#${entry.target.id}`) {
                link.setAttribute('aria-current', 'true');
            } else {
                link.removeAttribute('aria-current');
            }
        });
    });
}, { rootMargin: '-45% 0px -50% 0px' });

navLinks.forEach((link) => {
    const section = document.querySelector(link.getAttribute('href'));
    if (section) {
        sectionObserver.observe(section);
    }
});

// the hero has no menu link, so reaching it clears the highlight
sectionObserver.observe(document.querySelector('#top'));

// contact form (front end only, nothing is sent)
contactForm.addEventListener('submit', (event) => {
    event.preventDefault();
    const name = document.querySelector('#full-name').value.trim();
    formStatus.textContent = `Thank you, ${name}. We will reply within one business day.`;
    contactForm.reset();
});
