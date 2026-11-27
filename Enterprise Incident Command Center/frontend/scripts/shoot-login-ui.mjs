// Screenshot of the login page. Usage: SHOTS=<folder> node scripts/shoot-login-ui.mjs
import { launchBrowser } from './lib/browser.mjs';
const b = await launchBrowser({ width: 1440, height: 860 });
try {
  await b.go('/login');
  await b.waitFor('!!document.querySelector("form")', 'login form');
  await b.sleep(900);
  await b.shot('ui-login-new');
  console.log('done');
} finally { await b.close?.(); }
process.exit(0);
