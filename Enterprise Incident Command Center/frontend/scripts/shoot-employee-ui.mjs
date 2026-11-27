// Screenshots of the employee pages (home, requests, report form). Usage: SHOTS=<folder> node scripts/shoot-employee-ui.mjs
import { launchBrowser } from './lib/browser.mjs';
const b = await launchBrowser();
try {
  await b.loginAs('ananya@icc.com');
  await b.sleep(1200);
  await b.shot('ui-emp-home');
  await b.go('/incidents');
  await b.waitFor('document.querySelectorAll("tbody tr").length>0', 'rows');
  await b.sleep(500);
  await b.shot('ui-emp-list');
  await b.go('/incidents/new');
  await b.waitFor('document.querySelector("h1")?.textContent==="Report an incident"', 'form');
  await b.shot('ui-emp-new');
  console.log('done');
} finally { await b.close?.(); }
process.exit(0);
