// Run with: npm test   (uses Node's built-in test runner, no packages needed)
const test = require('node:test');
const assert = require('node:assert/strict');
const L = require('../js/logic.js');

const TODAY = '2026-11-25';
const good = (over = {}) => ({
  id: 'EMP101',
  name: 'Anita Sharma',
  department: 'Engineering',
  role: 'Software Engineer',
  status: 'Active',
  joiningDate: '2021-06-14',
  email: 'anita@peoplehub.com',
  ...over,
});

test('localToday uses the local calendar day, not the UTC day', () => {
  // 01:30 on 26 Nov in a time zone ahead of UTC is still 25 Nov in UTC; the local day must win.
  const localMorning = new Date(2026, 10, 26, 1, 30);
  assert.equal(L.localToday(localMorning), '2026-11-26');
  assert.equal(L.localToday(new Date(2026, 0, 5, 23, 59)), '2026-01-05');
});

test('isIsoDate accepts real dates only', () => {
  assert.equal(L.isIsoDate('2024-02-29'), true);
  assert.equal(L.isIsoDate('2023-02-29'), false);
  assert.equal(L.isIsoDate('2024-13-01'), false);
  assert.equal(L.isIsoDate('2024-1-1'), false);
  assert.equal(L.isIsoDate('<script>'), false);
  assert.equal(L.isIsoDate(20240101), false);
  assert.equal(L.isIsoDate(null), false);
});

test('escapeHtml neutralises markup in every place it can appear', () => {
  assert.equal(L.escapeHtml('<img src=x onerror="a()">'), '&lt;img src=x onerror=&quot;a()&quot;&gt;');
  assert.equal(L.escapeHtml("O'Brien & Sons"), 'O&#39;Brien &amp; Sons');
});

test('cleanText trims, squeezes spaces, drops control characters and rejects non-text', () => {
  assert.equal(L.cleanText('  Ada \n  Lovelace\u0000 '), 'Ada Lovelace');
  assert.equal(L.cleanText(42), '');
  assert.equal(L.cleanText(null), '');
});

test('validateEmployeeInput passes a good record', () => {
  assert.deepEqual(L.validateEmployeeInput(good(), TODAY), { valid: true, errors: {} });
});

test('validateEmployeeInput reports each bad field', () => {
  const { valid, errors } = L.validateEmployeeInput(
    { name: ' a ', department: 'Cooking', role: '', status: 'Gone', joiningDate: '2099-01-01', email: 'nope' },
    TODAY
  );
  assert.equal(valid, false);
  assert.deepEqual(Object.keys(errors).sort(), ['department', 'email', 'joiningDate', 'name', 'role', 'status']);
  assert.match(errors.joiningDate, /future/);
});

test('validateEmployeeInput enforces the length limits and the date range', () => {
  assert.ok(L.validateEmployeeInput(good({ name: 'x'.repeat(61) }), TODAY).errors.name);
  assert.ok(L.validateEmployeeInput(good({ role: 'x'.repeat(61) }), TODAY).errors.role);
  assert.ok(L.validateEmployeeInput(good({ email: 'a@' + 'b'.repeat(100) + '.com' }), TODAY).errors.email);
  assert.ok(L.validateEmployeeInput(good({ joiningDate: '1960-01-01' }), TODAY).errors.joiningDate);
  assert.ok(L.validateEmployeeInput(good({ joiningDate: '2023-02-30' }), TODAY).errors.joiningDate);
  assert.equal(L.validateEmployeeInput(good({ joiningDate: TODAY }), TODAY).valid, true); // today is allowed
});

test('the email is optional', () => {
  assert.equal(L.validateEmployeeInput(good({ email: '' }), TODAY).valid, true);
  assert.equal(L.validateEmployeeInput(good({ email: undefined }), TODAY).valid, true);
});

test('cleanEmployee keeps a good record and cleans its text', () => {
  const e = L.cleanEmployee(good({ name: '  Anita   Sharma ' }), TODAY);
  assert.equal(e.name, 'Anita Sharma');
  assert.equal(e.id, 'EMP101');
});

test('cleanEmployee refuses tampered storage: script in a field, a made-up id, an unknown status', () => {
  assert.equal(L.cleanEmployee(good({ id: '"><script>alert(1)</script>' }), TODAY), null);
  assert.equal(L.cleanEmployee(good({ id: 'EMP' }), TODAY), null);
  assert.equal(L.cleanEmployee(good({ status: '<b>x</b>' }), TODAY), null);
  assert.equal(L.cleanEmployee(good({ department: 'x" onmouseover="a()' }), TODAY), null);
  assert.equal(L.cleanEmployee(good({ joiningDate: '2999-01-01' }), TODAY), null);
  assert.equal(L.cleanEmployee(null, TODAY), null);
  assert.equal(L.cleanEmployee('EMP101', TODAY), null);
  assert.equal(L.cleanEmployee({ id: 'EMP101' }, TODAY), null);
});

test('cleanEmployee does not carry over unknown properties', () => {
  const e = L.cleanEmployee({ ...good(), isAdmin: true, __proto__: { x: 1 } }, TODAY);
  assert.deepEqual(Object.keys(e).sort(), ['department', 'email', 'id', 'joiningDate', 'name', 'role', 'status']);
});

test('cleanEmployeeList drops bad and duplicate records but keeps the good ones', () => {
  const fallback = [good({ id: 'EMP900' })];
  const saved = [good(), { id: 'bad' }, good({ name: 'Copy' }), good({ id: 'EMP102', name: 'Rahul Verma' })];
  const list = L.cleanEmployeeList(saved, fallback, TODAY);
  assert.deepEqual(list.map((e) => e.id), ['EMP101', 'EMP102']);
  assert.equal(list[0].name, 'Anita Sharma'); // first one wins
});

