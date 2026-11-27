// Checks the admin screens in a real browser (npm run check:admin): the dashboard with its numbers and trend,
// the assignment rules (a change really re-routes new incidents) and the audit trails. See scripts/lib/browser.mjs
// for the settings. Needs the dev server and the whole backend with the demo users.
//
// It creates two incidents whose short description starts with "[e2e]" and changes the rule of the category "Other"
// (and puts it back at the end, also when a check fails).
//
// To check "a service is down", stop the RCA service and run:  EXPECT_RCA_DOWN=1 npm run check:admin
import { api, apiLogin, launchBrowser } from './lib/browser.mjs';

const tag = 'E2E' + Date.now().toString(36).toUpperCase();
const rcaDown = process.env.EXPECT_RCA_DOWN === '1';

const admin = await apiLogin('mohit@icc.com');
const employee1 = await apiLogin('ananya@icc.com');
const summaryBefore = (await api('GET', '/api/dashboard/summary', { token: admin })).body;

const rulesBefore = (await api('GET', '/api/assignment-rules', { token: admin })).body;
const teams = (await api('GET', '/api/teams', { token: admin })).body;
const other = rulesBefore.find((r) => r.category === 'Other');
const originalTeam = other.teamId;
const newTeam = teams.find((t) => t.id !== originalTeam);
const make = async (what, category, impact, urgency) =>
  (await api('POST', '/api/incidents', { token: employee1, body: { shortDescription: `[e2e] ${tag} ${what}`, description: 'Created by the browser check.', category, impact, urgency } })).body;

const b = await launchBrowser();
const text = () => b.text();
const has = async (s) => (await text()).includes(s);
const waitText = (s, ms) => b.waitFor(`document.body.innerText.includes(${JSON.stringify(s)})`, 'the text: ' + s, ms);
const figure = (card, label) =>
  b.js(`(() => { const c=[...document.querySelectorAll('.MuiPaper-root')].find(p=>p.querySelector('h2')?.textContent==='${card}'); if(!c) return null; const cap=[...c.querySelectorAll('.MuiTypography-caption')].find(x=>x.textContent==='${label}'); return cap ? Number(cap.previousElementSibling.textContent) : null; })()`);

