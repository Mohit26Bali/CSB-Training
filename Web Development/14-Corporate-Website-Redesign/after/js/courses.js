// Fernhill College - course filters. Without JavaScript the form still works: it reloads the page with the
// filters in the address, and every course is listed.

const form = document.querySelector('#filters');
const items = Array.from(document.querySelectorAll('#course-list > li'));
const count = document.querySelector('#result-count');
const empty = document.querySelector('#empty');

// what each card can be found by: its title, subject and summary
items.forEach((item) => {
    item.dataset.text = item.textContent.replace(/\s+/g, ' ').toLowerCase();
});

function chosenLevels() {
    return Array.from(form.querySelectorAll('input[name="level"]:checked')).map((box) => box.value);
}

function describe(shown) {
    const parts = [];
    const subject = form.elements.subject;
    const mode = form.elements.mode;
    if (subject.value) {
        parts.push(subject.selectedOptions[0].textContent);
    }
    if (mode.value) {
        parts.push(mode.selectedOptions[0].textContent.toLowerCase());
    }
    const levels = chosenLevels();
    if (levels.length) {
        parts.push(`level ${levels.join(' or ')}`);
    }
    const query = form.elements.q.value.trim();
    if (query) {
        parts.push(`matching “${query}”`);
    }
    const noun = shown === 1 ? 'course' : 'courses';
    if (!parts.length) {
        return `Showing all ${shown} ${noun}`;
    }
    return `Showing ${shown} ${noun}: ${parts.join(', ')}`;
}

function update() {
    const subject = form.elements.subject.value;
    const mode = form.elements.mode.value;
    const levels = chosenLevels();
    const words = form.elements.q.value.trim().toLowerCase().split(/\s+/).filter(Boolean);
    let shown = 0;
    items.forEach((item) => {
        const card = item.firstElementChild;
        const match = (!subject || card.dataset.subject === subject)
            && (!mode || card.dataset.mode === mode)
            && (!levels.length || levels.includes(card.dataset.level))
            && words.every((word) => item.dataset.text.includes(word));
        item.hidden = !match;
        shown += match ? 1 : 0;
    });
    count.textContent = shown ? describe(shown) : 'No courses found';
    empty.hidden = shown > 0;
}

// keep the filters in the address, so a filtered list can be bookmarked or shared
function remember() {
    const params = new URLSearchParams(new FormData(form));
    for (const [key, value] of Array.from(params.entries())) {
        if (!value) {
            params.delete(key);
        }
    }
    const query = params.toString();
    try {
        history.replaceState(null, '', query ? `?${query}` : location.pathname);
    } catch (error) {
        // some browsers block this for files opened from disk; the filters still work
    }
}

let typing = null;
form.addEventListener('input', (event) => {
    clearTimeout(typing);
    typing = setTimeout(() => {
        update();
        remember();
    }, event.target.type === 'search' ? 250 : 0);
});

form.addEventListener('submit', (event) => {
    event.preventDefault();
    update();
    remember();
});

form.addEventListener('reset', () => {
    // the reset happens after this event, so update once the fields are cleared
    setTimeout(() => {
        update();
        remember();
        form.elements.q.focus();
    });
});

// filters from the address (for example courses.html?subject=health from the home page)
const params = new URLSearchParams(location.search);
if (params.has('q')) {
    form.elements.q.value = params.get('q');
}
['subject', 'mode'].forEach((name) => {
    const value = params.get(name);
    if (value && form.elements[name].querySelector(`option[value="${CSS.escape(value)}"]`)) {
        form.elements[name].value = value;
    }
});
params.getAll('level').forEach((value) => {
    const box = form.querySelector(`input[name="level"][value="${CSS.escape(value)}"]`);
    if (box) {
        box.checked = true;
    }
});
update();
