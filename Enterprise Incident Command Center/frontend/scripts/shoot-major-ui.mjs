// Screenshots of the major incident workbench and the incident page with its Major incident tab. Usage: SHOTS=<folder> node scripts/shoot-major-ui.mjs
import { launchBrowser } from './lib/browser.mjs';
const b = await launchBrowser();
try {
  await b.loginAs('mohit@icc.com');
  await b.go('/major-incidents');
  await b.waitFor('document.querySelectorAll("tbody tr a").length>0', 'major rows');
  const href = await b.js(`document.querySelector('tbody tr a').getAttribute('href')`);
  await b.go(href);
  await b.waitFor('/^Major incident #/.test(document.querySelector("h1")?.textContent||"")', 'major page');
  await b.sleep(1500);
  await b.shot('ui-major2');
  const inc = await b.js(`document.querySelector('a[href^="/incidents/"]')?.getAttribute('href')`);
  if (inc) {
    await b.go(inc);
    await b.waitFor('/^INC/.test(document.querySelector("h1")?.textContent||"")', 'incident');
    await b.js(`[...document.querySelectorAll('[role=tab]')].find(t=>t.textContent==='Major incident')?.click()`);
    await b.sleep(800);
    await b.shot('ui-incident-major-tab');
  }
  console.log('done');
} finally { await b.close?.(); }
process.exit(0);
