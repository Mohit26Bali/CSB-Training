// Comparison page - the before/after slider. The range input's value is the handle position:
// the old design shows to the left of it and the new design to the right. Mouse, touch and arrow keys all work.

const compare = document.querySelector('#compare');
const range = document.querySelector('#compare-range');

function show(value) {
    compare.style.setProperty('--pos', `${value}%`);
    range.setAttribute('aria-valuetext', `${value}% old design, ${100 - value}% new design`);
}

range.addEventListener('input', () => show(range.value));
show(range.value);
