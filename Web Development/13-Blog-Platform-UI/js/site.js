// Long Way Round - shared on every page: saved settings and the Display menu (theme and text size)

function load(key, fallback) {
    try {
        const value = JSON.parse(localStorage.getItem(key));
        return value === null ? fallback : value;
    } catch (error) {
        return fallback;
    }
}

function save(key, value) {
    try {
        localStorage.setItem(key, JSON.stringify(value));
    } catch (error) {
        // not saved; the setting still works until the page is closed
    }
}

// the theme and size are stored as plain strings so the inline script in <head> can read them before drawing
function saveSetting(key, value) {
    try {
        localStorage.setItem(key, value);
    } catch (error) {
        // not saved
    }
}

const root = document.documentElement;
const displayMenu = document.querySelector('.display-menu');
const displayForm = document.querySelector('#display-form');

// show the current choices in the menu
displayForm.elements.theme.value = root.dataset.theme || 'auto';
displayForm.elements.size.value = root.dataset.size || 'standard';

displayForm.addEventListener('change', (event) => {
    const { name, value } = event.target;
    root.dataset[name] = value;
    saveSetting(`lwr-${name}`, value);
});
displayForm.addEventListener('submit', (event) => event.preventDefault());

// close the menu with Escape (returning focus to its button) or by clicking anywhere else
document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && displayMenu.open) {
        displayMenu.open = false;
        displayMenu.querySelector('summary').focus();
    }
});
document.addEventListener('click', (event) => {
    if (displayMenu.open && !displayMenu.contains(event.target)) {
        displayMenu.open = false;
    }
});

// stories the reader has finished, shared by the home page and story pages
function readStories() {
    return load('lwr-read', []);
}
