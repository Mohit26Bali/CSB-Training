// Summit Bouldering - the three-step booking form: showing the right fields, checking each step,
// a live price summary, the confirmation and an "Add to calendar" file

const form = document.querySelector('#booking-form');
const steps = Array.from(form.querySelectorAll('.form-step'));
const progressItems = Array.from(document.querySelectorAll('#progress-steps li'));
const errorSummary = document.querySelector('#error-summary');
const confirmation = document.querySelector('#confirmation');
const summaryList = document.querySelector('#summary-list');
const summaryTotal = document.querySelector('#summary-total');
const classSelect = document.querySelector('#class-choice');
const dateInput = document.querySelector('#visit-date');
const adultsInput = document.querySelector('#adults');
const childrenInput = document.querySelector('#children');
const shoesBox = document.querySelector('#shoes');
const shoesIncluded = document.querySelector('#shoes-included');

const PRICES = {
    taster: { adult: 22, child: 16, name: 'Taster session' },
    daypass: { adult: 14, child: 10, name: 'Day pass' },
};
const SHOE_HIRE = 4;
const MAX_DAYS_AHEAD = 60;
const DAY_NUMBERS = { sun: 0, mon: 1, tue: 2, wed: 3, thu: 4, fri: 5, sat: 6 };
let currentStep = 1;
let lastBooking = null;

