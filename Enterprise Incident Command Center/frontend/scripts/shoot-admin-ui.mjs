// Screenshots of the admin screens for a visual check. Usage: SHOTS=<folder> node scripts/shoot-admin-ui.mjs
import { launchBrowser } from './lib/browser.mjs';
const b = await launchBrowser();
try {
  await b.loginAs('mohit@icc.com');
  for (const [path, name, wait] of [['/dashboard', 'ui-dashboard', 'document.querySelector("[role=img]") && [...document.querySelectorAll("h2")].some(h=>h.textContent==="Incidents")'], ['/admin/rules', 'ui-rules', 'document.querySelectorAll("tbody tr").length>=6'], ['/admin/audit', 'ui-audit', 'document.querySelectorAll("tbody tr").length>0']]) {
    await b.go(path);
    await b.waitFor('!!(' + wait + ')', name);
    await b.sleep(600);
    await b.shot(name);
  }
  console.log('done');
} finally { await b.close?.(); }
process.exit(0);
