// Fernhill College - contact form: clear errors next to each field and in a summary, a character counter,
// a confirmation message, and today's opening hours

const form = document.querySelector('#contact-form');
const summary = document.querySelector('#error-summary');
const success = document.querySelector('#success');
const message = document.querySelector('#message');
const counter = document.querySelector('#message-count');

// a course or topic chosen on another page arrives in the address, for example contact.html?course=barista
const params = new URLSearchParams(location.search);
['course', 'type'].forEach((name) => {
    const value = params.get(name);
    const select = form.elements[name];
    if (value && select.querySelector(`option[value="${CSS.escape(value)}"]`)) {
        select.value = value;
    }
});
if (params.get('course') && !params.get('type')) {
    form.elements.type.value = 'choosing';
}

function updateCounter() {
    const left = 1000 - message.value.length;
    counter.textContent = `${left.toLocaleString('en-GB')} ${left === 1 ? 'character' : 'characters'} left`;
}
message.addEventListener('input', updateCounter);
updateCounter();

function checks() {
    const problems = [];
    const name = form.elements.name.value.trim();
    const email = form.elements.email.value.trim();
    const phone = form.elements.phone.value.replace(/[\s()-]/g, '');
    if (!name) {
        problems.push(['name', 'Enter your full name']);
    }
    if (!email) {
        problems.push(['email', 'Enter your email address']);
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(email)) {
        problems.push(['email', 'Enter an email address in the correct format, like name@example.com']);
    }
    if (phone && !/^(\+44|0)\d{9,10}$/.test(phone)) {
        problems.push(['phone', 'Enter a UK phone number, like 0113 496 0420, or leave it empty']);
    }
    if (!form.elements.type.value) {
        problems.push(['type', 'Choose what your question is about']);
    }
    if (message.value.trim().length < 10) {
        problems.push(['message', message.value.trim() ? 'Your message must be at least 10 characters' : 'Enter your message']);
    }
    return problems;
}

function clearErrors() {
    form.querySelectorAll('.has-error').forEach((field) => field.classList.remove('has-error'));
    form.querySelectorAll('[aria-invalid]').forEach((input) => input.removeAttribute('aria-invalid'));
    form.querySelectorAll('.error').forEach((box) => {
        box.textContent = '';
    });
    summary.hidden = true;
}

function showErrors(problems) {
    problems.forEach(([id, text]) => {
        const input = document.getElementById(id);
        input.setAttribute('aria-invalid', 'true');
        input.closest('.field').classList.add('has-error');
        document.getElementById(`${id}-error`).textContent = text;
    });
    summary.querySelector('ul').innerHTML = problems.map(([id, text]) => `<li><a href="#${id}">${text}</a></li>`).join('');
    summary.hidden = false;
    summary.focus();
}

// links in the summary move focus straight to the field
summary.addEventListener('click', (event) => {
    const link = event.target.closest('a');
    if (link) {
        event.preventDefault();
        const input = document.querySelector(link.getAttribute('href'));
        input.focus();
        input.scrollIntoView({ block: 'center' });
    }
});

// a fixed field loses its error as soon as it is valid
form.addEventListener('input', (event) => {
    const input = event.target;
    if (input.getAttribute('aria-invalid') === 'true' && !checks().some(([id]) => id === input.id)) {
        input.removeAttribute('aria-invalid');
        input.closest('.field').classList.remove('has-error');
        document.getElementById(`${input.id}-error`).textContent = '';
    }
});

form.addEventListener('submit', (event) => {
    event.preventDefault();
    clearErrors();
    const problems = checks();
    if (problems.length) {
        showErrors(problems);
        return;
    }
    const topic = form.elements.type.selectedOptions[0].textContent.toLowerCase();
    document.querySelector('#success-text').textContent =
        `We have your message about ${topic}. We will reply to ${form.elements.email.value.trim()} within 2 working days.`;
    form.hidden = true;
    success.hidden = false;
    document.querySelector('#success-title').focus();
});

document.querySelector('#send-another').addEventListener('click', () => {
    form.reset();
    updateCounter();
    success.hidden = true;
    form.hidden = false;
    form.elements.name.focus();
});

// opening hours: mark today and say whether reception is open now
const now = new Date();
const today = now.getDay();
const minutes = now.getHours() * 60 + now.getMinutes();
let openText = 'Reception is closed today.';
document.querySelectorAll('.hours tr').forEach((row) => {
    if (!row.dataset.days.split(',').map(Number).includes(today)) {
        return;
    }
    row.classList.add('is-today');
    const times = Array.from(row.querySelectorAll('time')).map((t) => {
        const [h, m] = t.textContent.split(':').map(Number);
        return h * 60 + m;
    });
    if (times.length === 2) {
        const close = row.querySelectorAll('time')[1].textContent;
        const open = row.querySelectorAll('time')[0].textContent;
        if (minutes >= times[0] && minutes < times[1]) {
            openText = `Reception is open now, until ${close}.`;
        } else if (minutes < times[0]) {
            openText = `Reception opens today at ${open}.`;
        } else {
            openText = 'Reception has closed for today.';
        }
    }
});
document.querySelector('#open-now').textContent = openText;
