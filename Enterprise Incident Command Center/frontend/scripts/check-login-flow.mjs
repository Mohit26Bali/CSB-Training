// Checks the login flow in a real browser (npm run check:login). See scripts/lib/browser.mjs for the settings.
// Needs, all running: the dev server (npm run dev), the gateway and the Assignment service, with the demo users.
import { launchBrowser } from './lib/browser.mjs';

const b = await launchBrowser();
const eq = (a, c) => JSON.stringify(a) === JSON.stringify(c);

try {
  // 1. not logged in -> login page
  await b.go('/');
  b.check('not logged in: / redirects to /login', (await b.path()) === '/login', await b.path());
  await b.shot('f1-login');

  // 2. wrong password
  await b.login('ananya@icc.com', 'wrong-password');
  await b.waitFor('document.body.innerText.includes("Invalid username or password")', 'error message');
  b.check('wrong password: stays on /login and shows the backend message', (await b.path()) === '/login');
  await b.shot('f1-login-error');

  // 3. each role: login, home, menu, role guard, refresh, logout.  "soon" entries are screens of later phases.
  const roles = [
    { user: 'ananya@icc.com', name: 'Ananya Iyer', role: 'Employee', team: 'No team', menu: ['Home', 'My incidents', 'Report an incident'], forbidden: '/admin/rules' },
    { user: 'sai@icc.com', name: 'Sai Vivek', role: 'Support engineer', team: 'Network', menu: ['Home', 'Incident queue', 'Major incidents', 'Problems'], forbidden: '/dashboard' },
    { user: 'mohit@icc.com', name: 'Mohit Bali', role: 'Administrator', team: 'No team', menu: ['Home', 'Dashboard', 'All incidents', 'Major incidents', 'Problems and RCA', 'Assignment rules', 'Audit trails'], forbidden: null },
  ];
  for (const r of roles) {
    await b.go('/login');
    await b.login(r.user);
    await b.waitFor('location.pathname==="/" && document.body.innerText.includes("Welcome")', 'home after login');
    await b.waitFor('document.querySelectorAll("[role=navigation] a").length>0', 'menu');
    await b.waitFor(`[...document.querySelectorAll('.MuiChip-root')].map(c=>c.textContent).includes(${JSON.stringify(r.team)})`, 'the team chip (names loaded from the backend)');
    const text = await b.text();
    const chips = await b.chips();
    b.check(`${r.user}: Home shows name, role chip and the team chip loaded from /api/teams`, text.includes('Welcome, ' + r.name) && chips.includes(r.role) && chips.includes(r.team), `${r.name} / chips: ${chips.join(', ')}`);
    const m = await b.menu();
    b.check(`${r.user}: menu is exactly the ${r.role} menu`, eq(m, r.menu), m.join(' | '));
    await b.shot('f1-home-' + r.user.replace('.', '-'));
    if (r.forbidden) {
      await b.go(r.forbidden);
      b.check(`${r.user}: ${r.forbidden} shows "Not available for your role"`, (await b.text()).includes('Not available for your role'));
    }
    await b.go('/');
    b.check(`${r.user}: still logged in after a page refresh`, (await b.path()) === '/' && (await b.text()).includes('Welcome, ' + r.name));
    await b.logout();
    b.check(`${r.user}: Log out returns to /login and clears the stored login`, (await b.path()) === '/login' && (await b.js('window.sessionStorage.getItem("icc.auth")')) === null);
  }

  // 4. deep link while logged out, then login returns to it
  await b.go('/major-incidents');
  b.check('deep link while logged out goes to /login', (await b.path()) === '/login');
  await b.login('sai@icc.com');
  await b.waitFor('location.pathname==="/major-incidents"', 'return to the wanted page');
  {
    await b.waitFor('document.querySelector("h1")?.textContent==="Major incidents"', 'the wanted page');
    const where = await b.path();
    b.check('after login the user returns to the page they wanted', where === '/major-incidents' && (await b.text()).includes('Major incidents'), `at ${where}`);
  }

  // 5. a damaged token: the first 401 logs out with a message
  await b.js(`(() => { const a=JSON.parse(sessionStorage.getItem('icc.auth')); a.token='damaged.token.value'; sessionStorage.setItem('icc.auth', JSON.stringify(a)); })()`);
  await b.go('/');
  await b.waitFor('location.pathname==="/login" && document.body.innerText.includes("session expired")', 'session expired notice', 12000);
  b.check('invalid token: the first 401 logs out and shows "Your session expired"', (await b.path()) === '/login' && (await b.text()).includes('Your session expired'));
  b.check('and the stored login is cleared', (await b.js('window.sessionStorage.getItem("icc.auth")')) === null);
  await b.shot('f1-session-expired');

  // 6. phone width: the menu hides behind a Menu button
  await b.viewport(390, 800, true);
  await b.go('/login');
  await b.login('mohit@icc.com');
  await b.waitFor('location.pathname==="/" && document.body.innerText.includes("Welcome")', 'home on phone');
  const visibleLinks = '[...document.querySelectorAll("[role=navigation] a")].filter(a=>{const r=a.getBoundingClientRect();return r.width>0 && r.left>=0 && r.right<=window.innerWidth}).length';
  b.check('phone width: the menu is hidden until opened', (await b.js(visibleLinks)) === 0 && (await b.hasButton('Menu')));
  await b.clickButton('Menu');
  await b.waitFor(visibleLinks + '===7', 'menu opens with 7 admin links');
  b.check('phone width: the Menu button opens the administrator menu (7 entries)', (await b.js(visibleLinks)) === 7);
  await b.shot('f1-phone-menu');
} catch (e) {
  await b.fail(e);
}
process.exit((await b.finish()) ? 0 : 1);
