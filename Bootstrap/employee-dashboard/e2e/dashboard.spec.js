const { test, expect } = require('@playwright/test');
const path = require('node:path');
const { pathToFileURL } = require('node:url');

const PAGE = pathToFileURL(path.join(__dirname, '..', 'index.html')).href;

let errors;
test.beforeEach(async ({ page }) => {
  errors = [];
  page.on('pageerror', (e) => errors.push(e.message));
  page.on('console', (m) => m.type() === 'error' && errors.push(m.text()));
  await page.goto(PAGE);
  await page.evaluate(() => localStorage.clear());
  await page.reload();
});
test.afterEach(() => expect(errors).toEqual([]));

const rows = (page) => page.locator('#employeeRows tr');
const kpi = (page, label) => page.locator(`[data-kpi="${label}"]`);

async function fillForm(page, v) {
  await page.fill('#nameInput', v.name ?? '');
  await page.selectOption('#departmentInput', v.department ?? '');
  await page.fill('#roleInput', v.role ?? '');
  if (v.status) await page.selectOption('#statusInput', v.status);
  await page.fill('#joiningInput', v.joiningDate ?? '');
  await page.fill('#emailInput', v.email ?? '');
}

test('starts with the sample data: KPIs, first page of six rows', async ({ page }) => {
  await expect(kpi(page, 'Total Employees')).toHaveText('12');
  await expect(kpi(page, 'Active Employees')).toHaveText('8');
  await expect(kpi(page, 'On Leave')).toHaveText('2');
  await expect(kpi(page, 'Open Positions')).toHaveText('6');
  await expect(rows(page)).toHaveCount(6);
  await expect(page.locator('#resultCount')).toHaveText('12 of 12 employees');
});

test('search, filters, combination, empty state and clear', async ({ page }) => {
  await page.fill('#searchInput', 'anita');
  await expect(rows(page)).toHaveCount(1);
  await page.fill('#searchInput', 'EMP102');
  await expect(rows(page)).toHaveCount(1);
  await page.click('#clearFilters');
  await page.selectOption('#departmentFilter', 'Engineering');
  await expect(page.locator('#resultCount')).toHaveText('5 of 12 employees');
  await page.selectOption('#statusFilter', 'On Leave');
  await expect(page.locator('#employeeRows')).toContainText('No employees match');
  await page.click('#clearFilters');
  await expect(page.locator('#resultCount')).toHaveText('12 of 12 employees');
});

test('sorting and paging', async ({ page }) => {
  await page.click('.sort[data-sort="name"]');
  await expect(rows(page).first()).toContainText('Anita Sharma');
  await page.click('.sort[data-sort="name"]');
  await expect(rows(page).first()).toContainText('Vikram Singh');
  await page.getByRole('button', { name: 'Page 2' }).click();
  await expect(page.locator('#pageInfo')).toHaveText('Showing 7–12 of 12');
});

test('the form refuses bad input and shows why', async ({ page }) => {
  await page.click('#addEmployeeButton');
  await page.click('#saveEmployee');
  await expect(page.locator('#employeeForm')).toHaveClass(/was-validated/);
  await expect(rows(page)).toHaveCount(6); // nothing added

  const future = new Date(Date.now() + 3 * 86400000).toISOString().slice(0, 10);
  await fillForm(page, { name: 'Future Joiner', department: 'HR', role: 'Recruiter', joiningDate: future });
  await page.click('#saveEmployee');
  await expect(page.locator('#joiningFeedback')).toHaveText('The joining date cannot be in the future.');
  await expect(kpi(page, 'Total Employees')).toHaveText('12');

  await fillForm(page, { name: ' ', department: 'HR', role: 'Recruiter', joiningDate: '2024-01-01' });
  await page.click('#saveEmployee');
  await expect(kpi(page, 'Total Employees')).toHaveText('12');
});