try {
  // ================================================================== access
  await b.loginAs('sai@icc.com');
  await b.go('/admin/audit');
  await waitText('Not available for your role');
  b.check('an engineer is kept out of the audit trails', true);
  await b.go('/dashboard');
  await waitText('Not available for your role');
  b.check('an engineer is kept out of the dashboard', true);
  await b.logout();

  // ================================================================== dashboard
  await b.loginAs('mohit@icc.com');
  b.check('the menu entries Dashboard, Assignment rules and Audit trails are real now (not "soon")', !(await b.js(`[...document.querySelectorAll('[role=navigation] a')].some(a=>/soon$/.test(a.textContent))`)));
  await b.go('/dashboard');
  await b.waitFor('document.querySelector("h1")?.textContent==="Dashboard"', 'dashboard title');
  await b.waitFor('[...document.querySelectorAll("h2")].some(h=>h.textContent==="Incidents")', 'the incidents card');
  await b.waitFor('!!document.querySelector("[role=img]")', 'the trend chart');
  b.check('three cards and the trend chart are shown', (await has('Major incidents')) && (await has('Problems and RCA')) && (await b.js(`!!document.querySelector('[role=img]')`)));

  if (!rcaDown) {
    await b.waitFor('document.querySelector("[data-testid=updated]")?.textContent.includes("Updated")', 'the update time');
    const openShown = await figure('Incidents', 'Open');
    const criticalShown = await figure('Incidents', 'Critical and open');
    b.check('the incident figures on the page equal the server numbers', openShown === summaryBefore.incidents.open && criticalShown === summaryBefore.incidents.criticalOpen, `page ${openShown}/${criticalShown} server ${summaryBefore.incidents.open}/${summaryBefore.incidents.criticalOpen}`);
    b.check('no card says "Unavailable" while all services are up', !(await has('Unavailable:')));

    await make('critical for the dashboard', 'Network', 1, 1);
    await b.clickButton('Refresh');
    await b.waitFor(`(() => { const c=[...document.querySelectorAll('.MuiPaper-root')].find(p=>p.querySelector('h2')?.textContent==='Incidents'); const cap=[...c.querySelectorAll('.MuiTypography-caption')].find(x=>x.textContent==='Critical and open'); return cap && Number(cap.previousElementSibling.textContent)===${summaryBefore.incidents.criticalOpen + 1}; })()`, 'critical +1 after Refresh');
    b.check('a new critical incident raises "Critical and open" by one after Refresh', true);

    const label7 = await b.js(`document.querySelector('[role=img]').getAttribute('aria-label')`);
    await b.clickButton('30 days');
    await b.waitFor(`document.querySelectorAll('[role=img] > div').length===30`, 'the 30 day chart');
    const bars30 = await b.js(`document.querySelectorAll('[role=img] > div').length`);
    await b.clickButton('7 days');
    await b.waitFor(`document.querySelector('[role=img]')?.getAttribute('aria-label').includes('7 days')`, 'the 7 day chart');
    const bars7 = await b.js(`document.querySelectorAll('[role=img] > div').length`);
    b.check('the trend switches between 7 and 30 days (7 and 30 columns)', label7.includes('7 days') && bars7 === 7 && bars30 === 30, `${bars7} / ${bars30}`);
  } else {
    await b.waitFor('!!document.querySelector("[data-testid=unavailable-problems]")', 'the unavailable card for problems', 15000);
    b.check('RCA service down: the problems card says Unavailable', true);
    b.check('RCA service down: the incident and major incident cards are still filled', (await figure('Incidents', 'Open')) !== null && (await figure('Major incidents', 'Active')) !== null && !(await b.js(`!!document.querySelector('[data-testid=unavailable-incidents]')`)));
    b.check('RCA service down: a warning names the missing part', await has('Some figures are missing'));
  }

  // ================================================================== rules
  if (!rcaDown) {
    await b.go('/admin/rules');
    await b.waitFor('document.querySelector("h1")?.textContent==="Assignment rules"', 'rules title');
    await b.waitFor(`document.querySelectorAll('tbody tr').length>=6`, 'six rules');
    const categories = await b.js(`[...document.querySelectorAll('tbody th')].map(x=>x.textContent)`);
    b.check('one rule per category is listed (six)', ['Hardware', 'Software', 'Network', 'Database', 'Access', 'Other'].every((c) => categories.includes(c)), categories.join(', '));
    b.check('Save is off until a team is changed', !(await b.js(`!![...document.querySelectorAll('button')].find(x=>x.getAttribute('aria-label')==='Save Other' && !x.disabled)`)));

    await b.waitFor(`document.querySelector('[aria-label="Team for Other"]')?.getAttribute('aria-disabled')!=='true'`, 'the team picker to be enabled');
    const pick = await b.js(`(() => { const c=document.querySelector('[aria-label="Team for Other"]'); if(!c) return false; c.dispatchEvent(new MouseEvent('mousedown',{bubbles:true})); return true; })()`);
    await b.waitFor(`!!document.querySelector('[role=listbox]')`, 'the team list');
    await b.js(`(() => { const o=[...document.querySelectorAll('[role=listbox] [role=option]')].find(x=>x.textContent.trim()===${JSON.stringify(newTeam.name)}); o.click(); })()`);
    b.check('choosing another team turns Save on', pick && (await b.js(`!![...document.querySelectorAll('button')].find(x=>x.getAttribute('aria-label')==='Save Other' && !x.disabled)`)));
    await b.js(`[...document.querySelectorAll('button')].find(x=>x.getAttribute('aria-label')==='Save Other').click()`);
    await waitText('Other incidents now go to the');
    const stored = (await api('GET', '/api/assignment-rules', { token: admin })).body.find((r) => r.category === 'Other');
    b.check('Save: the server stored the new team and the page confirms it', stored.teamId === newTeam.id && (await has(newTeam.name)));
    const routed = await make('routed by the new rule', 'Other', 3, 3);
    b.check('a new incident in the category Other is routed to the new team', routed.assignedTeamId === newTeam.id, `team ${routed.assignedTeamId}`);
  }

  // ================================================================== audit
  await b.go('/admin/audit');
  await b.waitFor('document.querySelector("h1")?.textContent==="Audit trails"', 'audit title');
  await b.waitFor(`document.querySelectorAll('tbody tr').length>0`, 'audit rows');
  const tabs = await b.js(`[...document.querySelectorAll('[role=tab]')].map(t=>t.textContent)`);
  b.check('four tabs, one per service', tabs.length === 4, tabs.join(', '));
  b.check('the incident trail shows events with the author name and the record', (await has('Ananya Iyer')) && (await has('INCIDENT #')));
  b.check('paging text reads "1 to N of M"', /1 to \d+ of \d+/.test(await text()));
  if (!rcaDown) {
    await b.js(`[...document.querySelectorAll('[role=tab]')].find(t=>t.textContent==='Users and rules').click()`);
    await waitText('Mohit Bali');
    b.check('the "Users and rules" trail shows the rule change by the admin', (await has('Mohit Bali')) && (await has('Other')));
  }
  await b.js(`[...document.querySelectorAll('[role=tab]')].find(t=>t.textContent==='Problems').click()`);
  await b.waitFor(`!!document.querySelector('table')`, 'the problems trail');
  b.check('the problems tab opens without an error', !(await has('Something went wrong')));
} finally {
  // Put the rule back, whatever happened.
  await api('PUT', `/api/assignment-rules/${other.id}`, { token: admin, body: { teamId: originalTeam } });
}
await b.finish();
