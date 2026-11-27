// Checks the whole incident lifecycle in a real browser (npm run check:incidents): report, queue, accept, work notes,
// edit, resolve, close, reassign, who sees what, and the error messages of the server. See scripts/lib/browser.mjs.
//
// Needs, all running: the dev server (npm run dev), and the whole backend with the demo users.
// It creates a few incidents whose short description starts with "[e2e]". Delete them afterwards if you like:
//   DELETE FROM incident_comment WHERE incident_id IN (SELECT id FROM incident WHERE short_description LIKE '[e2e]%');
//   DELETE FROM incident WHERE short_description LIKE '[e2e]%';      (in the schema icc_incident)
import { api, apiLogin, launchBrowser } from './lib/browser.mjs';

const tag = 'E2E' + Date.now().toString(36).toUpperCase();
const title = (what) => `[e2e] ${tag} ${what}`;
const NETWORK = { category: 'Network', impact: 1, urgency: 1 };

// ---- data prepared through the API (no browser): somebody else's incident, one for the 409 test, one for reassign
const employee1 = await apiLogin('ananya@icc.com');
const employee2 = await apiLogin('rohan@icc.com');
const netEngineer = await apiLogin('sai@icc.com');
const make = async (token, what) => (await api('POST', '/api/incidents', { token, body: { shortDescription: title(what), description: 'Created by the browser check.', ...NETWORK } })).body.id;
const othersId = await make(employee2, 'belongs to employee2');
const staleId = await make(employee1, 'accepted by someone else meanwhile');
const reassignId = await make(employee1, 'to be reassigned');
const cancelId = await make(employee1, 'to be cancelled');
// Two resolved incidents for the Reopen steps (D34, D39: only a Resolved incident can be reopened): one is reopened by the engineer, one by the reporter.
const resolvedThroughApi = async (what) => {
  const incidentId = await make(employee1, what);
  await api('POST', `/api/incidents/${incidentId}/accept`, { token: netEngineer });
  await api('POST', `/api/incidents/${incidentId}/resolve`, { token: netEngineer, body: { resolutionCode: 'Solution provided', resolutionNotes: 'done' } });
  return incidentId;
};
const reopenId = await resolvedThroughApi('resolved, to be reopened by the engineer');
const reopenByReporterId = await resolvedThroughApi('resolved, to be reopened by the reporter');

const b = await launchBrowser();
const text = () => b.text();
const has = async (s) => (await text()).includes(s);
const fact = async (label, value) => new RegExp(`${label}\\s+${value}`).test(await text());
const search = async (s) => { await b.setField('Search number or text', s); await b.sleep(700); };
const rows = () => b.js(`[...document.querySelectorAll('tbody tr')].map(r=>r.textContent)`);
const waitRow = (s) => b.waitFor(`[...document.querySelectorAll('tbody tr')].some(r=>r.textContent.includes(${JSON.stringify(s)}))`, 'a list row with ' + s);
const waitText = (s, ms) => b.waitFor(`document.body.innerText.includes(${JSON.stringify(s)})`, 'the text: ' + s, ms);
const openDetail = async (id) => { await b.go(`/incidents/${id}`); await b.waitFor('/^INC/.test(document.querySelector("h1")?.textContent||"")', 'incident detail ' + id); };

