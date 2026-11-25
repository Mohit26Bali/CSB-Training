// Product Catalog UI - filter, sort, wishlist and cart count

const grid = document.querySelector('#product-grid');
const products = Array.from(grid.querySelectorAll('.product'));
const filterButtons = document.querySelectorAll('.filter-btn');
const sortSelect = document.querySelector('#sort-select');
const resultCount = document.querySelector('#result-count');
const cartCount = document.querySelector('#cart-count');
const announcer = document.querySelector('#announcer');

let cartItems = 0;

// filter by category
function applyFilter(category) {
    let shown = 0;
    products.forEach((product) => {
        const matches = category === 'all' || product.dataset.category === category;
        product.hidden = !matches;
        if (matches) {
            shown += 1;
        }
    });
    resultCount.textContent = shown === products.length
        ? `Showing ${shown} products`
        : `Showing ${shown} of ${products.length} products`;
}

filterButtons.forEach((button) => {
    button.addEventListener('click', () => {
        filterButtons.forEach((other) => other.setAttribute('aria-pressed', 'false'));
        button.setAttribute('aria-pressed', 'true');
        applyFilter(button.dataset.filter);
    });
});

// sort the cards
function applySort(order) {
    const sorted = [...products].sort((a, b) => {
        if (order === 'price-asc') {
            return a.dataset.price - b.dataset.price;
        }
        if (order === 'price-desc') {
            return b.dataset.price - a.dataset.price;
        }
        if (order === 'rating') {
            return b.dataset.rating - a.dataset.rating;
        }
        return a.dataset.order - b.dataset.order;
    });
    sorted.forEach((product) => grid.appendChild(product));
}

sortSelect.addEventListener('change', () => applySort(sortSelect.value));

// wishlist hearts and add to cart buttons
grid.addEventListener('click', (event) => {
    const wishlistButton = event.target.closest('.wishlist-btn');
    if (wishlistButton) {
        const saved = wishlistButton.getAttribute('aria-pressed') !== 'true';
        wishlistButton.setAttribute('aria-pressed', String(saved));
        const name = wishlistButton.closest('.product').querySelector('.product-name').textContent;
        announcer.textContent = saved ? `${name} saved to wishlist.` : `${name} removed from wishlist.`;
        return;
    }

    const cartButton = event.target.closest('.btn-cart');
    if (cartButton && !cartButton.disabled) {
        cartItems += 1;
        cartCount.textContent = cartItems;
        announcer.textContent = `${cartButton.dataset.name} added to cart. ${cartItems} ${cartItems === 1 ? 'item' : 'items'} in cart.`;

        cartButton.classList.add('is-added');
        cartCount.classList.add('is-bumped');
        setTimeout(() => {
            cartButton.classList.remove('is-added');
            cartCount.classList.remove('is-bumped');
        }, 400);
    }
});
