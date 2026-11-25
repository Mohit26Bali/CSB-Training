// Employee Management Dashboard: state, rendering and event handling (plain JavaScript + Bootstrap).
(function () {
  'use strict';

  const logic = PeopleHubLogic;
  const { escapeHtml, formatDate } = logic;
  const STORAGE_KEY = 'peoplehub-employees';
  const THEME_KEY = 'peoplehub-theme';
  const PAGE_SIZE = 6;

  const STATUS_BADGES = { Active: 'text-bg-success', 'On Leave': 'text-bg-warning', Inactive: 'text-bg-secondary' };

  // ---------- State ----------
  const state = {
    employees: loadEmployees(),
    search: '',
    department: '',
    status: '',
    sortBy: 'id',
    sortDir: 'asc',
    page: 1,
    pendingDeleteId: null,
  };

  // ---------- DOM ----------
  const $ = (id) => document.getElementById(id);
  const employeeModal = new bootstrap.Modal($('employeeModal'));
  const detailsModal = new bootstrap.Modal($('detailsModal'));
  const deleteModal = new bootstrap.Modal($('deleteModal'));
  const form = $('employeeForm');

  // ---------- Storage (challenge: persist in localStorage) ----------
  function loadEmployees() {
    try {
      // Saved data can be changed by anyone with developer tools, so every record is checked and cleaned.
      const saved = JSON.parse(localStorage.getItem(STORAGE_KEY));
      if (saved !== null) return logic.cleanEmployeeList(saved, SAMPLE_EMPLOYEES);
    } catch (error) {
      // unreadable data: fall back to the sample data
    }
    return SAMPLE_EMPLOYEES.slice();
  }

  function saveEmployees() {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(state.employees));
    } catch (error) {
      // storage unavailable: changes last for this visit only
    }
  }

  // ---------- Helpers ----------
  const today = () => logic.localToday();

  function showAlert(message, type) {
    const alert = document.createElement('div');
    alert.className = `alert alert-${type} alert-dismissible fade show`;
    alert.setAttribute('role', 'alert');
    alert.innerHTML = `${escapeHtml(message)}<button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>`;
    $('alertArea').replaceChildren(alert);
    setTimeout(() => bootstrap.Alert.getOrCreateInstance(alert).close(), 4000);
  }

  // ---------- Derived data ----------
  /** Search, department and status filters work together; then the result is sorted. */
  function visibleEmployees() {
    const list = logic.filterEmployees(state.employees, state);
    return logic.sortEmployees(list, state.sortBy, state.sortDir);
  }

  // ---------- Rendering ----------
  function renderKpis() {
    const count = (status) => state.employees.filter((e) => e.status === status).length;
    const kpis = [
      { label: 'Total Employees', value: state.employees.length, icon: 'bi-people', color: 'primary' },
      { label: 'Active Employees', value: count('Active'), icon: 'bi-person-check', color: 'success' },
      { label: 'On Leave', value: count('On Leave'), icon: 'bi-calendar2-x', color: 'warning' },
      { label: 'Open Positions', value: OPEN_POSITIONS, icon: 'bi-briefcase', color: 'info' },
    ];
    $('kpiCards').innerHTML = kpis.map((kpi) => `
      <div class="col-12 col-sm-6 col-xl-3">
        <div class="card h-100 shadow-sm border-0 border-start border-4 border-${kpi.color}">
          <div class="card-body d-flex align-items-center gap-3">
            <div class="kpi-icon rounded-3 bg-${kpi.color}-subtle text-${kpi.color}-emphasis d-flex align-items-center justify-content-center fs-4 flex-shrink-0">
              <i class="bi ${kpi.icon}"></i>
            </div>
            <div>
              <div class="text-body-secondary small">${kpi.label}</div>
              <div class="fs-3 fw-bold" data-kpi="${kpi.label}">${kpi.value}</div>
            </div>
          </div>
        </div>
      </div>`).join('');
  }

  function renderTable() {
    const all = visibleEmployees();
    const { pages, page, start } = logic.paginate(all.length, state.page, PAGE_SIZE);
    state.page = page;
    const rows = all.slice(start, start + PAGE_SIZE);

    $('employeeRows').innerHTML = rows.length === 0
      ? `<tr><td colspan="7" class="text-center text-body-secondary py-4"><i class="bi bi-search me-1"></i>No employees match the search and filters.</td></tr>`
      : rows.map((e) => `
        <tr data-id="${escapeHtml(e.id)}">
          <td class="text-nowrap fw-semibold">${escapeHtml(e.id)}</td>
          <td class="text-nowrap">${escapeHtml(e.name)}</td>
          <td>${escapeHtml(e.department)}</td>
          <td class="text-nowrap">${escapeHtml(e.role)}</td>
          <td><span class="badge rounded-pill ${STATUS_BADGES[e.status] || 'text-bg-secondary'}">${escapeHtml(e.status)}</span></td>
          <td class="text-nowrap">${formatDate(e.joiningDate)}</td>
          <td class="text-end text-nowrap">
            <div class="btn-group btn-group-sm" role="group" aria-label="Actions for ${escapeHtml(e.name)}">
              <button type="button" class="btn btn-outline-primary" data-action="view" title="View" aria-label="View ${escapeHtml(e.name)}"><i class="bi bi-eye"></i></button>
              <button type="button" class="btn btn-outline-secondary" data-action="edit" title="Edit" aria-label="Edit ${escapeHtml(e.name)}"><i class="bi bi-pencil"></i></button>
              <button type="button" class="btn btn-outline-danger" data-action="delete" title="Delete" aria-label="Delete ${escapeHtml(e.name)}"><i class="bi bi-trash"></i></button>
            </div>
          </td>
        </tr>`).join('');

    $('resultCount').textContent = `${all.length} of ${state.employees.length} employees`;
    $('pageInfo').textContent = all.length ? `Showing ${start + 1}–${start + rows.length} of ${all.length}` : 'Showing 0 of 0';
    renderPagination(pages);
    renderSortIcons();
  }

  function renderPagination(pages) {
    const item = (label, page, { disabled = false, active = false, aria = label } = {}) => `
      <li class="page-item${disabled ? ' disabled' : ''}${active ? ' active' : ''}">
        <button type="button" class="page-link" data-page="${page}" aria-label="${aria}" ${active ? 'aria-current="page"' : ''}>${label}</button>
      </li>`;
    let html = item('&laquo;', state.page - 1, { disabled: state.page === 1, aria: 'Previous page' });
    for (let p = 1; p <= pages; p += 1) html += item(String(p), p, { active: p === state.page, aria: `Page ${p}` });
    html += item('&raquo;', state.page + 1, { disabled: state.page === pages, aria: 'Next page' });
    $('pagination').innerHTML = html;
  }

  function renderSortIcons() {
    document.querySelectorAll('.sort').forEach((button) => {
      const active = button.dataset.sort === state.sortBy;
      button.querySelector('.bi').className = 'bi ' + (!active ? 'bi-arrow-down-up text-body-tertiary'
        : state.sortDir === 'asc' ? 'bi-sort-up' : 'bi-sort-down');
      button.closest('th').setAttribute('aria-sort', active ? (state.sortDir === 'asc' ? 'ascending' : 'descending') : 'none');
    });
  }

  function renderDepartmentChart() {
    $('departmentChart').innerHTML = logic.departmentCounts(state.employees).map(({ department, count: n, percent }) => {
      return `
        <div class="row g-2 align-items-center">
          <div class="col-4 col-sm-3 col-lg-2 small">${department}</div>
          <div class="col">
            <div class="progress" role="progressbar" aria-label="${department}" aria-valuenow="${percent}" aria-valuemin="0" aria-valuemax="100">
              <div class="progress-bar" style="width: ${percent}%"></div>
            </div>
          </div>
          <div class="col-auto small text-body-secondary">${n}</div>
        </div>`;
    }).join('');
  }

  function renderAll() {
    renderKpis();
    renderTable();
    renderDepartmentChart();
  }

  // ---------- Add / edit form (Bootstrap validation styles) ----------
  function openForm(employee) {
    form.reset();
    form.classList.remove('was-validated');
    $('editId').value = employee ? employee.id : '';
    $('employeeModalTitle').textContent = employee ? `Edit ${employee.name}` : 'Add Employee';
    $('saveEmployee').textContent = employee ? 'Save changes' : 'Save employee';
    if (employee) {
      $('nameInput').value = employee.name;
      $('departmentInput').value = employee.department;
      $('roleInput').value = employee.role;
      $('statusInput').value = employee.status;
      $('joiningInput').value = employee.joiningDate;
      $('emailInput').value = employee.email || '';
    } else {
      $('statusInput').value = 'Active';
    }
    $('joiningInput').max = today();
    employeeModal.show();
  }

  form.addEventListener('submit', (event) => {
    event.preventDefault();
    // Custom rules on top of the HTML ones: no blank names/roles, no future joining date.
    ['nameInput', 'roleInput'].forEach((id) => $(id).setCustomValidity($(id).value.trim().length >= 2 ? '' : 'Too short'));
    const joining = $('joiningInput');
    const future = joining.value && joining.value > today();
    joining.setCustomValidity(future ? 'Future date' : '');
    $('joiningFeedback').textContent = future ? 'The joining date cannot be in the future.' : 'Select the joining date.';

    form.classList.add('was-validated');
    if (!form.checkValidity()) return;

    const data = {
      name: logic.cleanText($('nameInput').value),
      department: $('departmentInput').value,
      role: logic.cleanText($('roleInput').value),
      status: $('statusInput').value,
      joiningDate: joining.value,
      email: $('emailInput').value.trim(),
    };
    // The same rules again in code, so a bad value can never be saved even if the form checks were bypassed.
    if (!logic.validateEmployeeInput(data, today()).valid) return;
    const editId = $('editId').value;
    if (editId) {
      state.employees = state.employees.map((e) => (e.id === editId ? { ...e, ...data } : e));
      showAlert(`${data.name}'s details were updated.`, 'success');
    } else {
      const id = logic.nextEmployeeId(state.employees);
      state.employees = [...state.employees, { id, ...data }];
      showAlert(`${data.name} was added as ${id}.`, 'success');
    }
    saveEmployees();
    employeeModal.hide();
    renderAll();
  });

  // ---------- Row actions: View / Edit / Delete ----------
  $('employeeRows').addEventListener('click', (event) => {
    const button = event.target.closest('[data-action]');
    if (!button) return;
    const employee = state.employees.find((e) => e.id === button.closest('tr').dataset.id);
    if (!employee) return;

    if (button.dataset.action === 'view') {
      $('detailsTitle').textContent = employee.name;
      $('detailsBody').innerHTML = `
        <dl class="row mb-0">
          <dt class="col-5">Employee ID</dt><dd class="col-7">${escapeHtml(employee.id)}</dd>
          <dt class="col-5">Department</dt><dd class="col-7">${escapeHtml(employee.department)}</dd>
          <dt class="col-5">Role</dt><dd class="col-7">${escapeHtml(employee.role)}</dd>
          <dt class="col-5">Status</dt><dd class="col-7"><span class="badge rounded-pill ${STATUS_BADGES[employee.status] || 'text-bg-secondary'}">${escapeHtml(employee.status)}</span></dd>
          <dt class="col-5">Joining date</dt><dd class="col-7">${formatDate(employee.joiningDate)}</dd>
          <dt class="col-5">Email</dt><dd class="col-7 text-break">${escapeHtml(employee.email || '—')}</dd>
        </dl>`;
      detailsModal.show();
    } else if (button.dataset.action === 'edit') {
      openForm(employee);
    } else {
      state.pendingDeleteId = employee.id;
      $('deleteBody').textContent = `${employee.name} (${employee.id}) will be removed from the list.`;
      deleteModal.show();
    }
  });

  $('confirmDelete').addEventListener('click', () => {
    const employee = state.employees.find((e) => e.id === state.pendingDeleteId);
    deleteModal.hide();
    if (!employee) return;
    state.employees = state.employees.filter((e) => e.id !== state.pendingDeleteId);
    state.pendingDeleteId = null;
    saveEmployees();
    showAlert(`${employee.name} was deleted.`, 'danger');
    renderAll();
  });

  // ---------- Toolbar: search, filters, sorting, paging ----------
  $('searchInput').addEventListener('input', (e) => { state.search = e.target.value; state.page = 1; renderTable(); });
  $('departmentFilter').addEventListener('change', (e) => { state.department = e.target.value; state.page = 1; renderTable(); });
  $('statusFilter').addEventListener('change', (e) => { state.status = e.target.value; state.page = 1; renderTable(); });
  $('clearFilters').addEventListener('click', () => {
    state.search = state.department = state.status = '';
    $('searchInput').value = $('departmentFilter').value = $('statusFilter').value = '';
    state.page = 1;
    renderTable();
  });

  document.querySelectorAll('.sort').forEach((button) => button.addEventListener('click', () => {
    const key = button.dataset.sort;
    state.sortDir = state.sortBy === key && state.sortDir === 'asc' ? 'desc' : 'asc';
    state.sortBy = key;
    renderTable();
  }));

  $('pagination').addEventListener('click', (event) => {
    const button = event.target.closest('[data-page]');
    if (!button || button.closest('.disabled')) return;
    state.page = Number(button.dataset.page);
    renderTable();
  });

  $('addEmployeeButton').addEventListener('click', () => openForm(null));

  // ---------- Challenges: theme toggle and collapsible sidebar ----------
  function applyTheme(theme) {
    document.documentElement.setAttribute('data-bs-theme', theme);
    const button = $('themeToggle');
    button.innerHTML = `<i class="bi ${theme === 'dark' ? 'bi-sun' : 'bi-moon-stars'}"></i>`;
    button.setAttribute('aria-label', theme === 'dark' ? 'Switch to light theme' : 'Switch to dark theme');
  }

  $('themeToggle').addEventListener('click', () => {
    const next = document.documentElement.getAttribute('data-bs-theme') === 'dark' ? 'light' : 'dark';
    applyTheme(next);
    try { localStorage.setItem(THEME_KEY, next); } catch (error) { /* not saved */ }
  });

  $('sidebarCollapse').addEventListener('click', () => {
    const collapsed = $('sidebarColumn').classList.toggle('sidebar--collapsed');
    $('sidebarCollapse').setAttribute('aria-label', collapsed ? 'Expand sidebar' : 'Collapse sidebar');
  });

  // Close the offcanvas sidebar after choosing a link on small screens
  document.querySelectorAll('#sidebar .nav-link').forEach((link) => link.addEventListener('click', () => {
    bootstrap.Offcanvas.getInstance($('sidebar'))?.hide();
  }));

  // ---------- Start ----------
  const options = (values) => values.map((v) => `<option value="${v}">${v}</option>`).join('');
  $('departmentFilter').insertAdjacentHTML('beforeend', options(DEPARTMENTS));
  $('statusFilter').insertAdjacentHTML('beforeend', options(STATUSES));
  $('departmentInput').insertAdjacentHTML('beforeend', options(DEPARTMENTS));
  $('statusInput').innerHTML = options(STATUSES);

  let savedTheme = 'light';
  try { savedTheme = localStorage.getItem(THEME_KEY) || 'light'; } catch (error) { /* default */ }
  applyTheme(savedTheme);
  renderAll();
})();
