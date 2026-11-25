// Registration Form - clear error messages, error summary, password checks and confirmation

const form = document.querySelector('#registration');
const intro = document.querySelector('.form-intro');
const summary = document.querySelector('#error-summary');
const summaryTitle = document.querySelector('#error-summary-title');
const errorList = document.querySelector('#error-list');
const confirmation = document.querySelector('#confirmation');
const password = document.querySelector('#password');
const confirmPassword = document.querySelector('#confirm-password');

// the browser still checks every rule; JavaScript only takes over how errors are shown
form.noValidate = true;

// fields in page order, with a clear message for each rule
const messages = {
    firstName: { valueMissing: 'Enter your first name' },
    lastName: { valueMissing: 'Enter your last name' },
    email: {
        valueMissing: 'Enter your email address',
        typeMismatch: 'Enter an email address in the format name@example.com',
        patternMismatch: 'Enter an email address in the format name@example.com'
    },
    phone: {
        valueMissing: 'Enter your mobile number',
        patternMismatch: 'Enter a phone number using only numbers, spaces, +, - and brackets (7 to 20 characters)'
    },
    dob: {
        valueMissing: 'Enter your date of birth',
        badInput: 'Enter a real date',
        rangeOverflow: 'You must be 16 or older on 15 April 2027',
        rangeUnderflow: 'Enter a date of birth after 1 January 1920'
    },
    country: { valueMissing: 'Choose the country you live in' },
    username: {
        valueMissing: 'Choose a username',
        tooShort: 'Username must be at least 3 characters',
        patternMismatch: 'Username must be 3 to 20 characters, using only lowercase letters, numbers and underscores'
    },
    password: {
        valueMissing: 'Create a password',
        tooShort: 'Password must be at least 8 characters',
        patternMismatch: 'Password must be at least 8 characters and include a letter and a number'
    },
    confirmPassword: {
        valueMissing: 'Type your password again',
        customError: 'The passwords do not match'
    },
    ticket: { valueMissing: 'Choose a ticket type' },
    arrival: {
        valueMissing: 'Enter your arrival date',
        badInput: 'Enter a real date',
        rangeUnderflow: 'Arrival date must be between 14 and 16 April 2027',
        rangeOverflow: 'Arrival date must be between 14 and 16 April 2027'
    },
    experience: {
        valueMissing: 'Enter your years of experience (0 is fine)',
        badInput: 'Enter a number',
        rangeUnderflow: 'Years of experience cannot be less than 0',
        rangeOverflow: 'Years of experience must be 50 or less',
        stepMismatch: 'Enter a whole number, for example 3'
    },
    role: { valueMissing: 'Enter your job role' },
    workshops: { customError: 'Choose no more than 2 workshops' },
    portfolio: {
        typeMismatch: 'Enter a full web address starting with https://',
        patternMismatch: 'Enter a full web address starting with https://'
    },
    shirt: { valueMissing: 'Choose a T-shirt size, or "No T-shirt, thanks"' },
    diet: { valueMissing: 'Choose your dietary needs, or "No special needs"' },
    terms: { valueMissing: 'You must agree to the code of conduct and ticket terms' }
};

const fieldNames = Object.keys(messages);
const ruleOrder = ['valueMissing', 'badInput', 'typeMismatch', 'tooShort', 'tooLong',
    'rangeUnderflow', 'rangeOverflow', 'stepMismatch', 'patternMismatch', 'customError'];

const currentErrors = new Map();
const checkedOnce = new Set();

// a name can belong to one input or a group of radios or checkboxes
function controlsFor(name) {
    const item = form.elements.namedItem(name);
    return item instanceof RadioNodeList ? Array.from(item) : [item];
}

function errorElementFor(name) {
    const controls = controlsFor(name);
    const id = controls.length > 1 ? name : controls[0].id;
    return document.querySelector(`#${id}-error`);
}

function messageFor(name) {
    const first = controlsFor(name)[0];
    const rule = ruleOrder.find((key) => first.validity[key]);
    return messages[name][rule] || first.validationMessage;
}

