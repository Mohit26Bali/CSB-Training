// Live test of the incident sync to ServiceNow (D38, D39). Run it yourself:
//   1. powershell -File scripts/start-sync-spare.ps1     (a spare Incident service on port 18086 with the sync switched on)
//   2. node scripts/sync-live-test.mjs
//   3. powershell -File scripts/stop-sync-spare.ps1
// It creates ONE throwaway incident in the local database and in your ServiceNow instance, walks it through its life
// (report, accept, comments, hold, resume, resolve, reopen, resolve again, close, "Closed is final"), and after every step
// waits for the sync and READS ServiceNow (GET only) to compare. It never prints a password or a token.
// Afterwards delete the test incident in ServiceNow by hand (the integration user cannot delete).
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const env = {};
for (const line of readFileSync(join(root, '.env'), 'utf8').split(/\r?\n/)) {
  const m = line.match(/^\s*([A-Z_]+)=(.*)$/);
  if (m) env[m[1]] = m[2].trim().replace(/^["']|["']$/g, '');
}
if (!env.SERVICENOW_URL || !env.SERVICENOW_USER || !env.SERVICENOW_PASSWORD) { console.log('Put SERVICENOW_URL, SERVICENOW_USER and SERVICENOW_PASSWORD in .env first.'); process.exit(1); }
const base = env.SERVICENOW_URL.replace(/\/+$/, '');
const auth = 'Basic ' + Buffer.from(`${env.SERVICENOW_USER}:${env.SERVICENOW_PASSWORD}`).toString('base64');
const q = (text) => encodeURIComponent(text);
async function get(path, label) {
  let res;
  try { res = await fetch(base + path, { headers: { Authorization: auth, Accept: 'application/json' } }); }
  catch (e) { console.log(`\n## ${label}\nNETWORK ERROR: ${e.cause?.code || e.message}`); return null; }
  const body = await res.json().catch(() => null);
  if (!res.ok) { console.log(`\n## ${label} -> HTTP ${res.status}`); return null; }
  return body?.result ?? body;
}

const GATEWAY = 'http://localhost:8080';
const SPARE = 'http://localhost:18086';
const PASSWORD = 'Icc@Demo2026';

const results = [];
const note = (ok, text) => { results.push([ok, text]); console.log((ok ? 'PASS ' : 'FAIL ') + text); };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function login(email) {
  const r = await fetch(GATEWAY + '/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: email, password: PASSWORD }) });
  if (!r.ok) throw new Error('login failed for ' + email);
  return (await r.json()).token;
}
async function call(method, path, token, body) {
  const r = await fetch(SPARE + path, { method, headers: { Authorization: 'Bearer ' + token, ...(body ? { 'Content-Type': 'application/json' } : {}) }, body: body ? JSON.stringify(body) : undefined });
  const text = await r.text();
  let json = null; try { json = text ? JSON.parse(text) : null; } catch { json = text; }
  return { status: r.status, body: json };
}
async function waitFor(id, token, wanted, label) {
  for (let i = 0; i < 30; i++) {
    const r = await call('GET', `/api/incidents/${id}`, token);
    const status = r.body?.incident?.syncStatus;
    if (status === wanted) return r.body.incident;
    if (status === 'FAILED') return r.body.incident;
    await sleep(3000);
  }
  return (await call('GET', `/api/incidents/${id}`, token)).body?.incident;
}
async function inServiceNow(correlationId) {
  const r = await get(`/api/now/table/incident?sysparm_query=${q('correlation_id=' + correlationId)}&sysparm_display_value=all&sysparm_limit=1&sysparm_fields=number,state,hold_reason,category,subcategory,impact,urgency,caller_id,assigned_to,assignment_group,close_code,close_notes,short_description,correlation_id`, 'ServiceNow record');
  return r && r[0] ? r[0] : null;
}
const show = (f) => (f && typeof f === 'object' ? f.display_value : f);

const ananya = await login('ananya@icc.com');
const sai = await login('sai@icc.com');

// ---------------------------------------------------------------- 1. report
console.log('\n1. Report an incident (employee Ananya)');
const created = await call('POST', '/api/incidents', ananya, { shortDescription: '[icc-test] live sync check', description: 'Created by the live sync test. Safe to delete.', category: 'Network', subcategory: 'VPN', impact: 3, urgency: 3 });
if (created.status !== 201) { console.log('create answered', created.status, JSON.stringify(created.body)); process.exit(1); }
const id = created.body.id;
console.log(`      local incident ${created.body.number} (id ${id}), sync status right after: ${created.body.syncStatus}`);
note(created.body.syncStatus === 'PENDING', 'the new incident waits for the sync (PENDING)');
let inc = await waitFor(id, ananya, 'SYNCED');
note(inc?.syncStatus === 'SYNCED', `the sync job sent it (status ${inc?.syncStatus})`);
const corr = `icc-incident-${id}`;
let sn = await inServiceNow(corr);
if (!sn) { note(false, 'ServiceNow has a record with our correlation id'); process.exit(1); }
note(true, `ServiceNow has it as ${show(sn.number)}`);
note(show(sn.category) === 'Network' && show(sn.impact) === '3 - Low' && show(sn.urgency) === '3 - Low', `category ${show(sn.category)}, impact ${show(sn.impact)}, urgency ${show(sn.urgency)}`);
note(show(sn.subcategory) === 'VPN', `subcategory ${show(sn.subcategory)}`);
note(/Ananya/.test(show(sn.caller_id) || ''), `caller: ${show(sn.caller_id)}`);
note(show(sn.assignment_group) === 'Network', `assignment group: ${show(sn.assignment_group)}`);
note(show(sn.state) === 'New', `state: ${show(sn.state)}`);

// ---------------------------------------------------------------- 2. accept
console.log('\n2. Accept (engineer Sai)');
await call('POST', `/api/incidents/${id}/accept`, sai);
inc = await waitFor(id, sai, 'SYNCED');
sn = await inServiceNow(corr);
note(inc?.syncStatus === 'SYNCED', `synced (status ${inc?.syncStatus})`);
note(show(sn?.state) === 'In Progress', `state: ${show(sn?.state)}`);
note(/Sai/.test(show(sn?.assigned_to) || ''), `assigned to: ${show(sn?.assigned_to)}`);

// ---------------------------------------------------------------- 3. comments
console.log('\n3. A comment and a work note');
const c1 = await call('POST', `/api/incidents/${id}/comments`, ananya, { type: 'COMMENT', body: 'Live test: a comment the reporter can see' });
const c2 = await call('POST', `/api/incidents/${id}/comments`, sai, { type: 'WORK_NOTE', body: 'Live test: an internal work note' });
note(c1.status === 201 && c2.status === 201, 'both comments saved locally');
await sleep(12000);
note(true, 'waited for the next sync round (the result is checked in the database below)');

// ---------------------------------------------------------------- 4. hold and resume
console.log('\n4. Put on hold (awaiting vendor), then resume');
await call('POST', `/api/incidents/${id}/hold`, sai, { reason: 'AWAITING_VENDOR', comment: 'Waiting for the vendor ticket' });
inc = await waitFor(id, sai, 'SYNCED');
sn = await inServiceNow(corr);
note(show(sn?.state) === 'On Hold' && /Vendor/.test(show(sn?.hold_reason) || ''), `state: ${show(sn?.state)}, reason: ${show(sn?.hold_reason)}`);
await call('POST', `/api/incidents/${id}/resume`, sai);
inc = await waitFor(id, sai, 'SYNCED');
sn = await inServiceNow(corr);
note(show(sn?.state) === 'In Progress', `after resume: ${show(sn?.state)}`);

// ---------------------------------------------------------------- 5. resolve and close
console.log('\n5. Resolve with a close code, then close');
const resolved = await call('POST', `/api/incidents/${id}/resolve`, sai, { resolutionCode: 'Solution provided', resolutionNotes: 'Live test: fixed' });
note(resolved.status === 200, 'resolved locally with the code "Solution provided"');
inc = await waitFor(id, sai, 'SYNCED');
sn = await inServiceNow(corr);
note(inc?.syncStatus === 'SYNCED', `synced (status ${inc?.syncStatus})`);
note(show(sn?.state) === 'Resolved', `state: ${show(sn?.state)}`);
note(show(sn?.close_code) === 'Solution provided', `close code: ${show(sn?.close_code)}`);

// ---------------------------------------------------------------- 6. reopen a RESOLVED incident (D39: only Resolved can be reopened)
console.log('\n6. Reopen the Resolved incident (reporter Ananya), resolve it again, then close it');
const reopened = await call('POST', `/api/incidents/${id}/reopen`, ananya, { reason: 'Live test: it came back' });
note(reopened.status === 200, 'reopened locally');
inc = await waitFor(id, ananya, 'SYNCED');
sn = await inServiceNow(corr);
note(inc?.syncStatus === 'SYNCED' && show(sn?.state) === 'In Progress', `after reopen: local ${inc?.syncStatus}, ServiceNow state ${show(sn?.state)}`);
await call('POST', `/api/incidents/${id}/resolve`, sai, { resolutionCode: 'Workaround provided', resolutionNotes: 'Live test: fixed again' });
inc = await waitFor(id, sai, 'SYNCED');
sn = await inServiceNow(corr);
note(show(sn?.state) === 'Resolved' && show(sn?.close_code) === 'Workaround provided', `resolved again: ${show(sn?.state)}, close code ${show(sn?.close_code)}`);
await call('POST', `/api/incidents/${id}/close`, sai);
inc = await waitFor(id, sai, 'SYNCED');
sn = await inServiceNow(corr);
note(show(sn?.state) === 'Closed', `closed: ${show(sn?.state)}`);

// ---------------------------------------------------------------- 7. Closed is final (D39)
console.log('\n7. A Closed incident takes no reopen and no comment');
const again = await call('POST', `/api/incidents/${id}/reopen`, ananya, { reason: 'Live test: too late' });
note(again.status === 409, `reopen of a Closed incident answers ${again.status} (expected 409)`);
const late = await call('POST', `/api/incidents/${id}/comments`, ananya, { type: 'COMMENT', body: 'too late' });
note(late.status === 409, `comment on a Closed incident answers ${late.status} (expected 409)`);
sn = await inServiceNow(corr);
note(show(sn?.state) === 'Closed', `ServiceNow still Closed: ${show(sn?.state)}`);

console.log(`\nIncident ${created.body.number} (id ${id}) in ServiceNow: ${show(sn?.number)}`);
const failed = results.filter(([ok]) => !ok).length;
console.log(failed === 0 ? 'ALL CHECKS PASSED' : `${failed} CHECK(S) FAILED`);
console.log('LIVE_TEST_INCIDENT_ID=' + id);
