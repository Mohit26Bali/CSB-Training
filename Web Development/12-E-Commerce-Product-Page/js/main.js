// Green Corner product page - gallery, full-screen viewer, options and price, quantity, wishlist, delivery estimate, tabs, reviews
// (the basket itself is in basket.js, shared with the home page)

const product = JSON.parse(document.querySelector('#product-data').textContent);

// ---------- gallery: swipeable slides, thumbnails, arrows and hover zoom ----------
const slides = document.querySelector('#slides');
const slideItems = Array.from(slides.querySelectorAll('.slide'));
const thumbs = Array.from(document.querySelectorAll('.thumb'));
const prevImage = document.querySelector('#prev-image');
const nextImage = document.querySelector('#next-image');
const imageCount = document.querySelector('#image-count');
let current = 0;

function showSlideState(index) {
    current = index;
    thumbs.forEach((thumb, i) => thumb.setAttribute('aria-current', String(i === index)));
    imageCount.textContent = `${index + 1} / ${slideItems.length}`;
    prevImage.disabled = index === 0;
    nextImage.disabled = index === slideItems.length - 1;
}

// while a button or thumbnail scrolls the slides, ignore the scroll events from the animation
let lockUntil = 0;

function goToSlide(index) {
    const target = Math.min(Math.max(index, 0), slideItems.length - 1);
    lockUntil = Date.now() + 800;
    slides.scrollTo({ left: target * slides.clientWidth });
    showSlideState(target);
}

let scrollTimer = null;
slides.addEventListener('scroll', () => {
    clearTimeout(scrollTimer);
    scrollTimer = setTimeout(() => {
        if (Date.now() >= lockUntil) {
            showSlideState(Math.round(slides.scrollLeft / slides.clientWidth));
        }
    }, 80);
}, { passive: true });

thumbs.forEach((thumb) => thumb.addEventListener('click', () => goToSlide(Number(thumb.dataset.index))));
prevImage.addEventListener('click', () => goToSlide(current - 1));
nextImage.addEventListener('click', () => goToSlide(current + 1));
slides.addEventListener('keydown', (event) => {
    if (event.key === 'ArrowRight' || event.key === 'ArrowLeft') {
        event.preventDefault();
        goToSlide(current + (event.key === 'ArrowRight' ? 1 : -1));
    }
});
prevImage.hidden = false;
nextImage.hidden = false;
showSlideState(0);

// hover zoom follows the mouse (mouse and trackpad only)
if (window.matchMedia('(hover: hover) and (pointer: fine)').matches) {
    slides.addEventListener('mousemove', (event) => {
        const image = event.target.closest('.slide img');
        if (!image) {
            return;
        }
        const box = image.getBoundingClientRect();
        image.style.transformOrigin = `${((event.clientX - box.left) / box.width) * 100}% ${((event.clientY - box.top) / box.height) * 100}%`;
        slides.classList.add('is-zooming');
    });
    slides.addEventListener('mouseleave', () => slides.classList.remove('is-zooming'));
}

// ---------- full-screen viewer ----------
const lightbox = document.querySelector('#lightbox');
const lightboxImage = document.querySelector('#lightbox-image');
const lightboxCaption = document.querySelector('#lightbox-caption');
const openLightbox = document.querySelector('#open-lightbox');
let lightboxIndex = 0;
let lightboxOpener = null;

function showLightboxImage(index) {
    lightboxIndex = (index + slideItems.length) % slideItems.length;
    const image = slideItems[lightboxIndex].querySelector('img');
    lightboxImage.src = image.src;
    lightboxImage.alt = image.alt;
    lightboxCaption.textContent = `${lightboxIndex + 1} of ${slideItems.length}: ${image.alt}`;
}

function openViewer(opener) {
    lightboxOpener = opener;
    showLightboxImage(current);
    lightbox.showModal();
}

openLightbox.hidden = false;
openLightbox.addEventListener('click', () => openViewer(openLightbox));
slides.addEventListener('click', (event) => {
    if (event.target.closest('.slide img')) {
        openViewer(openLightbox);
    }
});
document.querySelector('#lightbox-prev').addEventListener('click', () => showLightboxImage(lightboxIndex - 1));
document.querySelector('#lightbox-next').addEventListener('click', () => showLightboxImage(lightboxIndex + 1));
lightbox.addEventListener('keydown', (event) => {
    if (event.key === 'ArrowRight' || event.key === 'ArrowLeft') {
        event.preventDefault();
        showLightboxImage(lightboxIndex + (event.key === 'ArrowRight' ? 1 : -1));
    }
});
lightbox.addEventListener('click', (event) => {
    if (event.target === lightbox || event.target.closest('[data-close]')) {
        lightbox.close();
    }
});
lightbox.addEventListener('close', () => {
    goToSlide(lightboxIndex);
    if (lightboxOpener) {
        lightboxOpener.focus();
    }
});

