// Checks the problem screens in a real browser (npm run check:problems): creating a problem (also from a major
// incident), the six steps and their buttons (Assess, Confirm, Fix, Resolve, Complete), the exits (Accept risk,
// Mark duplicate, Cancel), Re-analyze, the corrective actions that close with the problem, the incident that wakes up
// when its problem closes, who may do what, and the server's own error messages (D31, D32).
// See scripts/lib/browser.mjs for the settings. Needs the dev server and the whole backend with the demo users.
//
// It creates incidents whose short description starts with "[e2e]", with major incidents and problems on them.
import { api, apiLogin, launchBrowser } from './lib/browser.mjs';

const tag = 'E2E' + Date.now().toString(36).toUpperCase();
const title = (what) => `[e2e] ${tag} ${what}`;

const employee1 = await apiLogin('ananya@icc.com');
const netEngineer = await apiLogin('sai@icc.com');
const admin = await apiLogin('mohit@icc.com');
const makeIncident = async (what, impact, urgency) =>
  (await api('POST', '/api/incidents', { token: employee1, body: { shortDescription: title(what), description: 'Created by the browser check.', category: 'Network', impact, urgency } })).body;
/** A problem made through the API, moved to Assess (the assignee is the network engineer, user 3). */
const makeAssessed = async (what, incidentId) => {
  const p = (await api('POST', '/api/problems', { token: admin, body: { title: title(what), description: 'Made by the browser check.', incidentIds: [incidentId] } })).body;
  await api('POST', `/api/problems/${p.id}/assess`, { token: admin, body: { assignedToId: 3 } });
  return p;
};

const outage = await makeIncident('router down (priority 1)', 1, 1);
const major = (await api('POST', '/api/major-incidents', { token: netEngineer, body: { incidentId: outage.id, summary: title('outage'), impactSummary: 'Building B offline' } })).body;
// An incident that is waiting for a problem: accepted, then put on hold with the reason "Awaiting problem".
const held = await makeIncident('waiting for the problem', 3, 3);
await api('POST', `/api/incidents/${held.id}/accept`, { token: netEngineer });
await api('POST', `/api/incidents/${held.id}/hold`, { token: netEngineer, body: { reason: 'AWAITING_PROBLEM' } });
const spare = await makeIncident('spare incident for api-made problems', 3, 3);
const dup = await makeAssessed('duplicate problem', spare.id);
const toCancel = await makeAssessed('problem to cancel', spare.id);
const stale = await makeAssessed('stale problem', spare.id);
const toAccept = await makeAssessed('problem to accept', spare.id);
await api('POST', `/api/problems/${toAccept.id}/confirm`, { token: admin });

const b = await launchBrowser();
const has = async (s) => (await b.text()).includes(s);
const waitText = (s, ms) => b.waitFor(`document.body.innerText.includes(${JSON.stringify(s)})`, 'the text: ' + s, ms);
const waitChip = (s) => b.waitFor(`[...document.querySelectorAll('.MuiChip-root')].some(c=>c.textContent===${JSON.stringify(s)})`, 'the chip ' + s);
const tab = (name) => b.js(`(() => { const t=[...document.querySelectorAll('[role=tab]')].find(x=>x.textContent.startsWith(${JSON.stringify(name)})); if(!t) return false; t.click(); return true; })()`);
const openProblem = async (id) => { await b.go(`/problems/${id}`); await b.waitFor('/^PRB/.test(document.querySelector("h1")?.textContent||"")', 'problem ' + id); };
const enabled = (text) => b.js(`!![...document.querySelectorAll('button')].find(e=>e.textContent.trim()===${JSON.stringify(text)} && !e.disabled)`);
const dialogEnabled = (text) => b.js(`!![...(document.querySelector('[role=dialog]')?.querySelectorAll('button')||[])].find(e=>e.textContent.trim()===${JSON.stringify(text)} && !e.disabled)`);
/** Clicks a dialog button and waits until the dialog has really gone (it fades out; the next one must not be mixed up with it). */
const dialogDone = async (text) => { await b.clickDialogButton(text); await b.waitFor('!document.querySelector("[role=dialog]")', 'the dialog to close'); };
const dialogOpen = () => b.waitFor('!!document.querySelector("[role=dialog]")', 'a dialog');

