// Green Corner - shared on every page: saved data, toast messages and the basket drawer

const money = (value) => `£${value.toFixed(2)}`;
const FREE_DELIVERY = 40;

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
        // not saved; still works for this visit
    }
}

// toast messages
const toast = document.createElement('p');
toast.className = 'toast';
toast.setAttribute('role', 'status');
document.body.append(toast);
let toastTimer = null;

function showToast(message) {
    toast.textContent = message;
    toast.classList.add('is-visible');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => toast.classList.remove('is-visible'), 3200);
}

// ---------- the basket, shared by every page ----------
let basket = load('greencorner-basket', []);
const basketDialog = document.querySelector('#basket');
const basketButton = document.querySelector('#basket-btn');
let basketOpener = null;

// tell the page when the basket changes, so stock limits can be updated
function basketChanged() {
    save('greencorner-basket', basket);
    renderBasket();
    document.dispatchEvent(new CustomEvent('basketchange'));
}

function inBasket(productId, sizeId) {
    return basket.filter((item) => item.id === productId && item.size === sizeId).reduce((sum, item) => sum + item.qty, 0);
}

function addItem(item) {
    const existing = basket.find((line) => line.key === item.key);
    if (existing) {
        existing.qty += item.qty;
    } else {
        basket.push(item);
    }
    basketChanged();
}

function renderBasket() {
    const items = basket.reduce((sum, item) => sum + item.qty, 0);
    const subtotal = basket.reduce((sum, item) => sum + item.qty * item.unit, 0);
    const count = document.querySelector('#basket-count');
    count.textContent = items;
    count.hidden = items === 0;
    document.querySelector('#basket-label').textContent = items ? `Basket, ${items} ${items === 1 ? 'item' : 'items'}` : 'Basket, empty';

    document.querySelector('#basket-list').innerHTML = basket.map((item, index) => `<li class="basket-item">
        <img src="${item.image}" alt="" width="1000" height="1000">
        <div>
            <h3><a href="${item.url}">${item.name}</a></h3>
            <p>${item.potName ? `${item.sizeLabel}, ${item.potName}` : item.sizeLabel}</p>
            <p>Quantity ${item.qty} × ${money(item.unit)}</p>
            <button class="remove-btn" type="button" data-remove="${index}">Remove<span class="visually-hidden"> ${item.name}, ${item.sizeLabel}</span></button>
        </div>
        <p class="item-price">${money(item.qty * item.unit)}</p>
    </li>`).join('');
    document.querySelector('#basket-empty').hidden = basket.length > 0;
    document.querySelector('#subtotal').value = money(subtotal);
    document.querySelector('#checkout').disabled = basket.length === 0;
    document.querySelector('#free-progress').value = Math.min(subtotal, FREE_DELIVERY);
    document.querySelector('#free-text').textContent = subtotal >= FREE_DELIVERY
        ? 'You have free delivery!'
        : `Spend ${money(FREE_DELIVERY - subtotal)} more for free delivery.`;
}

function openBasket(message, opener) {
    basketOpener = opener;
    document.querySelector('#basket-message').textContent = message || '';
    renderBasket();
    basketDialog.showModal();
    document.querySelector('#basket-title').focus();
}

basketButton.hidden = false;
basketButton.addEventListener('click', () => openBasket('', basketButton));

document.querySelector('#basket-list').addEventListener('click', (event) => {
    const button = event.target.closest('[data-remove]');
    if (!button) {
        return;
    }
    const removed = basket.splice(Number(button.dataset.remove), 1)[0];
    basketChanged();
    document.querySelector('#basket-message').textContent = `Removed ${removed.name} (${removed.sizeLabel}).`;
    const next = document.querySelector('#basket-list [data-remove]') || document.querySelector('#basket-title');
    next.focus();
});

document.querySelector('#checkout').addEventListener('click', () => {
    showToast('Demo shop: checkout is not available, so nothing has been ordered.');
});

basketDialog.addEventListener('click', (event) => {
    if (event.target === basketDialog || event.target.closest('[data-close]')) {
        basketDialog.close();
    }
});
basketDialog.addEventListener('close', () => {
    if (basketOpener) {
        basketOpener.focus();
    }
});

// keep the basket in step when another tab or page changes it
window.addEventListener('storage', (event) => {
    if (event.key === 'greencorner-basket') {
        basket = load('greencorner-basket', []);
        renderBasket();
        document.dispatchEvent(new CustomEvent('basketchange'));
    }
});

renderBasket();