// ---------- options, price and stock ----------
const form = document.querySelector('#buy-form');
form.noValidate = true;
const priceElement = document.querySelector('#price');
const priceStatus = document.querySelector('#price-status');
const stickyPrice = document.querySelector('#sticky-price');
const quantity = document.querySelector('#quantity');
const quantityError = document.querySelector('#quantity-error');
const stockText = document.querySelector('#stock-text');
const stepButtons = document.querySelectorAll('.step');

// "Medium, Terracotta" for plants, "Sage green" for a pot
function choiceText(size, pot) {
    return pot.name ? `${size.label}, ${pot.name}` : size.label;
}

function selection() {
    const size = product.sizes.find((item) => item.id === form.elements.size.value);
    // accessories have no pot to choose
    const pot = form.elements.pot
        ? product.pots.find((item) => item.id === form.elements.pot.value)
        : { id: 'none', name: '', extra: 0 };
    return { size, pot, unit: Math.round((size.price + pot.extra) * 100) / 100 };
}

function maxQuantity(size) {
    return Math.max(0, Math.min(10, size.stock - inBasket(product.id, size.id)));
}

function updateOptions(announce) {
    const { size, pot, unit } = selection();
    priceElement.textContent = money(unit);
    priceElement.value = unit.toFixed(2);
    stickyPrice.textContent = money(unit);
    if (pot.name) {
        document.querySelector('#pot-name').textContent = pot.name;
    }
    if (announce) {
        priceStatus.textContent = `${choiceText(size, pot)}: ${money(unit)}`;
    }

    const left = maxQuantity(size);
    quantity.max = Math.max(left, 1);
    if (Number(quantity.value) > left && left > 0) {
        quantity.value = left;
    }
    if (left === 0) {
        stockText.textContent = `All remaining stock of this ${product.noun} is in your basket`;
    } else if (size.stock <= 5) {
        stockText.textContent = `Only ${size.stock} left in this ${product.noun}`;
    } else {
        stockText.textContent = 'In stock, ready to send';
    }
    stockText.classList.toggle('is-low', size.stock <= 5);
    updateSteppers();
}

function updateSteppers() {
    const value = Number(quantity.value) || 0;
    stepButtons[0].disabled = value <= 1;
    stepButtons[1].disabled = value >= Number(quantity.max);
}

stepButtons.forEach((button) => {
    button.hidden = false;
    button.addEventListener('click', () => {
        const next = (Number(quantity.value) || 1) + Number(button.dataset.step);
        quantity.value = Math.min(Math.max(next, 1), Number(quantity.max));
        quantityError.textContent = '';
        quantity.removeAttribute('aria-invalid');
        updateSteppers();
    });
});

form.addEventListener('change', (event) => {
    if (event.target.name === 'size' || event.target.name === 'pot') {
        updateOptions(true);
    }
});
quantity.addEventListener('input', updateSteppers);

function checkQuantity() {
    const { size } = selection();
    const left = maxQuantity(size);
    let message = '';
    if (left === 0) {
        message = `You already have all ${size.stock} of this ${product.noun} in your basket.`;
    } else if (!quantity.validity.valid || !quantity.value) {
        message = quantity.validity.rangeOverflow
            ? `Only ${left} more can be added in this ${product.noun}.`
            : `Enter a whole number from 1 to ${left}.`;
    }
    quantityError.textContent = message;
    if (message) {
        quantity.setAttribute('aria-invalid', 'true');
        quantity.focus();
        return false;
    }
    quantity.removeAttribute('aria-invalid');
    return true;
}

// ---------- add to basket ----------
function addToBasket() {
    if (!checkQuantity()) {
        return null;
    }
    const { size, pot, unit } = selection();
    const qty = Number(quantity.value);
    addItem({
        key: `${product.id}|${size.id}|${pot.id}`, id: product.id, name: product.name, url: product.url, image: product.image,
        size: size.id, sizeLabel: size.label, pot: pot.id, potName: pot.name, unit, qty
    });
    quantity.value = 1;
    updateSteppers();
    return `Added ${qty} × ${product.name} (${choiceText(size, pot)}) to your basket.`;
}