// rules the HTML attributes cannot express on their own
function updateCustomRules() {
    const mismatch = confirmPassword.value !== '' && confirmPassword.value !== password.value;
    confirmPassword.setCustomValidity(mismatch ? 'The passwords do not match' : '');

    const workshops = controlsFor('workshops');
    const chosen = workshops.filter((box) => box.checked).length;
    workshops[0].setCustomValidity(chosen > 2 ? 'Choose no more than 2 workshops' : '');
}

function validate(name) {
    const controls = controlsFor(name);
    const valid = controls[0].validity.valid;
    const errorElement = errorElementFor(name);

    errorElement.textContent = '';
    if (valid) {
        currentErrors.delete(name);
        controls.forEach((control) => control.removeAttribute('aria-invalid'));
    } else {
        const message = messageFor(name);
        currentErrors.set(name, message);
        controls.forEach((control) => control.setAttribute('aria-invalid', 'true'));

        const prefix = document.createElement('span');
        prefix.className = 'visually-hidden';
        prefix.textContent = 'Error: ';
        errorElement.append(prefix, message);
    }

    checkedOnce.add(name);
    if (!summary.hidden) {
        showSummary(false);
    }
    return valid;
}

// error summary at the top of the form, with a link to each problem
function showSummary(moveFocus) {
    const names = fieldNames.filter((name) => currentErrors.has(name));
    if (names.length === 0) {
        summary.hidden = true;
        return;
    }

    summaryTitle.textContent = names.length === 1
        ? 'There is 1 problem with your registration'
        : `There are ${names.length} problems with your registration`;

    errorList.innerHTML = '';
    names.forEach((name) => {
        const link = document.createElement('a');
        link.href = `#${controlsFor(name)[0].id}`;
        link.dataset.name = name;
        link.textContent = currentErrors.get(name);
        const item = document.createElement('li');
        item.append(link);
        errorList.append(item);
    });

    summary.hidden = false;
    if (moveFocus) {
        summary.focus();
    }
}

errorList.addEventListener('click', (event) => {
    const link = event.target.closest('a');
    if (!link) {
        return;
    }
    event.preventDefault();
    const control = controlsFor(link.dataset.name)[0];
    control.closest('.field, .group').scrollIntoView({ block: 'start' });
    control.focus({ preventScroll: true });
});

// show errors after a field is changed, then update them while the user fixes it
form.addEventListener('change', (event) => {
    const name = event.target.name;
    updateCustomRules();
    if (messages[name]) {
        validate(name);
    }
    if (name === 'password' && checkedOnce.has('confirmPassword')) {
        validate('confirmPassword');
    }
    updateProgress();
});

form.addEventListener('input', (event) => {
    const name = event.target.name;
    updateCustomRules();
    if (checkedOnce.has(name)) {
        validate(name);
    }
    if (name === 'password' && checkedOnce.has('confirmPassword')) {
        validate('confirmPassword');
    }
    updateProgress();
});

// progress list: a section is complete when every field in it is valid
const sections = document.querySelectorAll('.form-section');

function updateProgress() {
    sections.forEach((section) => {
        const fields = Array.from(section.elements).filter((element) => element.willValidate);
        const complete = fields.every((field) => field.validity.valid);
        const step = document.querySelector(`.progress li[data-section="${section.dataset.section}"]`);
        step.classList.toggle('is-complete', complete);
        step.querySelector('.progress-state').textContent = complete ? ' (complete)' : ' (not complete)';
    });
}

// ticket choice updates the order summary
const orderTicket = document.querySelector('#order-ticket');
const orderTotal = document.querySelector('#order-total');

function selectedTicket() {
    return controlsFor('ticket').find((radio) => radio.checked);
}

function updateOrder() {
    const ticket = selectedTicket();
    orderTicket.value = ticket ? ticket.dataset.label : 'None chosen yet';
    orderTotal.value = `£${ticket ? ticket.dataset.price : 0}`;
}

controlsFor('ticket').forEach((radio) => radio.addEventListener('change', updateOrder));

// password: show or hide, and a strength meter
const showButton = document.querySelector('.show-password');
const strength = document.querySelector('.strength');
const strengthMeter = document.querySelector('#strength-meter');
const strengthWord = document.querySelector('#strength-word');

showButton.hidden = false;
strength.hidden = false;

