// Long Way Round story page - reading progress, table of contents, copy link and "read" tracking

const body = document.querySelector('#story-body');
const bar = document.querySelector('#progress-bar');
const toc = document.querySelector('#toc');
const tocDetails = toc.querySelector('details');
const tocLinks = Array.from(toc.querySelectorAll('a'));
const slug = document.querySelector('.story').dataset.slug;
const wide = window.matchMedia('(min-width: 70rem)');

// ---------- reading progress: how far through the story text the reader is ----------
function progress() {
    const start = body.offsetTop;
    const distance = body.offsetHeight - window.innerHeight * 0.6;
    const value = Math.min(1, Math.max(0, (window.scrollY - start + window.innerHeight * 0.4) / distance));
    bar.style.transform = `scaleX(${value})`;
    return value;
}

// the sum is cheap, so it simply runs on every scroll event
window.addEventListener('scroll', progress, { passive: true });
window.addEventListener('resize', progress);
progress();

// ---------- table of contents ----------
// open beside the story on large screens; folded up above it on small screens
function placeToc() {
    tocDetails.open = wide.matches;
}
placeToc();
wide.addEventListener('change', placeToc);

// on large screens the list is always shown, so the summary should not fold it away
tocDetails.querySelector('summary').addEventListener('click', (event) => {
    if (wide.matches) {
        event.preventDefault();
    }
});

tocLinks.forEach((link) => {
    link.addEventListener('click', () => {
        if (!wide.matches) {
            tocDetails.open = false;
        }
    });
});

// highlight the section being read
const headings = tocLinks.map((link) => document.getElementById(link.hash.slice(1)));

function markCurrent(id) {
    tocLinks.forEach((link) => {
        if (link.hash === `#${id}`) {
            link.setAttribute('aria-current', 'true');
        } else {
            link.removeAttribute('aria-current');
        }
    });
}

function currentSection() {
    const line = window.innerHeight * 0.3;
    let current = null;
    headings.forEach((heading) => {
        if (heading.getBoundingClientRect().top <= line) {
            current = heading;
        }
    });
    if (current) {
        markCurrent(current.id);
    } else {
        tocLinks.forEach((link) => link.removeAttribute('aria-current'));
    }
}

const watcher = new IntersectionObserver(currentSection, { rootMargin: '0px 0px -70% 0px' });
headings.forEach((heading) => watcher.observe(heading));
window.addEventListener('scroll', currentSection, { passive: true });

// ---------- toast messages ----------
const toast = document.querySelector('#toast');
let toastTimer = null;

function showToast(message) {
    toast.textContent = message;
    toast.classList.add('is-visible');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => toast.classList.remove('is-visible'), 3000);
}

// ---------- copy link ----------
document.querySelector('#share-btn').addEventListener('click', async () => {
    const address = location.href.split('#')[0];
    // give up after a second: some browsers never answer the clipboard request on local files
    const timeout = new Promise((resolve, reject) => {
        setTimeout(() => reject(new Error('no answer')), 1000);
    });
    try {
        await Promise.race([navigator.clipboard.writeText(address), timeout]);
        showToast('Link copied to your clipboard.');
    } catch (error) {
        // the clipboard can be blocked (for example on pages opened from a file)
        showToast(`Copy this link: ${address}`);
    }
});

// ---------- remember finished stories ----------
// a story counts as read once the writer's box at the end has come into view (or been scrolled past,
// which happens when a reader jumps straight to the bottom with the End key)
const end = document.querySelector('.author-box');

function checkFinished() {
    if (end.getBoundingClientRect().top < window.innerHeight) {
        const done = readStories();
        if (!done.includes(slug)) {
            done.push(slug);
            save('lwr-read', done);
        }
        window.removeEventListener('scroll', checkFinished);
    }
}
window.addEventListener('scroll', checkFinished, { passive: true });
