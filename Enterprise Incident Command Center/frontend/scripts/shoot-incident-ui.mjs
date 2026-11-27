// Takes screenshots of the incident list and detail for a visual look. Usage: SHOTS=<folder> node scripts/shoot-incident-ui.mjs
import { launchBrowser } from './lib/browser.mjs';
const b = await launchBrowser();
try {
  await b.loginAs('mohit@icc.com');
  await b.go('/incidents');
  await b.waitFor('document.querySelectorAll("tbody tr").length>0', 'rows');
  await b.sleep(500);
  await b.shot('ui-list');
  const id = await b.js(`document.querySelector('tbody tr a').getAttribute('href').split('/').pop()`);
  await b.go('/incidents/' + id);
  await b.waitFor('/^INC/.test(document.querySelector("h1")?.textContent||"")', 'detail');
  await b.sleep(500);
  await b.shot('ui-detail');
  console.log('done', id);
} finally { await b.close?.(); }
process.exit(0);
