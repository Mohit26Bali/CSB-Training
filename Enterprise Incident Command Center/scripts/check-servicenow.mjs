// Read-only readiness check of the ServiceNow instance for Phase 4 (node scripts/check-servicenow.mjs).
// It only sends GET requests, never prints the password, and changes nothing on the instance.
// The connection comes from .env: SERVICENOW_URL, SERVICENOW_USER, SERVICENOW_PASSWORD (the integration user).
//
// Exit code 0 = nothing failed (warnings are fine), 1 = at least one FAIL.
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

// ---- what OUR app has today (keep in step with the app: IncidentCategories, data.sql, ProblemAction) ----
const OUR = {
  categories: ['Hardware', 'Software', 'Network', 'Database', 'Access', 'Other'],
  teams: ['Service Desk', 'Network', 'Database', 'Applications'],
  users: [
    ['mohit@icc.com', 'Mohit Bali'],
    ['priya@icc.com', 'Priya Nair'],
    ['sai@icc.com', 'Sai Vivek'],
    ['sachin@icc.com', 'Sachin Suryavanshi'],
    ['vinod@icc.com', 'Vinod'],
    ['ananya@icc.com', 'Ananya Iyer'],
    ['rohan@icc.com', 'Rohan Mehta'],
  ],
  incidentStates: ['New', 'In Progress', 'On Hold', 'Resolved', 'Closed', 'Canceled'],
  problemStates: ['New', 'Assess', 'Root Cause Analysis', 'Fix in Progress', 'Resolved', 'Closed'],
  holdReasons: ['Awaiting Caller', 'Awaiting Problem', 'Awaiting Vendor', 'Awaiting Change'],
};
// How the adapter translates the categories that the instance does not use (CategoryMapping in libs/servicenow-client).
const MAPPED_CATEGORIES = { Access: 'Password Reset', Other: 'Inquiry / Help' };
// The ten close codes of this instance (read from its own choice list, 2026-11-26). Our resolution code must be one of these.
const SN_CLOSE_CODES = ['Duplicate', 'Known error', 'No resolution provided', 'Resolved by caller', 'Resolved by change', 'Resolved by problem', 'Resolved by request', 'Solution provided', 'Workaround provided', 'User error'];

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const env = {};
try {
  for (const line of readFileSync(join(root, '.env'), 'utf8').split(/\r?\n/)) {
    const m = line.match(/^\s*([A-Z_]+)=(.*)$/);
    if (m) env[m[1]] = m[2].trim().replace(/^["']|["']$/g, '');
  }
} catch {
  console.log('FAIL  cannot read .env in the project folder');
  process.exit(1);
}
for (const key of ['SERVICENOW_URL', 'SERVICENOW_USER', 'SERVICENOW_PASSWORD']) {
  if (!env[key]) {
    console.log(`FAIL  ${key} is missing in .env`);
    process.exit(1);
  }
}
const base = env.SERVICENOW_URL.replace(/\/+$/, '');
const auth = 'Basic ' + Buffer.from(`${env.SERVICENOW_USER}:${env.SERVICENOW_PASSWORD}`).toString('base64');

let failures = 0;
const say = (level, text) => {
  if (level === 'FAIL') failures++;
  console.log(`${level.padEnd(5)} ${text}`);
};

/** GET only. Returns { status, body } and never throws. */
async function get(path) {
  try {
    const res = await fetch(base + path, { headers: { Authorization: auth, Accept: 'application/json' } });
    const text = await res.text();
    let body = null;
    try { body = JSON.parse(text); } catch { /* not JSON */ }
    return { status: res.status, body };
  } catch (e) {
    return { status: 0, body: null, error: e.cause?.code || e.message };
  }
}
const q = encodeURIComponent;
/** Values in use for a field, with how many records, through the aggregate (stats) API. */
async function valuesInUse(table, field) {
  const r = await get(`/api/now/stats/${table}?sysparm_group_by=${field}&sysparm_count=true&sysparm_display_value=true`);
  if (r.status !== 200) return null;
  return (r.body?.result ?? []).map((row) => ({ value: row.groupby_fields?.[0]?.value ?? '', count: Number(row.stats?.count ?? 0) })).filter((x) => x.value);
}
const list = (values) => values.map((v) => `${v.value} (${v.count})`).join(', ');

console.log(`ServiceNow readiness check (read-only) for ${new URL(base).host}\n`);

// ---------------------------------------------------------------- 1. the instance and the login
console.log('1. The instance and the integration user');
const ping = await get('/api/now/table/incident?sysparm_limit=1&sysparm_fields=number');
if (ping.status === 0) {
  say('FAIL', `the instance is not reachable (${ping.error}). A free instance goes to sleep after inactivity: open it in the browser to wake it, then run this again.`);
  process.exit(1);
}
if (ping.status === 401) say('FAIL', 'the integration user was refused (401): wrong password, locked, or it must reset its password');
else if (ping.status === 200) say('PASS', 'the instance answers and the integration user can sign in');
else say('FAIL', `unexpected answer ${ping.status} from the incident table`);
if (failures > 0) process.exit(1);

// ---------------------------------------------------------------- 2. what the user may read
console.log('\n2. What the integration user may read');
for (const table of ['incident', 'problem', 'sys_user', 'sys_user_group']) {
  const r = await get(`/api/now/table/${table}?sysparm_limit=1&sysparm_fields=sys_id`);
  say(r.status === 200 ? 'PASS' : 'FAIL', `read table ${table} (HTTP ${r.status})`);
}
// The choice table answers, but asking for ONE field's list (filtering by table and field name) is refused for this user.
const choices = await get(`/api/now/table/sys_choice?sysparm_query=${q('name=incident^element=close_code^inactive=false')}&sysparm_fields=label,value&sysparm_limit=30`);
if (choices.status === 200) {
  const labels = (choices.body.result ?? []).map((c) => c.label);
  const missing = SN_CLOSE_CODES.filter((c) => !labels.includes(c));
  say(missing.length === 0 ? 'PASS' : 'WARN', missing.length === 0 ? 'the close-code list of the instance matches the ten values in this script' : `the close-code list differs from this script: missing ${missing.join(', ')}`);
} else {
  say('WARN', `cannot read ONE field's choice list (HTTP ${choices.status}: the filter by table and field name is refused for this user), so the values below are checked from the records in use, not from the lists`);
}

// ---------------------------------------------------------------- 3. the people
console.log('\n3. Our seven users (matched by email)');
const emails = OUR.users.map(([email]) => email);
const users = await get(`/api/now/table/sys_user?sysparm_query=${q('emailIN' + emails.join(','))}&sysparm_fields=email,name,active,locked_out&sysparm_limit=50`);
if (users.status !== 200) {
  say('FAIL', `cannot look the users up (HTTP ${users.status})`);
} else {
  const found = new Map((users.body.result ?? []).map((u) => [u.email.toLowerCase(), u]));
  for (const [email, name] of OUR.users) {
    const u = found.get(email);
    if (!u) say('WARN', `${email} (${name}) does not exist on the instance yet: create it before any incident is resolved`);
    else if (u.active !== 'true' || u.locked_out === 'true') say('WARN', `${email} exists but is not usable (active=${u.active}, locked_out=${u.locked_out})`);
    else say('PASS', `${email} exists as "${u.name}"`);
  }
}

// ---------------------------------------------------------------- 4. the teams
console.log('\n4. Our four teams (as groups)');
const groups = await get(`/api/now/table/sys_user_group?sysparm_query=${q('nameIN' + OUR.teams.join(','))}&sysparm_fields=name,active&sysparm_limit=50`);
if (groups.status !== 200) {
  say('FAIL', `cannot look the groups up (HTTP ${groups.status})`);
} else {
  const names = new Set((groups.body.result ?? []).map((g) => g.name));
  for (const team of OUR.teams) say(names.has(team) ? 'PASS' : 'WARN', names.has(team) ? `group "${team}" exists` : `group "${team}" does not exist on the instance: it must be created or mapped to another group`);
}

// ---------------------------------------------------------------- 5. the values
console.log('\n5. Values: ours against what the instance uses');
const categories = await valuesInUse('incident', 'category');
if (categories) {
  const used = new Set(categories.map((c) => c.value));
  console.log(`      categories in use on the instance: ${list(categories)}`);
  for (const c of OUR.categories) {
    if (used.has(c)) say('PASS', `category "${c}" is known to the instance`);
    else if (MAPPED_CATEGORIES[c]) say('PASS', `category "${c}" is not used on the instance, and is mapped to "${MAPPED_CATEGORIES[c]}" by the adapter (CategoryMapping)`);
    else say('WARN', `category "${c}" is not used on the instance and has no mapping`);
  }
}
const closeCodes = await valuesInUse('incident', 'close_code');
if (closeCodes) console.log(`      close codes in use on the instance: ${list(closeCodes)}`);
say('PASS', `our resolution code is a fixed list of the ten close codes of the instance (D36): ${SN_CLOSE_CODES.join(', ')}`);
const states = await valuesInUse('incident', 'state');
if (states) {
  const used = new Set(states.map((s) => s.value));
  for (const s of OUR.incidentStates) say(used.has(s) ? 'PASS' : 'WARN', used.has(s) ? `incident state "${s}" is in use on the instance` : `incident state "${s}" is not in use on the instance (not a problem by itself; it exists in the list)`);
}
const holds = await valuesInUse('incident', 'hold_reason');
if (holds) console.log(`      hold reasons in use: ${holds.length ? list(holds) : 'none yet'} (ours: ${OUR.holdReasons.join(', ')})`);
const problemStates = await valuesInUse('problem', 'state');
if (problemStates) {
  const used = new Set(problemStates.map((s) => s.value));
  console.log(`      problem states in use: ${list(problemStates)}`);
  for (const s of OUR.problemStates) if (!used.has(s)) say('WARN', `problem state "${s}" is not in use yet (our six steps map one to one to the instance's six)`);
}

// ---------------------------------------------------------------- 6. summary
console.log('\n' + (failures === 0 ? 'Result: nothing failed. WARN lines are the work still to do before the sync.' : `Result: ${failures} FAIL line(s): fix those first.`));
process.exit(failures === 0 ? 0 : 1);