form.addEventListener('submit', (event) => {
    event.preventDefault();
    const message = addToBasket();
    if (message) {
        openBasket(message, document.querySelector('#add-btn'));
    }
});

document.querySelector('#buy-now').addEventListener('click', (event) => {
    const message = addToBasket();
    if (message) {
        openBasket(`${message} Ready to check out.`, event.currentTarget);
        document.querySelector('#checkout').focus();
    }
});

document.querySelector('#sticky-add').addEventListener('click', (event) => {
    const message = addToBasket();
    if (message) {
        openBasket(message, event.currentTarget);
    }
});

// stock limits depend on what is already in the basket
document.addEventListener('basketchange', () => updateOptions(false));

// ---------- wishlist ----------
const wishButton = document.querySelector('#wish-btn');
const wishNote = document.querySelector('#wishlist-note');
let wishlist = load('greencorner-wishlist', []);

function renderWish() {
    const saved = wishlist.includes(product.id);
    wishButton.setAttribute('aria-pressed', String(saved));
    wishButton.querySelector('.wish-label').textContent = saved ? 'Saved to wishlist' : 'Save to wishlist';
}

wishButton.hidden = false;
wishButton.addEventListener('click', () => {
    if (wishlist.includes(product.id)) {
        wishlist = wishlist.filter((id) => id !== product.id);
        wishNote.textContent = `Removed from your wishlist. ${wishlist.length} ${wishlist.length === 1 ? 'plant' : 'plants'} saved.`;
    } else {
        wishlist.push(product.id);
        wishNote.textContent = `Saved! You have ${wishlist.length} ${wishlist.length === 1 ? 'plant' : 'plants'} in your wishlist.`;
    }
    save('greencorner-wishlist', wishlist);
    renderWish();
});

// ---------- delivery estimate: weekday orders before 2 pm are delivered the next working day ----------
function nextWorkingDay(date) {
    const day = new Date(date);
    do {
        day.setDate(day.getDate() + 1);
    } while (day.getDay() === 0 || day.getDay() === 6);
    return day;
}

function deliveryEstimate(now) {
    const cutoff = new Date(now);
    cutoff.setHours(14, 0, 0, 0);
    const weekday = now.getDay() !== 0 && now.getDay() !== 6;
    const sendDay = weekday && now < cutoff ? now : nextWorkingDay(now);
    const arrives = nextWorkingDay(sendDay);
    const dayName = new Intl.DateTimeFormat('en-GB', { weekday: 'long', day: 'numeric', month: 'long' }).format(arrives);
    if (weekday && now < cutoff) {
        const minutes = Math.ceil((cutoff - now) / 60000);
        const hours = Math.floor(minutes / 60);
        const left = hours ? `${hours} h ${minutes % 60} min` : `${minutes} min`;
        return `Order within ${left} for delivery on ${dayName}`;
    }
    return `Order now for delivery on ${dayName}`;
}

function updateDelivery() {
    document.querySelector('#delivery-text').textContent = `${deliveryEstimate(new Date())}. Free delivery over £40.`;
}

// ---------- product details: tabs on wide screens, accordion on small ones ----------
const details = document.querySelector('#details');
const tabList = details.querySelector('.tab-list');
const panels = Array.from(details.querySelectorAll('.panel'));
const wide = window.matchMedia('(min-width: 900px)');
let activePanel = 0;

tabList.innerHTML = panels.map((panel, i) => `<button class="tab" type="button" role="tab" id="tab-${panel.id}" aria-controls="${panel.id}">${panel.querySelector('.accordion-btn').textContent}</button>`).join('');
const tabs = Array.from(tabList.querySelectorAll('.tab'));

function selectTab(index, moveFocus) {
    activePanel = index;
    tabs.forEach((tab, i) => {
        tab.setAttribute('aria-selected', String(i === index));
        tab.tabIndex = i === index ? 0 : -1;
        panels[i].hidden = i !== index;
    });
    if (moveFocus) {
        tabs[index].focus();
    }
}