test('cleanEmployeeList falls back to the sample data when nothing usable is saved', () => {
  const fallback = [good({ id: 'EMP900' })];
  assert.deepEqual(L.cleanEmployeeList('nonsense', fallback, TODAY), fallback);
  assert.deepEqual(L.cleanEmployeeList({ a: 1 }, fallback, TODAY), fallback);
  assert.deepEqual(L.cleanEmployeeList([{ id: 'bad' }], fallback, TODAY), fallback);
  assert.notEqual(L.cleanEmployeeList('x', fallback, TODAY), fallback); // a copy, not the same array
});

test('cleanEmployeeList keeps an empty list when the person deleted everyone', () => {
  assert.deepEqual(L.cleanEmployeeList([], [good()], TODAY), []);
});

const people = [
  good({ id: 'EMP101', name: 'Anita Sharma', department: 'Engineering', status: 'Active', joiningDate: '2021-06-14' }),
  good({ id: 'EMP102', name: 'Rahul Verma', department: 'Finance', status: 'Active', joiningDate: '2020-02-03' }),
  good({ id: 'EMP103', name: 'Priya Nair', department: 'HR', status: 'On Leave', joiningDate: '2022-09-19' }),
  good({ id: 'EMP1000', name: 'Zed Last', department: 'Engineering', status: 'Inactive', joiningDate: '2024-01-01' }),
];

test('filterEmployees: search by name or id, case-insensitive', () => {
  assert.deepEqual(L.filterEmployees(people, { search: 'ANITA' }).map((e) => e.id), ['EMP101']);
  assert.deepEqual(L.filterEmployees(people, { search: 'emp102' }).map((e) => e.id), ['EMP102']);
  assert.deepEqual(L.filterEmployees(people, { search: '  ' }).length, 4);
});

test('filterEmployees: search, department and status combine', () => {
  assert.deepEqual(L.filterEmployees(people, { department: 'Engineering' }).map((e) => e.id), ['EMP101', 'EMP1000']);
  assert.deepEqual(L.filterEmployees(people, { department: 'Engineering', status: 'Inactive' }).map((e) => e.id), ['EMP1000']);
  assert.deepEqual(L.filterEmployees(people, { search: 'rahul', status: 'On Leave' }), []);
});

test('filterEmployees does not treat the search as a pattern', () => {
  assert.deepEqual(L.filterEmployees(people, { search: '.*' }), []);
  assert.deepEqual(L.filterEmployees(people, { search: '(' }), []);
});

test('sortEmployees: ids sort by number, so EMP1000 comes after EMP103', () => {
  assert.deepEqual(L.sortEmployees(people, 'id', 'asc').map((e) => e.id), ['EMP101', 'EMP102', 'EMP103', 'EMP1000']);
  assert.deepEqual(L.sortEmployees(people, 'id', 'desc').map((e) => e.id), ['EMP1000', 'EMP103', 'EMP102', 'EMP101']);
});

test('sortEmployees: by name, by date, and it does not change the original list', () => {
  const before = people.map((e) => e.id);
  assert.deepEqual(L.sortEmployees(people, 'name', 'asc').map((e) => e.name), ['Anita Sharma', 'Priya Nair', 'Rahul Verma', 'Zed Last']);
  assert.deepEqual(L.sortEmployees(people, 'joiningDate', 'asc').map((e) => e.id), ['EMP102', 'EMP101', 'EMP103', 'EMP1000']);
  assert.deepEqual(people.map((e) => e.id), before);
});

test('sortEmployees: an unknown column falls back to id (no property of the object is exposed)', () => {
  assert.deepEqual(L.sortEmployees(people, 'constructor', 'asc').map((e) => e.id), ['EMP101', 'EMP102', 'EMP103', 'EMP1000']);
});

test('nextEmployeeId continues from the highest number, starting at EMP101', () => {
  assert.equal(L.nextEmployeeId([]), 'EMP101');
  assert.equal(L.nextEmployeeId(people), 'EMP1001');
  assert.equal(L.nextEmployeeId([good({ id: 'EMP50' })]), 'EMP101');
});

test('paginate keeps the page inside the range', () => {
  assert.deepEqual(L.paginate(0, 1, 6), { pages: 1, page: 1, start: 0 });
  assert.deepEqual(L.paginate(12, 2, 6), { pages: 2, page: 2, start: 6 });
  assert.deepEqual(L.paginate(13, 9, 6), { pages: 3, page: 3, start: 12 });
  assert.deepEqual(L.paginate(13, -4, 6), { pages: 3, page: 1, start: 0 });
  assert.deepEqual(L.paginate(13, 'x', 6), { pages: 3, page: 1, start: 0 });
});

test('departmentCounts gives counts and rounded percentages, even with no one', () => {
  const rows = L.departmentCounts(people);
  assert.deepEqual(rows.find((r) => r.department === 'Engineering'), { department: 'Engineering', count: 2, percent: 50 });
  assert.deepEqual(rows.find((r) => r.department === 'Marketing'), { department: 'Marketing', count: 0, percent: 0 });
  assert.ok(L.departmentCounts([]).every((r) => r.count === 0 && r.percent === 0));
});

test('formatDate shows a readable date, and a dash for a bad one', () => {
  assert.match(L.formatDate('2021-06-14'), /14 Jun 2021/);
  assert.equal(L.formatDate('garbage'), '—');
  assert.equal(L.formatDate(undefined), '—');
});
