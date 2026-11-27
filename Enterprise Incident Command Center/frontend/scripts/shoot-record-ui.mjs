// Screenshots of the major incident and problem pages, home and login. Usage: SHOTS=<folder> node scripts/shoot-record-ui.mjs
import { launchBrowser } from './lib/browser.mjs';
const b = await launchBrowser();
try {
  await b.loginAs('mohit@icc.com');
  await b.sleep(500);
  await b.shot('ui-home');
  await b.go('/major-incidents');
  await b.waitFor('document.querySelectorAll("tbody tr a").length>0', 'major rows');
  const major = await b.js(`document.querySelector('tbody tr a').getAttribute('href')`);
  await b.go(major);
  await b.waitFor('/^Major incident #/.test(document.querySelector("h1")?.textContent||"")', 'major page');
  await b.sleep(600);
  await b.shot('ui-major');
  await b.go('/problems');
  await b.waitFor('document.querySelectorAll("tbody tr a").length>0', 'problem rows');
  const problem = await b.js(`document.querySelector('tbody tr a').getAttribute('href')`);
  await b.go(problem);
  await b.waitFor('/^PRB/.test(document.querySelector("h1")?.textContent||"")', 'problem page');
  await b.sleep(600);
  await b.shot('ui-problem');
  await b.logout();
  await b.sleep(400);
  await b.shot('ui-login');
  console.log('done');
} finally { await b.close?.(); }
process.exit(0);
