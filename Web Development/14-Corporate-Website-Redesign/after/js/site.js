// Fernhill College - shared on every page: the mobile menu

const menuButton = document.querySelector('.menu-toggle');
const nav = document.querySelector('#site-nav');

function setMenu(open) {
    menuButton.setAttribute('aria-expanded', String(open));
    nav.classList.toggle('is-open', open);
}

menuButton.addEventListener('click', () => {
    setMenu(menuButton.getAttribute('aria-expanded') !== 'true');
});

// Escape closes the menu and returns focus to the button
document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && menuButton.getAttribute('aria-expanded') === 'true') {
        setMenu(false);
        menuButton.focus();
    }
});

// moving focus or clicking outside the header closes it too
document.addEventListener('click', (event) => {
    if (!event.target.closest('.site-header')) {
        setMenu(false);
    }
});
