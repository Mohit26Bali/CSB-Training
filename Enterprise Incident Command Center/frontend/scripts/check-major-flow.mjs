// Checks the major incident screens in a real browser (npm run check:major): declaring from a priority 1 incident,
// the status that moves one step at a time, the timeline, who may do what, and the server's own error messages.
// See scripts/lib/browser.mjs for the settings. Needs the dev server and the whole backend with the demo users.
//
// It creates incidents whose short description starts with "[e2e]" and major incidents on them. Remove them with
// the cleanup SQL in the project notes (or leave them: they are harmless test data).
import { api, apiLogin, launchBrowser } from './lib/browser.mjs';

const tag = 'E2E' + Date.now().toString(36).toUpperCase();
const title = (what) => `[e2e] ${tag} ${what}`;

const employee1 = await apiLogin('ananya@icc.com');
const netEngineer = await apiLogin('sai@icc.com');
const make = async (what, impact, urgency) =>
  (await api('POST', '/api/incidents', { token: employee1, body: { shortDescription: title(what), description: 'Created by the browser check.', category: 'Network', impact, urgency } })).body;
const mainIncident = await make('router down (priority 1)', 1, 1);
const staleIncident = await make('declared by someone else meanwhile', 1, 1);
const lowIncident = await make('printer slow (priority 3)', 2, 2);

const b = await launchBrowser();
const has = async (s) => (await b.text()).includes(s);
const waitText = (s, ms) => b.waitFor(`document.body.innerText.includes(${JSON.stringify(s)})`, 'the text: ' + s, ms);
const openIncident = async (id) => { await b.go(`/incidents/${id}`); await b.waitFor('/^INC/.test(document.querySelector("h1")?.textContent||"")', 'incident ' + id); };
const openMajor = async (id) => { await b.go(`/major-incidents/${id}`); await b.waitFor('/^Major incident #/.test(document.querySelector("h1")?.textContent||"")', 'major incident ' + id); };
const escalateButton = () => b.hasButton('Escalate to major incident');
const order = async (a, c) => { const t = await b.text(); return t.indexOf(a) >= 0 && t.indexOf(c) >= 0 && t.indexOf(a) < t.indexOf(c); };

