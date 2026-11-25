// Resume page - Save as PDF, copy email address, print the folded project list, and placeholder highlighting

// "Save as PDF" opens the browser's print dialog, where "Save as PDF" is one of the printers
document.querySelector('#print-btn').addEventListener('click', () => window.print());

// the "More projects" list is folded on screen but should always appear on paper
const moreProjects = document.querySelector('.more-projects');
let wasOpen = false;
window.addEventListener('beforeprint', () => {
    wasOpen = moreProjects.open;
    moreProjects.open = true;
});
window.addEventListener('afterprint', () => {
    moreProjects.open = wasOpen;
});

// copy the email address, with a message that screen readers also hear (role="status")
const copyButton = document.querySelector('#copy-email');
const copyStatus = document.querySelector('#copy-status');
const email = document.querySelector('#email-link').textContent.trim();

copyButton.addEventListener('click', async () => {
    // some browsers never answer the clipboard request on local files, so give up after a second
    const timeout = new Promise((resolve, reject) => {
        setTimeout(() => reject(new Error('no answer')), 1000);
    });
    try {
        await Promise.race([navigator.clipboard.writeText(email), timeout]);
        copyStatus.textContent = `Copied ${email} to your clipboard.`;
    } catch (error) {
        copyStatus.textContent = `Copy blocked by the browser. The address is ${email}.`;
    }
});

// open the page as index.html?placeholders to outline every detail that still needs replacing
if (new URLSearchParams(location.search).has('placeholders')) {
    document.documentElement.classList.add('show-placeholders');
}