let id;
try {
  // ================================================================== employee1
  await b.loginAs('ananya@icc.com');
  await b.go('/incidents');
  await b.waitFor('document.querySelector("h1")?.textContent==="My incidents"', 'list title');
  b.check('employee: the list is titled "My incidents" and has no scope switch', !(await b.hasButton('Reported by me')) && (await b.js(`[...document.querySelectorAll('a,button')].some(e=>e.textContent.trim()==='Report an incident')`)));

  await b.go('/incidents/new');
  await b.waitFor('document.querySelector("h1")?.textContent==="Report an incident"', 'report form');
  await b.clickButton('Report incident');
  await waitText('Enter a short description.');
  b.check('report form: empty submit shows the field errors and sends nothing', (await has('Describe the problem.')) && (await has('Choose a category.')) && (await b.path()) === '/incidents/new');

  await b.setField('Short description', title('core router down'));
  await b.setField('Description', 'Building B has no network.');
  await b.selectOption('Category', 'Network');
  b.check('report form: each error disappears as soon as its field is filled in', !(await has('Enter a short description.')) && !(await has('Describe the problem.')) && !(await has('Choose a category.')));
  await b.selectOption('Impact', '3');
  await b.selectOption('Urgency', '3');
  await waitText('P5 Planning');
  await b.selectOption('Impact', '1');
  await b.selectOption('Urgency', '1');
  await waitText('P1 Critical');
  b.check('report form: the priority preview follows impact and urgency (3,3 gives P5, 1,1 gives P1)', true);
  await b.shot('f2-report-form');
  await b.clickButton('Report incident');
  await b.waitFor('/^\\/incidents\\/\\d+$/.test(location.pathname)', 'redirect to the new incident');
  id = Number((await b.path()).split('/').pop());
  await b.waitFor('/^INC/.test(document.querySelector("h1")?.textContent||"")', 'detail page');
  const chips = await b.chips();
  b.check('after reporting: detail page with the success notice, state New, P1 Critical', (await has('Your incident was reported')) && chips.includes('New') && chips.includes('P1 Critical'), chips.join(', '));
  b.check('the server routed it: team Network (from the Assignment rules), assigned to nobody', (await fact('Team', 'Network')) && (await fact('Assigned to', 'Nobody yet')));
  b.check('an employee gets no action buttons on the incident', !(await b.hasButton('Accept')) && !(await b.hasButton('Edit')) && !(await b.hasButton('Reassign')));

  await b.setField('Add a comment', 'Please hurry ' + tag);
  await b.clickButton('Add');
  await waitText('Please hurry ' + tag);
  b.check('employee: a comment is added and shown with the author name', (await has('Ananya Iyer')) && !(await b.js(`[...document.querySelectorAll('label')].some(l=>l.textContent.trim()==='Type')`)));
  await b.shot('f2-employee-detail');

  await b.go('/incidents');
  await search(tag);
  await waitRow(tag);
  const myRows = await rows();
  b.check('employee: search finds the incident in the list (title, number, state)', myRows.some((r) => r.includes('core router down') && r.includes('INC') && r.includes('New')), `${myRows.length} row(s)`);
  b.check('employee: the list shows only own incidents (not the one of employee2)', !myRows.some((r) => r.includes('belongs to employee2')));

  await openDetail(othersId).catch(() => {});
  await waitText('not found', 8000);
  b.check("employee: somebody else's incident is 'not found' (404, not 403)", await has('not found'));
  await b.logout();

  // ================================================================== db.engineer (a different team)
  await b.loginAs('sachin@icc.com');
  b.check("after Log out the next user starts at Home, not on the previous user's incident page", (await b.path()) === '/');
  await b.go('/incidents');
  await b.waitFor('document.querySelector("h1")?.textContent==="Incident queue"', 'queue title');
  await search(tag);
  await b.waitFor('document.body.innerText.includes("No incidents match")', 'empty queue');
  b.check("other team's queue (scope My queue) does not contain the Network incident", true);
  await b.clickButton('All incidents');
  await waitRow(tag);
  b.check('"All incidents" scope shows it', (await rows()).some((r) => r.includes('core router down')));
  await openDetail(id);
  b.check('an engineer of another team cannot Accept, but can Reassign and Edit', !(await b.hasButton('Accept')) && (await b.hasButton('Reassign')) && (await b.hasButton('Edit')));
  await b.logout();

  // ================================================================== net.engineer (the owning team)
  await b.loginAs('sai@icc.com');
  await b.go('/incidents');
  await search(tag);
  await waitRow('core router down');
  b.check('Network engineer: the incident is in "My queue"', true);

  // the server's 409 message, shown after somebody else was faster
  await openDetail(staleId);
  await b.waitFor(`[...document.querySelectorAll('button')].some(e=>e.textContent.trim()==='Accept')`, 'Accept button');
  await api('POST', `/api/incidents/${staleId}/accept`, { token: netEngineer });
  const second = await api('POST', `/api/incidents/${staleId}/accept`, { token: netEngineer });
  await b.clickButton('Accept');
  await waitText(second.body.message.slice(0, 40));
  b.check('someone else accepted first: the page shows the server\'s own 409 message', second.status === 409 && (await has(second.body.message)), second.body.message);
  await b.shot('f2-conflict');

  await openDetail(id);
  await b.clickButton('Accept');
  await waitText('You accepted');
  b.check('Accept: state In progress, assigned to the engineer', (await b.chips()).includes('In progress') && (await fact('Assigned to', 'Sai Vivek')));

  await b.selectOption('Type', 'Work note');
  await b.setField('Add a comment', 'Replacing the router ' + tag);
  await b.clickButton('Add');
  await waitText('Replacing the router ' + tag);
  const noteSaved = await api('GET', `/api/incidents/${id}`, { token: netEngineer });
  b.check('engineer: a work note is added, marked internal, and stored as WORK_NOTE on the server', (await b.chips()).includes('Work note (internal)') && noteSaved.body.comments.some((c) => c.type === 'WORK_NOTE' && c.body.includes(tag)));

  await b.clickButton('Edit');
  await b.waitFor('!!document.querySelector("[role=dialog]")', 'edit dialog');
  await b.setField('Description', 'Updated description ' + tag);
  await b.selectOption('Subcategory', 'VPN');
  await b.clickDialogButton('Save');
  await waitText('was updated');
  b.check('Edit: the changed description and the subcategory are saved and shown', (await has('Updated description ' + tag)) && (await fact('Subcategory', 'VPN')));

  // On hold: a reason is required, and waiting for the caller needs a comment the caller can see
  await b.clickButton('Put on hold');
  await b.waitFor('!!document.querySelector("[role=dialog]")', 'hold dialog');
  b.check('Put on hold is disabled until a reason is chosen', !(await b.js(`!![...document.querySelector('[role=dialog]').querySelectorAll('button')].find(e=>e.textContent.trim()==='Put on hold' && !e.disabled)`)));
  await b.selectOption('On hold reason', 'Awaiting caller');
  b.check('Awaiting caller needs a comment for the caller: still disabled', !(await b.js(`!![...document.querySelector('[role=dialog]').querySelectorAll('button')].find(e=>e.textContent.trim()==='Put on hold' && !e.disabled)`)));
  await b.setField('Comment for the caller', 'Please send a screenshot ' + tag);
  await b.clickDialogButton('Put on hold');
  await waitText('is on hold.');
  await b.waitFor("[...document.querySelectorAll('.MuiChip-root')].some(c=>c.textContent==='On hold')", 'the On hold chip');
  b.check('On hold: state and reason shown; Resolve is gone, Resume is offered', (await fact('On hold reason', 'Awaiting caller')) && !(await b.hasButton('Resolve')) && (await b.hasButton('Resume')));
  await b.shot('f2-on-hold');
  await b.clickButton('Resume');
  await waitText('is back in progress.');
  await b.waitFor("[...document.querySelectorAll('.MuiChip-root')].some(c=>c.textContent==='In progress')", 'the In progress chip');
  b.check('Resume: back In progress, the reason is gone, Resolve is offered again', !(await has('On hold reason')) && (await b.hasButton('Resolve')));

  await b.clickButton('Resolve');
  await b.waitFor('!!document.querySelector("[role=dialog]")', 'resolve dialog');
  b.check('Resolve is disabled until both fields are filled', !(await b.js(`!![...document.querySelector('[role=dialog]').querySelectorAll('button')].find(e=>e.textContent.trim()==='Resolve' && !e.disabled)`)));
  await b.selectOption('Resolution code', 'Solution provided');
  await b.setField('Resolution notes', 'Router replaced ' + tag);
  await b.clickDialogButton('Resolve');
  await waitText('Resolution: Solution provided');
  b.check('Resolve: state Resolved with the resolution shown', (await b.chips()).includes('Resolved') && (await has('Router replaced ' + tag)));

  await b.clickButton('Close');
  await waitText('This incident is closed.');
  await b.waitFor("[...document.querySelectorAll('.MuiChip-root')].some(c=>c.textContent==='Closed')", 'the Closed chip');
  b.check('Close: state Closed and final: no Resolve, no comment form, no Reopen (D39)', (await b.chips()).includes('Closed') && !(await b.hasButton('Resolve')) && !(await b.hasButton('Add')) && !(await b.hasButton('Reopen')));
  await b.shot('f2-closed');

  await openDetail(reassignId);
  await b.clickButton('Reassign');
  await b.waitFor('!!document.querySelector("[role=dialog]")', 'reassign dialog');
  await b.selectOption('New team', 'Database');
  await b.clickDialogButton('Reassign');
  await waitText('was reassigned');
  b.check('Reassign: now team Database, state New, assigned to nobody', (await fact('Team', 'Database')) && (await fact('Assigned to', 'Nobody yet')) && (await b.chips()).includes('New'));

  // Cancel: asks first, and a cancelled incident is final
  await openDetail(cancelId);
  await b.clickButton('Cancel incident');
  await b.waitFor('!!document.querySelector("[role=dialog]")', 'cancel dialog');
  await b.clickDialogButton('Cancel the incident');
  await waitText('was cancelled.');
  await b.waitFor("[...document.querySelectorAll('.MuiChip-root')].some(c=>c.textContent==='Cancelled')", 'the Cancelled chip');
  b.check('Cancel: state Cancelled, no more actions', !(await b.hasButton('Accept')) && !(await b.hasButton('Cancel incident')) && !(await b.hasButton('Reassign')));

  // Reopen (teacher feedback, D34): a resolved incident still takes comments, and can be reopened with a reason
  await openDetail(reopenId);
  await b.waitFor(`[...document.querySelectorAll('button')].some(e=>e.textContent.trim()==='Reopen')`, 'Reopen button');
  b.check('a resolved incident offers Reopen and still has the comment form', (await b.hasButton('Reopen')) && (await b.hasButton('Add')));
  await b.setField('Add a comment', 'Customer says it is still failing ' + tag);
  await b.clickButton('Add');
  await waitText('Customer says it is still failing ' + tag);
  b.check('a comment on a closed incident is accepted and shown', await has('Customer says it is still failing ' + tag));
  await b.clickButton('Reopen');
  await b.waitFor('!!document.querySelector("[role=dialog]")', 'reopen dialog');
  b.check('Reopen is disabled until a reason is written', !(await b.js(`!![...document.querySelector('[role=dialog]').querySelectorAll('button')].find(e=>e.textContent.trim()==='Reopen' && !e.disabled)`)));
  await b.setField('Why is it reopened?', 'The failure came back ' + tag);
  await b.clickDialogButton('Reopen');
  await waitText('was reopened.');
  await b.waitFor("[...document.querySelectorAll('.MuiChip-root')].some(c=>c.textContent==='In progress')", 'the In progress chip');
  b.check('Reopen: back In progress, the reason is in the comments, Resolve is offered again, the old resolution box is gone',
    (await has('Reopened: The failure came back ' + tag)) && (await b.hasButton('Resolve')) && !(await has('Resolution:')));
  await b.logout();

  // ================================================================== db.engineer again: the reassigned incident arrived
  await b.loginAs('sachin@icc.com');
  await b.go('/incidents');
  await search(tag);
  await waitRow('to be reassigned');
  await openDetail(reassignId);
  b.check('Database engineer: the reassigned incident is in their queue and they can Accept it', await b.hasButton('Accept'));
  await b.logout();

  // ================================================================== employee1 again: what the reporter sees
  await b.loginAs('ananya@icc.com');
  await openDetail(id);
  const t = await text();
  b.check('reporter sees the resolution, own comment and the question to the caller, but NOT the internal work note', t.includes('Router replaced ' + tag) && t.includes('Please hurry ' + tag) && t.includes('Please send a screenshot ' + tag) && !t.includes('Replacing the router ' + tag));
  await b.go('/incidents');
  await search(tag);
  await b.selectOption('State', 'Closed');
  await b.sleep(600);
  await waitRow('core router down');
  const closedRows = await rows();
  b.check('state filter "Closed" keeps only closed incidents, including the one that was reported', closedRows.length >= 1 && closedRows.every((r) => r.includes('Closed')) && closedRows.some((r) => r.includes('core router down')), `${closedRows.length} row(s)`);

  // the reporter reopens their own resolved incident
  await openDetail(reopenByReporterId);
  await b.waitFor(`[...document.querySelectorAll('button')].some(e=>e.textContent.trim()==='Reopen')`, 'Reopen button for the reporter');
  await b.clickButton('Reopen');
  await b.waitFor('!!document.querySelector("[role=dialog]")', 'reopen dialog');
  await b.setField('Why is it reopened?', 'It happened again ' + tag);
  await b.clickDialogButton('Reopen');
  await waitText('was reopened.');
  b.check('the reporter can reopen their own resolved incident (the reason is in the comments)', await has('Reopened: It happened again ' + tag));
  await b.logout();

  // ================================================================== admin
  await b.loginAs('mohit@icc.com');
  await b.go('/incidents');
  await b.waitFor('document.querySelector("h1")?.textContent==="All incidents"', 'admin list title');
  await search(tag);
  await waitRow('core router down');
  b.check('admin: default scope is All incidents, with a scope switch', (await b.hasButton('Reported by me')) && (await b.hasButton('All incidents')) && (await rows()).length >= 3);
  await openDetail(reassignId);
  b.check('admin on an open incident: can Edit, cannot Accept or Reassign (engineer actions)', (await b.hasButton('Edit')) && !(await b.hasButton('Accept')) && !(await b.hasButton('Reassign')));
  await b.shot('f2-admin-detail');
  await b.logout();
} catch (e) {
  await b.fail(e);
}
process.exit((await b.finish()) ? 0 : 1);