// ---------- dates ----------
function isoDate(date) {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

function parseDate(value) {
    const [y, m, d] = value.split('-').map(Number);
    return new Date(y, m - 1, d);
}

function longDate(date) {
    return date.toLocaleDateString('en-GB', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
}

function shortDate(date) {
    return date.toLocaleDateString('en-GB', { weekday: 'short', day: 'numeric', month: 'short' });
}

function addDays(date, days) {
    const copy = new Date(date);
    copy.setDate(copy.getDate() + days);
    return copy;
}

// the next time a weekly class runs: later today if it has not started yet, otherwise next week
function nextClassDate(dayKey, time, now = new Date()) {
    const [h, m] = time.split(':').map(Number);
    let ahead = (DAY_NUMBERS[dayKey] - now.getDay() + 7) % 7;
    if (ahead === 0 && (now.getHours() * 60 + now.getMinutes()) >= h * 60 + m) {
        ahead = 7;
    }
    const date = addDays(now, ahead);
    date.setHours(h, m, 0, 0);
    return date;
}

const todayDate = new Date();
dateInput.min = isoDate(todayDate);
dateInput.max = isoDate(addDays(todayDate, MAX_DAYS_AHEAD));

// ---------- reading the form ----------
function sessionType() {
    return form.elements.sessionType.value;
}

function chosenClass() {
    const option = classSelect.selectedOptions[0];
    if (!option || !option.value) {
        return null;
    }
    return {
        id: option.value,
        name: option.dataset.name,
        day: option.dataset.day,
        time: option.dataset.time,
        type: option.dataset.type,
        price: Number(option.dataset.price),
        spaces: Number(option.dataset.spaces),
    };
}

function isKidsClub() {
    const lesson = sessionType() === 'class' && chosenClass();
    return Boolean(lesson && lesson.name.startsWith("Kids' Club"));
}

// shoes come with a taster and with Intro to Bouldering
function shoesIncludedNow() {
    const lesson = chosenClass();
    return sessionType() === 'taster' || (sessionType() === 'class' && lesson !== null && lesson.name === 'Intro to Bouldering');
}

function count(input) {
    const value = Number.parseInt(input.value, 10);
    return Number.isNaN(value) ? 0 : value;
}

// ---------- showing the right fields ----------
function updateFields() {
    const type = sessionType();
    form.querySelectorAll('[data-show-for]').forEach((field) => {
        field.hidden = !field.dataset.showFor.split(' ').includes(type);
    });

    // Kids' Club is for children only, so the adults counter is set to 0 and locked
    const kidsOnly = isKidsClub();
    adultsInput.disabled = kidsOnly;
    if (kidsOnly) {
        adultsInput.value = 0;
        if (count(childrenInput) === 0) {
            childrenInput.value = 1;
        }
    } else if (adultsInput.dataset.wasLocked === 'true' && count(adultsInput) === 0) {
        adultsInput.value = 1;
    }
    adultsInput.dataset.wasLocked = String(kidsOnly);

    const included = shoesIncludedNow();
    shoesBox.closest('label').hidden = included;
    shoesIncluded.hidden = !included;
    updateCounters();
    updateSummary();
}

function updateCounters() {
    form.querySelectorAll('.counter-btn').forEach((button) => {
        const input = document.getElementById(button.dataset.stepFor);
        const change = Number(button.dataset.change);
        const value = count(input);
        button.disabled = input.disabled || (change < 0 ? value <= Number(input.min) : value >= Number(input.max));
    });
}

form.querySelectorAll('.counter-btn').forEach((button) => {
    button.addEventListener('click', () => {
        const input = document.getElementById(button.dataset.stepFor);
        const next = Math.min(Number(input.max), Math.max(Number(input.min), count(input) + Number(button.dataset.change)));
        input.value = next;
        updateCounters();
        updateSummary();
    });
});

// ---------- price summary ----------
function money(value) {
    return `£${value.toFixed(2)}`;
}

function people(adults, children) {
    const parts = [];
    if (adults) {
        parts.push(`${adults} ${adults === 1 ? 'adult' : 'adults'}`);
    }
    if (children) {
        parts.push(`${children} under-16${children === 1 ? '' : 's'}`);
    }
    return parts.join(', ') || 'None yet';
}

function booking() {
    const type = sessionType();
    const adults = count(adultsInput);
    const children = count(childrenInput);
    const climbers = adults + children;
    const lesson = chosenClass();
    let name;
    let when = 'Not chosen yet';
    let start = null;
    let minutes = 60;
    let climbing = 0;

    if (type === 'class') {
        name = lesson ? lesson.name : 'A class';
        if (lesson) {
            start = nextClassDate(lesson.day, lesson.time);
            when = `${shortDate(start)}, ${lesson.time}`;
            climbing = lesson.price * climbers;
            minutes = 90;
        }
    } else {
        name = PRICES[type].name;
        climbing = PRICES[type].adult * adults + PRICES[type].child * children;
        if (dateInput.value) {
            start = parseDate(dateInput.value);
            when = shortDate(start);
            const slot = form.elements.slot.value;
            if (type === 'taster' && slot) {
                when += `, ${slot}`;
                const [h, m] = slot.split(':').map(Number);
                start.setHours(h, m, 0, 0);
            } else if (type === 'daypass') {
                start.setHours(10, 0, 0, 0);
                minutes = 180;
            }
        }
    }
    const shoes = !shoesIncludedNow() && shoesBox.checked ? SHOE_HIRE * climbers : 0;
    return { type, name, when, start, minutes, adults, children, climbers, climbing, shoes, total: climbing + shoes };
}

function updateSummary() {
    const b = booking();
    const rows = [
        ['Session', b.name],
        ['When', b.when],
        ['Climbers', people(b.adults, b.children)],
        ['Climbing', money(b.climbing)],
        ['Shoe hire', shoesIncludedNow() ? 'Included' : money(b.shoes)],
    ];
    summaryList.innerHTML = rows.map(([label, value]) => `<div><dt>${label}</dt><dd>${value}</dd></div>`).join('');
    summaryTotal.value = money(b.total);
    summaryTotal.textContent = money(b.total);
}

form.addEventListener('change', (event) => {
    if (event.target.name === 'sessionType' || event.target === classSelect) {
        updateFields();
    } else {
        updateCounters();
        updateSummary();
    }
});
form.addEventListener('input', (event) => {
    // fixing a field clears its error straight away
    const field = event.target;
    if (field.getAttribute('aria-invalid') === 'true') {
        setError(field, '');
    }
    if (field === adultsInput || field === childrenInput) {
        updateCounters();
        updateSummary();
    }
});

// ---------- checking each step ----------
function errorFor(field) {
    if (field.name === 'slot') {
        return document.querySelector('#slot-error');
    }
    return document.getElementById(`${field.id}-error`);
}

function setError(field, message) {
    const target = field.name === 'slot' ? document.querySelector('#slot-group') : field;
    const box = errorFor(field);
    if (box) {
        box.textContent = message;
    }
    if (field.name === 'slot') {
        form.querySelectorAll('input[name="slot"]').forEach((radio) => radio.setAttribute('aria-invalid', String(Boolean(message))));
    } else {
        target.setAttribute('aria-invalid', String(Boolean(message)));
    }
}

function checkStep(step) {
    const problems = [];
    const add = (field, message) => problems.push({ field, message });
    const type = sessionType();

    if (step === 1) {
        if (type === 'taster' || type === 'daypass') {
            if (!dateInput.value) {
                add(dateInput, 'Choose the date of your visit.');
            } else if (dateInput.value < dateInput.min) {
                add(dateInput, 'The date must be today or later.');
            } else if (dateInput.value > dateInput.max) {
                add(dateInput, `You can book up to ${MAX_DAYS_AHEAD} days ahead.`);
            }
        }
        if (type === 'taster') {
            const slot = form.elements.slot.value;
            const firstSlot = form.querySelector('input[name="slot"]');
            if (!slot) {
                add(firstSlot, 'Choose a start time for your taster.');
            } else if (dateInput.value === isoDate(new Date())) {
                const [h, m] = slot.split(':').map(Number);
                const now = new Date();
                if (h * 60 + m <= now.getHours() * 60 + now.getMinutes()) {
                    add(firstSlot, 'That time has already passed today. Choose a later time or another day.');
                }
            }
        }
        if (type === 'class' && !chosenClass()) {
            add(classSelect, 'Choose a class from the list.');
        }
    }

    if (step === 2) {
        const adults = count(adultsInput);
        const children = count(childrenInput);
        const lesson = type === 'class' ? chosenClass() : null;
        if (adults + children === 0) {
            add(adultsInput, 'Add at least one climber.');
        } else if (isKidsClub() && children === 0) {
            add(childrenInput, "Kids' Club needs at least one child.");
        } else if (!isKidsClub() && children > 0 && adults === 0) {
            add(adultsInput, 'Under-16s must climb with an adult. Add at least one adult.');
        } else if (lesson && adults + children > lesson.spaces) {
            add(childrenInput.value > 0 ? childrenInput : adultsInput,
                `Only ${lesson.spaces} ${lesson.spaces === 1 ? 'space is' : 'spaces are'} left in this class.`);
        }
        if (!form.elements.experience.value) {
            add(form.elements.experience, 'Tell us how much climbing you have done, so we can help.');
        }
    }

    if (step === 3) {
        const name = form.elements.fullName;
        const email = form.elements.email;
        const phone = form.elements.phone;
        if (name.value.trim().length < 2) {
            add(name, 'Enter your full name.');
        }
        if (!email.value.trim()) {
            add(email, 'Enter your email address.');
        } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(email.value.trim())) {
            add(email, 'Enter an email address like name@example.com.');
        }
        const digits = phone.value.replace(/[\s()-]/g, '');
        if (digits && !/^(\+447\d{9}|07\d{9})$/.test(digits)) {
            add(phone, 'Enter a UK mobile number like 07700 900123, or leave it empty.');
        }
        if (!form.elements.safety.checked) {
            add(form.elements.safety, 'Confirm that your group will follow the safety rules.');
        }
    }
    return problems;
}

function clearErrors(stepElement) {
    stepElement.querySelectorAll('[aria-invalid="true"]').forEach((field) => field.setAttribute('aria-invalid', 'false'));
    stepElement.querySelectorAll('.error').forEach((box) => {
        box.textContent = '';
    });
    errorSummary.hidden = true;
}

// errors show next to each field and in a summary at the top that links to them
function showErrors(problems) {
    problems.forEach(({ field, message }) => setError(field, message));
    errorSummary.querySelector('ul').innerHTML = problems
        .map(({ field, message }) => `<li><a href="#${field.id}">${message}</a></li>`)
        .join('');
    errorSummary.hidden = false;
    errorSummary.focus();
}

errorSummary.addEventListener('click', (event) => {
    const link = event.target.closest('a');
    if (link) {
        event.preventDefault();
        const field = document.querySelector(link.getAttribute('href'));
        field.focus();
        field.scrollIntoView({ block: 'center' });
    }
});

// ---------- moving between steps ----------
function goTo(number, moveFocus = true) {
    currentStep = number;
    steps.forEach((step) => {
        step.hidden = Number(step.dataset.step) !== number;
    });
    progressItems.forEach((item, index) => {
        if (index + 1 === number) {
            item.setAttribute('aria-current', 'step');
        } else {
            item.removeAttribute('aria-current');
        }
        item.classList.toggle('is-done', index + 1 < number);
    });
    if (moveFocus) {
        const step = steps[number - 1];
        step.tabIndex = -1;
        step.focus({ preventScroll: true });
        document.querySelector('#booking').scrollIntoView({ block: 'start' });
    }
}

form.querySelectorAll('[data-next]').forEach((button) => {
    button.addEventListener('click', () => {
        const stepElement = steps[currentStep - 1];
        clearErrors(stepElement);
        const problems = checkStep(currentStep);
        if (problems.length) {
            showErrors(problems);
            return;
        }
        goTo(currentStep + 1);
    });
});

form.querySelectorAll('[data-back]').forEach((button) => {
    button.addEventListener('click', () => {
        clearErrors(steps[currentStep - 1]);
        goTo(currentStep - 1);
    });
});

// ---------- confirming ----------
function reference() {
    const letters = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    let code = '';
    for (let i = 0; i < 4; i += 1) {
        code += letters[Math.floor(Math.random() * letters.length)];
    }
    return `SB-${code}`;
}

form.addEventListener('submit', (event) => {
    event.preventDefault();
    const stepElement = steps[currentStep - 1];
    clearErrors(stepElement);
    // check every step again, in case something changed after going back
    for (let step = 1; step <= 3; step += 1) {
        const problems = checkStep(step);
        if (problems.length) {
            goTo(step, false);
            showErrors(problems);
            return;
        }
    }
    const b = booking();
    lastBooking = { ...b, ref: reference(), email: form.elements.email.value.trim() };
    const time = b.type === 'daypass' ? '' : ` at ${b.start.toTimeString().slice(0, 5)}`;
    document.querySelector('#confirm-text').textContent =
        `${b.name} on ${longDate(b.start)}${time} for ${people(b.adults, b.children)}. `
        + `Your reference is ${lastBooking.ref}, and the total of ${money(b.total)} is paid at the desk. `
        + `A confirmation would be sent to ${lastBooking.email}.`;
    form.hidden = true;
    document.querySelector('#progress-steps').hidden = true;
    confirmation.hidden = false;
    document.querySelector('#confirm-title').focus();
});

// ---------- add to calendar: a small .ics file ----------
function icsTime(date) {
    return `${isoDate(date).replace(/-/g, '')}T${String(date.getHours()).padStart(2, '0')}${String(date.getMinutes()).padStart(2, '0')}00`;
}

document.querySelector('#calendar-btn').addEventListener('click', () => {
    if (!lastBooking) {
        return;
    }
    const end = new Date(lastBooking.start.getTime() + lastBooking.minutes * 60000);
    const ics = [
        'BEGIN:VCALENDAR', 'VERSION:2.0', 'PRODID:-//Summit Bouldering//Booking//EN',
        'BEGIN:VEVENT',
        `UID:${lastBooking.ref}@summit-bouldering.example`,
        `DTSTAMP:${icsTime(new Date())}`,
        `DTSTART:${icsTime(lastBooking.start)}`,
        `DTEND:${icsTime(end)}`,
        `SUMMARY:${lastBooking.name} at Summit Bouldering`,
        'LOCATION:Unit 4\\, Foundry Lane\\, Sheffield S3 8XX',
        `DESCRIPTION:Booking ${lastBooking.ref}. Pay at the desk.`,
        'END:VEVENT', 'END:VCALENDAR',
    ].join('\r\n');
    const link = document.createElement('a');
    link.href = URL.createObjectURL(new Blob([ics], { type: 'text/calendar' }));
    link.download = `summit-booking-${lastBooking.ref}.ics`;
    document.body.append(link);
    link.click();
    link.remove();
    showToast('Calendar file downloaded.');
});

document.querySelector('#new-booking').addEventListener('click', () => {
    form.reset();
    form.hidden = false;
    document.querySelector('#progress-steps').hidden = false;
    confirmation.hidden = true;
    clearErrors(form);
    updateFields();
    goTo(1);
});

// ---------- links from the rest of the page ----------
// "Book" on a timetable class chooses that class and jumps to the form
document.querySelectorAll('.session-book[data-class]').forEach((button) => {
    button.addEventListener('click', () => {
        form.elements.sessionType.value = 'class';
        classSelect.value = button.dataset.class;
        form.hidden = false;
        confirmation.hidden = true;
        document.querySelector('#progress-steps').hidden = false;
        clearErrors(form);
        updateFields();
        goTo(1, false);
        document.querySelector('#book').scrollIntoView({ block: 'start' });
        classSelect.focus({ preventScroll: true });
        const lesson = chosenClass();
        showToast(`${lesson.name} chosen. Continue to add your climbers.`);
    });
});

// "Book a day pass" on the prices section chooses a day pass
document.querySelectorAll('[data-book-type]').forEach((link) => {
    link.addEventListener('click', () => {
        form.elements.sessionType.value = link.dataset.bookType;
        updateFields();
        goTo(1, false);
    });
});

updateFields();