showButton.addEventListener('click', () => {
    const showing = password.type === 'text';
    password.type = showing ? 'password' : 'text';
    showButton.firstChild.textContent = showing ? 'Show' : 'Hide';
});

function passwordScore(value) {
    if (value.length < 8) {
        return 0;
    }
    let score = 1;
    if (value.length >= 12) {
        score += 1;
    }
    if (/[a-z]/.test(value) && /[A-Z]/.test(value)) {
        score += 1;
    }
    if (/[0-9]/.test(value) && /[^A-Za-z0-9]/.test(value)) {
        score += 1;
    }
    return score;
}

function updateStrength() {
    const words = ['Too short', 'Weak', 'Fair', 'Good', 'Strong'];
    const score = passwordScore(password.value);
    strengthMeter.value = score;
    strengthWord.textContent = password.value === '' ? 'not set' : words[score];
}

password.addEventListener('input', updateStrength);

// character counter for the text area
const needs = document.querySelector('#needs');
const needsCount = document.querySelector('#needs-count');

function updateCount() {
    const remaining = needs.maxLength - needs.value.length;
    needsCount.textContent = `${remaining} of ${needs.maxLength} characters left`;
    needsCount.classList.toggle('is-near-limit', remaining <= 30);
}

needs.addEventListener('input', updateCount);

// submit: check everything, then show the summary or the confirmation
form.addEventListener('submit', (event) => {
    event.preventDefault();
    updateCustomRules();
    fieldNames.forEach(validate);

    if (currentErrors.size > 0) {
        showSummary(true);
        return;
    }
    showConfirmation();
});

function showConfirmation() {
    const data = new FormData(form);
    const ticket = selectedTicket();
    const arrival = new Date(`${data.get('arrival')}T12:00:00`);
    const workshops = data.getAll('workshops');
    const letters = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    let reference = 'NDS27-';
    for (let i = 0; i < 5; i += 1) {
        reference += letters[Math.floor(Math.random() * letters.length)];
    }

    document.querySelector('#confirm-name').textContent = data.get('firstName').trim();
    document.querySelector('#confirm-ref').textContent = reference;
    document.querySelector('#confirm-email').textContent = data.get('email');
    document.querySelector('#confirm-ticket').textContent = ticket.dataset.label;
    document.querySelector('#confirm-arrival').textContent = arrival.toLocaleDateString('en-GB', {
        weekday: 'long', day: 'numeric', month: 'long', year: 'numeric'
    });
    document.querySelector('#confirm-workshops').textContent = workshops.length ? workshops.join(', ') : 'None chosen';
    document.querySelector('#confirm-username').textContent = data.get('username');
    document.querySelector('#confirm-total').textContent = `£${ticket.dataset.price}`;

    // clear the form, including the password, as soon as it has been used, but keep the booking in the side panel
    clearForm();
    orderTicket.value = ticket.dataset.label;
    orderTotal.value = `£${ticket.dataset.price}`;
    document.querySelectorAll('.progress li').forEach((step) => {
        step.classList.add('is-complete');
        step.querySelector('.progress-state').textContent = ' (complete)';
    });
    form.hidden = true;
    intro.hidden = true;
    confirmation.hidden = false;
    confirmation.querySelector('h2').focus();
}

function clearForm() {
    form.reset();
    password.type = 'password';
    showButton.firstChild.textContent = 'Show';
    currentErrors.clear();
    checkedOnce.clear();
    fieldNames.forEach((name) => {
        errorElementFor(name).textContent = '';
        controlsFor(name).forEach((control) => control.removeAttribute('aria-invalid'));
    });
    summary.hidden = true;
    updateCustomRules();
    updateOrder();
    updateStrength();
    updateProgress();
    needsCount.textContent = `Up to ${needs.maxLength} characters`;
    needsCount.classList.remove('is-near-limit');
}

document.querySelector('#register-another').addEventListener('click', () => {
    clearForm();
    confirmation.hidden = true;
    form.hidden = false;
    intro.hidden = false;
    document.querySelector('#form-title').scrollIntoView();
    document.querySelector('#first-name').focus({ preventScroll: true });
});

updateOrder();
updateProgress();