let problemA, problemB;
try {
  // ================================================================== employee: no access
  await b.loginAs('ananya@icc.com');
  b.check('employee: the menu has no "Problems" entry, and /problems is not available', !(await b.menu()).includes('Problems') && (await (async () => { await b.go('/problems'); return has('Not available for your role'); })()));
  await b.logout();

  // ================================================================== engineer creates problems
  await b.loginAs('sai@icc.com');
  await b.go('/problems');
  await b.waitFor('document.querySelector("h1")?.textContent==="Problems"', 'list title');
  b.check('engineer: "Problems" is a real page with a New problem button', await b.js(`[...document.querySelectorAll('a,button')].some(e=>e.textContent.trim()==='New problem')`));

  await b.go('/problems/new');
  await b.waitFor('document.querySelector("h1")?.textContent==="New problem"', 'new problem form');
  await b.clickButton('Create problem');
  await waitText('Enter a title.');
  b.check('new problem: an empty form shows all three errors and sends nothing', (await has('Describe the problem.')) && (await has('Choose at least one incident.')) && (await b.path()) === '/problems/new');

  // from the major incident page the form starts with the incident and the major incident chosen
  await b.go(`/major-incidents/${major.id}`);
  await b.waitFor('/^Major incident #/.test(document.querySelector("h1")?.textContent||"")', 'major incident page');
  await b.clickButton('Create a problem for this major incident');
  await b.waitFor('location.pathname==="/problems/new"', 'new problem form from the major incident');
  await b.waitFor(`document.body.innerText.includes(${JSON.stringify(outage.number)})`, 'the incident is chosen');
  await b.waitFor(`[...document.querySelectorAll('input')].some(i=>i.value.startsWith('#${major.id}: '))`, 'the major incident is chosen');
  b.check('from the major incident page: the form starts with its incident and the major incident chosen', (await has(outage.number)) && (await b.js(`[...document.querySelectorAll('input')].some(i=>i.value.startsWith('#${major.id}: '))`)));
  await b.setField('Title', title('router failure'));
  await b.setField('Description', 'The core router in building B failed.');
  await b.selectOption('Impact', '1 - High');
  await b.selectOption('Urgency', '1 - High');
  await b.shot('f4-new-problem');
  await b.clickButton('Create problem');
  await b.waitFor('/^\\/problems\\/\\d+$/.test(location.pathname)', 'redirect to the problem');
  problemA = Number((await b.path()).split('/').pop());
  await b.waitFor('/^PRB/.test(document.querySelector("h1")?.textContent||"")', 'problem page');
  await waitChip('P1 Critical');
  const created = await api('GET', `/api/problems/${problemA}`, { token: admin });
  b.check('created: New and P1 Critical, with the notice; the server stored the incident, the major incident and the priority', (await has('The problem was created.')) && (await b.chips()).includes('New') && created.body.incidentIds.includes(outage.id) && created.body.problem.majorIncidentId === major.id && created.body.problem.priority === 1);
  await b.waitFor(`document.body.innerText.includes(${JSON.stringify(outage.number)})`, 'linked incident shown');
  b.check('the page shows the linked incident (number and title) and the major incident with its status', (await has(title('router down (priority 1)'))) && (await b.chips()).includes('Declared'));
  b.check('an engineer sees the explanation instead of the step buttons (no Assess, no Edit)', (await has('Only an administrator moves a problem')) && !(await b.hasButton('Assess')) && !(await b.hasButton('Edit')) && !(await b.hasButton('Add action')));

  // the searchable picker, with an assignee: the problem starts at Assess
  await b.go('/problems/new');
  await b.waitFor('document.querySelector("h1")?.textContent==="New problem"', 'new problem form');
  await b.setField('Title', title('held incident problem'));
  await b.setField('Description', 'On an incident that waits for this problem.');
  await b.pickOption('Incidents', tag + ' waiting for the problem', held.number);
  b.check('the incident picker searches the server and the chosen incident appears as a chip', (await b.chips()).some((c) => c.includes(held.number)));
  await b.selectOption('Assigned to (optional)', 'Sai Vivek');
  await b.clickButton('Create problem');
  await b.waitFor('/^\\/problems\\/\\d+$/.test(location.pathname)', 'redirect to the second problem');
  problemB = Number((await b.path()).split('/').pop());
  await waitChip('Assess');
  b.check('created with an assignee: the problem starts at Assess, like ServiceNow', (await b.chips()).includes('Assess'));
  await b.logout();

  // ================================================================== admin: problem A through the six steps
  await b.loginAs('mohit@icc.com');
  await openProblem(problemA);
  await waitChip('New');
  b.check('New: only Assess (and Edit) are offered, no other step', (await b.hasButton('Assess')) && (await b.hasButton('Edit')) && !(await b.hasButton('Confirm')) && !(await b.hasButton('Fix')) && !(await b.hasButton('Cancel')));

  await b.clickButton('Edit');
  await dialogOpen();
  await b.selectOption('Urgency', '3 - Low');
  await dialogDone('Save');
  await waitText('was updated.');
  await waitChip('P3 Moderate');
  b.check('Edit: impact 1 and urgency 3 give P3 Moderate (the priority is calculated)', (await b.chips()).includes('P3 Moderate'));

  await b.clickButton('Assess');
  await dialogOpen();
  b.check('Assess needs an assignee: the dialog button is disabled until one is chosen', !(await dialogEnabled('Assess')));
  await b.selectOption('Assigned to', 'Sai Vivek');
  await dialogDone('Assess');
  await waitText('is being assessed.');
  await waitChip('Assess');
  b.check('Assess: the state moves and the assignee is shown; now Confirm, Mark duplicate and Cancel are offered (not Fix)', (await has('Sai Vivek')) && (await b.hasButton('Confirm')) && (await b.hasButton('Mark duplicate')) && (await b.hasButton('Cancel')) && !(await b.hasButton('Fix')) && !(await b.hasButton('Accept risk')));

  await b.clickButton('Confirm');
  await waitText('The problem is now in root cause analysis.');
  await waitChip('Root cause analysis');
  b.check('Confirm: root cause analysis; Fix, Accept risk, Mark duplicate and Cancel are offered', (await b.hasButton('Fix')) && (await b.hasButton('Accept risk')) && (await b.hasButton('Mark duplicate')) && (await b.hasButton('Cancel')));
  await b.shot('f4-rca-step');

  // Fix: both notes are required
  await b.clickButton('Fix');
  await dialogOpen();
  b.check('Fix is disabled until both the cause notes and the fix notes are written', !(await dialogEnabled('Fix')));
  await b.setField('Cause notes', 'Line card failure in the core router ' + tag);
  b.check('Fix is still disabled with only the cause notes', !(await dialogEnabled('Fix')));
  await b.setField('Fix notes', 'Replace the line card ' + tag);
  await dialogDone('Fix');
  await waitText('is in progress.');
  await waitChip('Fix in progress');
  b.check('Fix: Fix in progress; Resolve, Accept risk and Re-analyze are offered; Cancel and Mark duplicate are gone', (await b.hasButton('Resolve')) && (await b.hasButton('Accept risk')) && (await b.hasButton('Re-analyze')) && !(await b.hasButton('Cancel')) && !(await b.hasButton('Mark duplicate')));

  await tab('Corrective actions');
  await b.setField('What must be done', 'Replace all line cards of this batch');
  await b.selectOption('Owner', 'Sai Vivek');
  await b.setField('Due date', '2020-01-01');
  b.check('a due date in the past keeps "Add action" disabled', !(await enabled('Add action')));
  await b.setField('Due date', '2030-01-31');
  await b.clickButton('Add action');
  await waitText('The corrective action was added.');
  b.check('Add action: the action is listed as Open, and the page says open actions do not block', (await has('Replace all line cards of this batch')) && (await b.chips()).includes('Open') && (await has('Open actions do not block the problem')));

  await tab('Analysis');
  b.check('Analysis tab shows the cause notes and the fix notes', (await has('Line card failure in the core router ' + tag)) && (await has('Replace the line card ' + tag)));
  await b.clickButton('Resolve');
  await waitText('The problem was resolved.');
  await waitChip('Resolved');
  b.check('Resolve with an open action: allowed. Resolved, resolution code Fix applied, Complete offered', (await b.chips()).includes('Fix applied') && (await b.hasButton('Complete')) && (await b.hasButton('Re-analyze')));
  await b.shot('f4-resolved');

  // Re-analyze: back to root cause analysis, ending undone, notes kept
  await b.clickButton('Re-analyze');
  await dialogOpen();
  await dialogDone('Re-analyze');
  await waitText('is back in root cause analysis.');
  await waitChip('Root cause analysis');
  b.check('Re-analyze: back in root cause analysis, resolution code gone, "Reopened 1x" shown, notes kept', !(await b.chips()).includes('Fix applied') && (await b.chips()).includes('Reopened 1x') && (await has('Replace the line card ' + tag)));

  await b.clickButton('Fix');
  await dialogOpen();
  b.check('Fix dialog is prefilled with the notes that were kept (enabled at once)', await dialogEnabled('Fix'));
  await dialogDone('Fix');
  await waitChip('Fix in progress');
  await b.clickButton('Resolve');
  await waitChip('Resolved');
  await b.clickButton('Complete');
  await waitText('The problem was closed.');
  await waitChip('Closed');
  b.check('Complete: Closed; only Re-analyze (and no Edit or Add action) remains', (await b.hasButton('Re-analyze')) && !(await b.hasButton('Edit')) && !(await b.hasButton('Complete')));
  await tab('Corrective actions');
  b.check('closing the problem marked its open action Done (no blocking, ServiceNow cascade)', (await b.chips()).includes('Done') && !(await b.chips()).includes('Open') && !(await b.hasButton('Add action')));
  await b.shot('f4-closed');

  // ================================================================== problem B: an incident on hold wakes up
  const heldBefore = await api('GET', `/api/incidents/${held.id}`, { token: admin });
  await openProblem(problemB);
  await b.clickButton('Confirm');
  await waitChip('Root cause analysis');
  await b.clickButton('Fix');
  await dialogOpen();
  await b.setField('Cause notes', 'The router ran out of memory ' + tag);
  await b.setField('Fix notes', 'Memory upgraded ' + tag);
  await dialogDone('Fix');
  await waitChip('Fix in progress');
  await b.clickButton('Resolve');
  await waitChip('Resolved');
  await b.clickButton('Complete');
  await waitChip('Closed');
  const heldAfter = await api('GET', `/api/incidents/${held.id}`, { token: admin });
  b.check('the incident that was On hold (Awaiting problem) is In progress again, and carries the fix notes as a work note',
    heldBefore.body.incident.state === 'ON_HOLD' && heldAfter.body.incident.state === 'IN_PROGRESS' && heldAfter.body.incident.holdReason === null &&
      heldAfter.body.comments.some((c) => c.type === 'WORK_NOTE' && c.body.includes('Memory upgraded ' + tag)));

  // ================================================================== the exits
  await openProblem(dup.id);
  await b.clickButton('Mark duplicate');
  await dialogOpen();
  b.check('Mark duplicate needs the original: disabled until one is chosen', !(await dialogEnabled('Mark duplicate')));
  const original = (await api('GET', `/api/problems/${problemA}`, { token: admin })).body.problem.number;
  await b.selectOption('Duplicate of', original);
  await dialogDone('Mark duplicate');
  await waitText('was closed as a duplicate.');
  await waitChip('Duplicate');
  const afterDup = await api('GET', `/api/problems/${dup.id}`, { token: admin });
  b.check('Mark duplicate: Closed with the code Duplicate and a link to the original', (await b.chips()).includes('Closed') && (await has('Duplicate of')) && afterDup.body.problem.duplicateOfId === problemA);
  const originalAfter = await api('GET', `/api/problems/${problemA}`, { token: admin });
  b.check('the incidents of the duplicate were linked to the original problem', originalAfter.body.incidentIds.includes(spare.id));

  await openProblem(toCancel.id);
  await b.clickButton('Cancel');
  await dialogOpen();
  await dialogDone('Cancel the problem');
  await waitText('was canceled.');
  await waitChip('Canceled');
  b.check('Cancel: Closed with the code Canceled, after a confirmation', (await b.chips()).includes('Closed') && (await b.hasButton('Re-analyze')));

  await openProblem(toAccept.id);
  await waitChip('Root cause analysis');
  await b.clickButton('Accept risk');
  await dialogOpen();
  b.check('Accept risk without a workaround: enabled (nothing was applied)', await dialogEnabled('Accept risk'));
  await b.js(`document.querySelector('[role=dialog] input[type=checkbox]').click()`);
  await b.sleep(250);
  b.check('ticking "A workaround has been applied" makes the workaround required: disabled', !(await dialogEnabled('Accept risk')));
  await b.setField('Workaround', 'Restart the gateway every Monday ' + tag);
  await dialogDone('Accept risk');
  await waitText('the risk is accepted.');
  await waitChip('Risk accepted');
  b.check('Accept risk: Closed straight from root cause analysis, with the code Risk accepted and the workaround shown', (await b.chips()).includes('Closed') && (await has('Restart the gateway every Monday ' + tag)));

  // the server's own message when the page is out of date
  await openProblem(stale.id);
  await b.waitFor(`[...document.querySelectorAll('button')].some(e=>e.textContent.trim()==='Confirm')`, 'Confirm button');
  await api('POST', `/api/problems/${stale.id}/cancel`, { token: admin });
  const refused = await api('POST', `/api/problems/${stale.id}/confirm`, { token: admin });
  await b.clickButton('Confirm');
  await waitText(refused.body.message.slice(0, 40));
  b.check("stale page, problem already canceled elsewhere: the page shows the server's own 409 message", refused.status === 409 && (await has(refused.body.message)), refused.body.message);
  await b.shot('f4-conflict');

  // ================================================================== the list
  await b.go('/problems');
  await b.waitFor('document.querySelector("h1")?.textContent==="Problems and RCA"', 'admin list title');
  await b.waitFor(`[...document.querySelectorAll('tbody tr')].some(r=>r.textContent.includes('router failure'))`, 'problem in the list');
  const row = await b.js(`[...document.querySelectorAll('tbody tr')].map(r=>r.textContent).find(r=>r.includes('router failure'))`);
  b.check('list: the closed problem shows number, status, priority, assignee and major incident', /PRB/.test(row) && row.includes('Closed') && row.includes('P3') && row.includes('Sai Vivek') && row.includes('#' + major.id), row.slice(0, 110));
  await b.selectOption('Status', 'Closed');
  await b.sleep(700);
  b.check('list: the Closed filter keeps the closed problem', await b.js(`[...document.querySelectorAll('tbody tr')].some(r=>r.textContent.includes('router failure'))`));
  await b.selectOption('Status', 'Assess');
  await b.sleep(700);
  b.check('list: the Assess filter hides it', !(await b.js(`[...document.querySelectorAll('tbody tr')].some(r=>r.textContent.includes('router failure'))`)));
  await b.logout();
} catch (e) {
  await b.fail(e);
}
process.exit((await b.finish()) ? 0 : 1);