function setDetailsMode() {
    const asTabs = wide.matches;
    details.classList.toggle('is-tabs', asTabs);
    tabList.hidden = !asTabs;
    panels.forEach((panel, i) => {
        const body = panel.querySelector('.panel-body');
        const button = panel.querySelector('.accordion-btn');
        if (asTabs) {
            panel.setAttribute('role', 'tabpanel');
            panel.setAttribute('aria-labelledby', `tab-${panel.id}`);
            panel.tabIndex = 0;
            body.hidden = false;
        } else {
            panel.removeAttribute('role');
            panel.setAttribute('aria-labelledby', button.closest('h2').id);
            panel.removeAttribute('tabindex');
            panel.hidden = false;
            const open = i === activePanel;
            button.setAttribute('aria-expanded', String(open));
            body.hidden = !open;
        }
    });
    if (asTabs) {
        selectTab(activePanel, false);
    }
}

tabs.forEach((tab, index) => {
    tab.addEventListener('click', () => selectTab(index, false));
    tab.addEventListener('keydown', (event) => {
        const moves = { ArrowRight: (index + 1) % tabs.length, ArrowLeft: (index - 1 + tabs.length) % tabs.length, Home: 0, End: tabs.length - 1 };
        if (event.key in moves) {
            event.preventDefault();
            selectTab(moves[event.key], true);
        }
    });
});

panels.forEach((panel, index) => {
    const button = panel.querySelector('.accordion-btn');
    button.addEventListener('click', () => {
        const open = button.getAttribute('aria-expanded') !== 'true';
        button.setAttribute('aria-expanded', String(open));
        panel.querySelector('.panel-body').hidden = !open;
        if (open) {
            activePanel = index;
        }
    });
});

wide.addEventListener('change', setDetailsMode);

// "Plant care" in the header opens the care guide
document.querySelectorAll('a[href="#details"]').forEach((link) => {
    link.addEventListener('click', () => {
        activePanel = 1;
        setDetailsMode();
        if (!wide.matches) {
            panels[1].querySelector('.accordion-btn').setAttribute('aria-expanded', 'true');
            panels[1].querySelector('.panel-body').hidden = false;
        }
    });
});

// ---------- reviews: filter by stars, sort, "helpful" ----------
const reviewList = document.querySelector('#review-list');
const reviews = Array.from(reviewList.children);
const ratingBars = Array.from(document.querySelectorAll('.rating-bar'));
const reviewSort = document.querySelector('#review-sort');
let starFilter = null;

function renderReviews() {
    const sorted = [...reviews].sort((a, b) => {
        if (reviewSort.value === 'highest') {
            return b.dataset.rating - a.dataset.rating || b.dataset.date.localeCompare(a.dataset.date);
        }
        if (reviewSort.value === 'lowest') {
            return a.dataset.rating - b.dataset.rating || b.dataset.date.localeCompare(a.dataset.date);
        }
        return b.dataset.date.localeCompare(a.dataset.date);
    });
    let shown = 0;
    sorted.forEach((item) => {
        const visible = starFilter === null || Number(item.dataset.rating) === starFilter;
        item.hidden = !visible;
        shown += visible ? 1 : 0;
        reviewList.appendChild(item);
    });
    document.querySelector('#review-count').textContent = starFilter === null
        ? `Showing all ${reviews.length} reviews`
        : `Showing ${shown} ${starFilter}-star ${shown === 1 ? 'review' : 'reviews'} of ${reviews.length}`;
}

ratingBars.forEach((bar) => {
    bar.addEventListener('click', () => {
        const stars = Number(bar.dataset.stars);
        starFilter = starFilter === stars ? null : stars;
        ratingBars.forEach((other) => other.setAttribute('aria-pressed', String(Number(other.dataset.stars) === starFilter)));
        renderReviews();
    });
});
reviewSort.addEventListener('change', renderReviews);

reviewList.addEventListener('click', (event) => {
    const button = event.target.closest('.helpful');
    if (!button) {
        return;
    }
    const pressed = button.getAttribute('aria-pressed') !== 'true';
    button.setAttribute('aria-pressed', String(pressed));
    button.querySelector('.helpful-count').textContent = pressed ? '1' : '0';
});

// ---------- sticky add-to-basket bar when the main buttons scroll out of view ----------
const stickyBar = document.querySelector('#sticky-buy');
new IntersectionObserver(([entry]) => {
    stickyBar.hidden = entry.isIntersecting || entry.boundingClientRect.top > 0;
}).observe(document.querySelector('#main-actions'));

// ---------- start ----------
updateOptions(false);
renderWish();
updateDelivery();
setInterval(updateDelivery, 60000);
setDetailsMode();
renderReviews();