test('add, edit and delete an employee, and they survive a reload', async ({ page }) => {
  await page.click('#addEmployeeButton');
  await fillForm(page, { name: 'Zoya Khan', department: 'Finance', role: 'Auditor', status: 'On Leave', joiningDate: '2024-03-04', email: 'zoya@peoplehub.com' });
  await page.click('#saveEmployee');
  await expect(page.locator('#alertArea')).toContainText('Zoya Khan was added as EMP113');
  await expect(kpi(page, 'Total Employees')).toHaveText('13');
  await expect(kpi(page, 'On Leave')).toHaveText('3');

  await page.reload();
  await expect(kpi(page, 'Total Employees')).toHaveText('13');

  await page.fill('#searchInput', 'zoya');
  await page.locator('#employeeRows [data-action="edit"]').click();
  await page.fill('#roleInput', 'Senior Auditor');
  await page.click('#saveEmployee');
  await expect(rows(page).first()).toContainText('Senior Auditor');
  await expect(page.locator('#employeeModal')).toBeHidden(); // wait for the close animation before opening the next one

  await page.locator('#employeeRows [data-action="view"]').click();
  await expect(page.locator('#detailsBody')).toContainText('zoya@peoplehub.com');
  await page.waitForTimeout(450); // let the open animation finish; Bootstrap ignores Escape while it is running
  await page.keyboard.press('Escape');
  await expect(page.locator('#detailsModal')).toBeHidden();

  await page.locator('#employeeRows [data-action="delete"]').click();
  await page.click('#confirmDelete');
  await expect(page.locator('#alertArea')).toContainText('Zoya Khan was deleted');
  await expect(kpi(page, 'Total Employees')).toHaveText('12');
});

test('script typed into a field is shown as text and never runs', async ({ page }) => {
  await page.click('#addEmployeeButton');
  await fillForm(page, { name: '<img src=x onerror=window.__hacked=1>', department: 'HR', role: '<b>bold</b>', joiningDate: '2024-03-04' });
  await page.click('#saveEmployee');
  await page.fill('#searchInput', 'img src');
  await expect(rows(page).first()).toContainText('<img src=x onerror=window.__hacked=1>');
  expect(await page.evaluate(() => window.__hacked)).toBeUndefined();
});

test('tampered saved data is cleaned, not rendered', async ({ page }) => {
  await page.evaluate(() =>
    localStorage.setItem(
      'peoplehub-employees',
      JSON.stringify([
        { id: 'EMP101', name: 'Good Person', department: 'HR', role: 'Recruiter', status: 'Active', joiningDate: '2022-01-01' },
        { id: '"><img src=x onerror=window.__hacked=1>', name: 'Evil', department: 'HR', role: 'x', status: 'Active', joiningDate: '2022-01-01' },
        { id: 'EMP102', name: 'Bad Status', department: 'HR', role: 'Recruiter', status: '<img src=x onerror=window.__hacked=1>', joiningDate: '2022-01-01' },
      ])
    )
  );
  await page.reload();
  await expect(rows(page)).toHaveCount(1);
  await expect(rows(page).first()).toContainText('Good Person');
  expect(await page.evaluate(() => window.__hacked)).toBeUndefined();

  await page.evaluate(() => localStorage.setItem('peoplehub-employees', '{not json'));
  await page.reload();
  await expect(kpi(page, 'Total Employees')).toHaveText('12'); // back to the sample data
});

test('the theme choice is remembered', async ({ page }) => {
  await page.click('#themeToggle');
  await expect(page.locator('html')).toHaveAttribute('data-bs-theme', 'dark');
  await page.reload();
  await expect(page.locator('html')).toHaveAttribute('data-bs-theme', 'dark');
});

for (const [name, width] of [['desktop', 1400], ['tablet', 820], ['mobile', 390]]) {
  test(`${name} (${width}px): no sideways page scroll, menu behaves`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
    expect(overflow).toBeLessThanOrEqual(0);
    const sidebarVisible = await page.locator('#sidebar').isVisible();
    expect(sidebarVisible).toBe(width >= 992);
    if (width < 992) {
      await page.click('[data-bs-target="#sidebar"]');
      await expect(page.locator('#sidebar')).toBeVisible();
    }
  });
}
