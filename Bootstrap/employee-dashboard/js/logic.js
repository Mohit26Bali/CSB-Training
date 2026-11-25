// PeopleHub logic that does not touch the page: cleaning and checking employee data, searching,
// sorting, paging and counting. Kept apart from app.js so it can be tested in Node (see tests/).
// Works in the browser (as window.PeopleHubLogic) and in Node (require).
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.PeopleHubLogic = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  const DEPARTMENTS = ['Engineering', 'Finance', 'HR', 'Marketing'];
  const STATUSES = ['Active', 'Inactive', 'On Leave'];

  const LIMITS = { nameMin: 2, nameMax: 60, roleMin: 2, roleMax: 60, emailMax: 100, earliestJoining: '1970-01-01' };
  const ID_PATTERN = /^EMP\d{1,6}$/;
  const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  const SORTABLE = ['id', 'name', 'department', 'role', 'status', 'joiningDate'];

  /** Today as YYYY-MM-DD in the visitor's own time zone (toISOString would give the UTC day, which is
   *  yesterday for a few hours every morning in India). */
  function localToday(now = new Date()) {
    const pad = (n) => String(n).padStart(2, '0');
    return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
  }

  /** True for a real calendar date written YYYY-MM-DD (so 2024-02-30 is rejected). */
  function isIsoDate(value) {
    if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
    const [y, m, d] = value.split('-').map(Number);
    const date = new Date(Date.UTC(y, m - 1, d));
    return date.getUTCFullYear() === y && date.getUTCMonth() === m - 1 && date.getUTCDate() === d;
  }

  const escapeHtml = (text) =>
    String(text).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

  /** Trims, drops control characters and squeezes runs of spaces. Non-text becomes "". */
  function cleanText(value) {
    if (typeof value !== 'string') return '';
    // eslint-disable-next-line no-control-regex
    return value.replace(/[\u0000-\u001f\u007f]/g, '').replace(/\s+/g, ' ').trim();
  }

  /** Checks what a person typed into the form. Returns { valid, errors } with one message per bad field. */
  function validateEmployeeInput(input, today = localToday()) {
    const errors = {};
    const name = cleanText(input.name);
    const role = cleanText(input.role);
    const email = cleanText(input.email);

    if (name.length < LIMITS.nameMin) errors.name = `Enter a name of at least ${LIMITS.nameMin} characters.`;
    else if (name.length > LIMITS.nameMax) errors.name = `The name can be at most ${LIMITS.nameMax} characters.`;

    if (!DEPARTMENTS.includes(input.department)) errors.department = 'Choose a department.';

    if (role.length < LIMITS.roleMin) errors.role = `Enter a role of at least ${LIMITS.roleMin} characters.`;
    else if (role.length > LIMITS.roleMax) errors.role = `The role can be at most ${LIMITS.roleMax} characters.`;

    if (!STATUSES.includes(input.status)) errors.status = 'Choose a status.';

    if (!isIsoDate(input.joiningDate)) errors.joiningDate = 'Select the joining date.';
    else if (input.joiningDate > today) errors.joiningDate = 'The joining date cannot be in the future.';
    else if (input.joiningDate < LIMITS.earliestJoining) errors.joiningDate = 'That joining date is too far in the past.';

    if (email && (email.length > LIMITS.emailMax || !EMAIL_PATTERN.test(email))) errors.email = 'Enter a valid email address.';

    return { valid: Object.keys(errors).length === 0, errors };
  }

  /** One stored record, made safe: returns a clean copy, or null if it is not a usable employee.
   *  Stored data can be edited by anyone with the browser's developer tools, so nothing is trusted. */
  function cleanEmployee(raw, today = localToday()) {
    if (!raw || typeof raw !== 'object') return null;
    if (typeof raw.id !== 'string' || !ID_PATTERN.test(raw.id)) return null;
    const candidate = {
      name: cleanText(raw.name),
      department: raw.department,
      role: cleanText(raw.role),
      status: raw.status,
      joiningDate: raw.joiningDate,
      email: cleanText(raw.email),
    };
    if (!validateEmployeeInput(candidate, today).valid) return null;
    return { id: raw.id, ...candidate };
  }

  /** The saved list, made safe. Bad or duplicate records are dropped; if nothing usable is left, `fallback` is used. */
  function cleanEmployeeList(saved, fallback, today = localToday()) {
    if (!Array.isArray(saved)) return fallback.slice();
    const seen = new Set();
    const list = [];
    for (const raw of saved) {
      const employee = cleanEmployee(raw, today);
      if (!employee || seen.has(employee.id)) continue;
      seen.add(employee.id);
      list.push(employee);
    }
    return list.length > 0 || saved.length === 0 ? list : fallback.slice();
  }

  /** Search (name or ID), department and status together. */
  function filterEmployees(list, { search = '', department = '', status = '' } = {}) {
    const term = String(search).trim().toLowerCase();
    return list.filter(
      (e) =>
        (!term || e.name.toLowerCase().includes(term) || e.id.toLowerCase().includes(term)) &&
        (!department || e.department === department) &&
        (!status || e.status === status)
    );
  }

  /** A sorted copy. IDs sort by number (EMP99 before EMP100); an unknown column falls back to the ID. */
  function sortEmployees(list, key = 'id', direction = 'asc') {
    const column = SORTABLE.includes(key) ? key : 'id';
    const sign = direction === 'desc' ? -1 : 1;
    return list.slice().sort(
      (a, b) => String(a[column]).localeCompare(String(b[column]), 'en', { numeric: true, sensitivity: 'base' }) * sign
    );
  }

  /** The next free ID: one more than the highest number in use, never below EMP101. */
  function nextEmployeeId(list) {
    const highest = list.reduce((max, e) => Math.max(max, Number(String(e.id).replace('EMP', '')) || 0), 100);
    return `EMP${highest + 1}`;
  }

  /** Page maths: keeps the page number inside 1..pages. */
  function paginate(total, page, pageSize) {
    const pages = Math.max(1, Math.ceil(total / pageSize));
    const current = Math.min(Math.max(1, Math.floor(page) || 1), pages);
    return { pages, page: current, start: (current - 1) * pageSize };
  }

  function departmentCounts(list, departments = DEPARTMENTS) {
    const total = list.length || 1;
    return departments.map((department) => {
      const count = list.filter((e) => e.department === department).length;
      return { department, count, percent: Math.round((count / total) * 100) };
    });
  }

  function formatDate(iso) {
    if (!isIsoDate(iso)) return '—';
    return new Date(iso + 'T00:00:00').toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
  }

  return {
    DEPARTMENTS,
    STATUSES,
    LIMITS,
    localToday,
    isIsoDate,
    escapeHtml,
    cleanText,
    validateEmployeeInput,
    cleanEmployee,
    cleanEmployeeList,
    filterEmployees,
    sortEmployees,
    nextEmployeeId,
    paginate,
    departmentCounts,
    formatDate,
  };
});