let majorId;
try {
  // ================================================================== employee: no access
  await b.loginAs('ananya@icc.com');
  b.check('employee: the menu has no "Major incidents" entry', !(await b.menu()).includes('Major incidents'));
  await b.go('/major-incidents');
  b.check('employee: /major-incidents shows "Not available for your role"', await has('Not available for your role'));
  await openIncident(mainIncident.id);
  b.check('employee: the priority 1 incident has no Escalate button', !(await escalateButton()));
  await b.logout();

  // ================================================================== net.engineer declares
  await b.loginAs('sai@icc.com');
  b.check('engineer: the menu offers "Major incidents" as a real page (not "soon")', (await b.js(`[...document.querySelectorAll('[role=navigation] a')].some(a=>a.textContent.trim()==='Major incidents')`)));
  await openIncident(lowIncident.id);
  b.check('a priority 3 incident has no Escalate button', !(await escalateButton()));
  await openIncident(mainIncident.id);
  b.check('a priority 1 incident shows the Escalate button to an engineer', await escalateButton());

  await b.clickButton('Escalate to major incident');
  await b.waitFor('!!document.querySelector("[role=dialog]")', 'declare dialog');
  const prefilled = await b.js(`[...document.querySelectorAll('[role=dialog] input')].some(i=>i.value.includes('router down'))`);
  b.check('declare dialog: the summary is prefilled from the incident, and Declare waits for the impact text', prefilled && !(await b.js(`!![...document.querySelector('[role=dialog]').querySelectorAll('button')].find(e=>e.textContent.trim()==='Promote to major incident' && !e.disabled)`)));
  await b.setField('Business impact', '80 people in building B have no network.');
  await b.selectOption('Commander', 'Sachin Suryavanshi');
  await b.shot('f3-declare-dialog');
  await b.clickDialogButton('Promote to major incident');
  await b.waitFor('/^\\/major-incidents\\/\\d+$/.test(location.pathname)', 'redirect to the major incident');
  majorId = Number((await b.path()).split('/').pop());
  await b.waitFor('/^Major incident #/.test(document.querySelector("h1")?.textContent||"")', 'major incident page');
  await waitText('Major incident declared for ' + mainIncident.number);
  b.check('declared: status Declared, commander is the chosen person, declared by the engineer', (await b.chips()).includes('Declared') && (await has('Sachin Suryavanshi')) && (await has('Sai Vivek')));
  b.check('declared: the page links to the incident by number and title', await has(mainIncident.number + ': ' + title('router down (priority 1)')));
  b.check('declared: the timeline starts with the entry the server wrote', await has('Major incident declared for ' + mainIncident.number));
  await b.shot('f3-declared');

  await openIncident(mainIncident.id);
  b.check('the incident now shows "Major incident #' + majorId + '" and no longer offers Escalate', (await b.chips()).includes('Major incident #' + majorId) && !(await escalateButton()));

  // timeline update and the first status step
  await openMajor(majorId);
  await b.setField('Add an update', 'Spare router on site ' + tag);
  await b.clickButton('Post update');
  await waitText('Spare router on site ' + tag);
  b.check('timeline: a posted update appears above the older entries (newest first)', await order('Spare router on site ' + tag, 'Major incident declared for'));

  b.check('status: only the next step is offered (Mitigating), not Resolved or Closed', (await b.hasButton('Move to Mitigating')) && !(await b.hasButton('Move to Resolved')) && !(await b.hasButton('Move to Closed')));
  await b.clickButton('Move to Mitigating');
  await waitText('Status changed from DECLARED to MITIGATING');
  b.check('moved to Mitigating: the server wrote the status change to the timeline', (await b.chips()).includes('Mitigating') && (await b.hasButton('Move to Resolved')));
  await b.logout();

  // ================================================================== db.engineer (the commander) continues
  await b.loginAs('sachin@icc.com');
  await openMajor(majorId);
  await b.setField('Add an update', 'Standby switch carries the traffic ' + tag);
  await b.clickButton('Post update');
  await waitText('Standby switch carries the traffic ' + tag);
  await b.clickButton('Move to Resolved');
  await waitText('Status changed from MITIGATING to RESOLVED');
  b.check('another engineer (the commander) can post updates and move the status', (await b.chips()).includes('Resolved') && (await has('Sachin Suryavanshi')));
  await b.logout();

  // ================================================================== admin closes it
  await b.loginAs('mohit@icc.com');
  await b.go('/major-incidents');
  await b.waitFor('document.querySelector("h1")?.textContent==="Major incidents"', 'list title');
  await b.waitFor(`[...document.querySelectorAll('tbody tr')].some(r=>r.textContent.includes('router down'))`, 'the major incident in the list');
  b.check('admin: sees the major incident in the list with commander and status', (await b.js(`[...document.querySelectorAll('tbody tr')].map(r=>r.textContent)`)).some((r) => r.includes('router down') && r.includes('Sachin Suryavanshi') && r.includes('Resolved')));
  await openMajor(majorId);
  await b.clickButton('Move to Closed');
  await waitText('This major incident is closed.');
  b.check('Closed: no more steps, and the update form is replaced by a message', (await b.chips()).includes('Closed') && !(await b.hasButton('Post update')) && (await has('A closed major incident takes no more updates.')));
  await b.shot('f3-closed');

  await b.go('/major-incidents');
  await b.selectOption('Status', 'Closed');
  await b.sleep(700);
  const closedRows = await b.js(`[...document.querySelectorAll('tbody tr')].map(r=>r.textContent)`);
  b.check('list: the Closed filter keeps only closed major incidents', closedRows.length >= 1 && closedRows.every((r) => r.includes('Closed')) && closedRows.some((r) => r.includes('router down')), `${closedRows.length} row(s)`);
  await b.selectOption('Status', 'Declared');
  await b.sleep(700);
  b.check('list: the Declared filter does not show it', !(await b.js(`[...document.querySelectorAll('tbody tr')].some(r=>r.textContent.includes('router down'))`)));
  await b.logout();

  // ================================================================== the server's own 409 messages
  await b.loginAs('sai@icc.com');
  await openIncident(staleIncident.id);
  await b.waitFor(`[...document.querySelectorAll('button')].some(e=>e.textContent.trim()==='Escalate to major incident')`, 'Escalate button');
  const taken = await api('POST', '/api/major-incidents', { token: netEngineer, body: { incidentId: staleIncident.id, summary: 'declared elsewhere', impactSummary: 'x' } });
  const again = await api('POST', '/api/major-incidents', { token: netEngineer, body: { incidentId: staleIncident.id, summary: 'declared elsewhere', impactSummary: 'x' } });
  await b.clickButton('Escalate to major incident');
  await b.waitFor('!!document.querySelector("[role=dialog]")', 'declare dialog');
  await b.setField('Business impact', 'second attempt');
  await b.clickDialogButton('Promote to major incident');
  await waitText(again.body.message.slice(0, 40));
  b.check("someone else escalated first: the dialog shows the server's own 409 message", taken.status === 201 && again.status === 409 && (await has(again.body.message)), again.body.message);
  await b.shot('f3-declare-conflict');
  await b.clickDialogButton('Cancel');

  await openMajor(taken.body.id);
  await b.waitFor(`[...document.querySelectorAll('button')].some(e=>e.textContent.trim()==='Move to Mitigating')`, 'move button');
  await api('PATCH', `/api/major-incidents/${taken.body.id}/status`, { token: netEngineer, body: { status: 'MITIGATING' } });
  const refused = await api('PATCH', `/api/major-incidents/${taken.body.id}/status`, { token: netEngineer, body: { status: 'MITIGATING' } });
  await b.clickButton('Move to Mitigating');
  await waitText(refused.body.message.slice(0, 40));
  b.check("stale page, status already moved: the page shows the server's own 409 message", refused.status === 409 && (await has(refused.body.message)), refused.body.message);
  await b.logout();
} catch (e) {
  await b.fail(e);
}
process.exit((await b.finish()) ? 0 : 1);
