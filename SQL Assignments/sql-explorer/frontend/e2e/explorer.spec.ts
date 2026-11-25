import { test, expect, type Page } from '@playwright/test'

/** Collect anything the page reports as an error, so a test can assert there was none. */
function watchErrors(page: Page) {
  const errors: string[] = []
  page.on('console', (m) => {
    if (m.type() === 'error') errors.push(m.text())
  })
  page.on('pageerror', (e) => errors.push(`PAGEERROR ${e.message}`))
  return errors
}

test('the home page shows all 15 assignments with their numbers', async ({ page }) => {
  const errors = watchErrors(page)
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'SQL Explorer', level: 1 })).toBeVisible()
  await expect(page.locator('.cards .card')).toHaveCount(15)
  const org = page.locator('.cards .card', { hasText: 'Organization Database' })
  await expect(org).toContainText('org_db')
  await expect(org.locator('dd').nth(0)).toHaveText('4') // tables
  await expect(org.locator('dd').nth(1)).toHaveText('33') // rows
  expect(errors).toEqual([])
})

test('every assignment page opens on every tab without errors', async ({ page }) => {
  test.setTimeout(300_000) // 15 assignments x 7 tabs
  const errors = watchErrors(page)
  for (let n = 1; n <= 15; n++) {
    const id = String(n).padStart(2, '0')
    for (const tab of ['overview', 'schema', 'data', 'queries', 'scripts', 'negative', 'playground']) {
      await page.goto(`/a/${id}${tab === 'overview' ? '' : `?tab=${tab}`}`)
      await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
      await expect(page.locator('#tabpanel')).toBeVisible()
      await expect(page.getByRole('alert')).toHaveCount(0)
    }
  }
  expect(errors).toEqual([])
})

test('schema tab shows keys, foreign keys and the CHECK constraint', async ({ page }) => {
  await page.goto('/a/01?tab=schema')
  await expect(page.getByText('employees.dept_id → departments.dept_id')).toBeVisible()
  const projects = page.getByRole('region', { name: /^projects \d+ rows/ })
  await expect(projects.locator('.pill.key-pk').first()).toBeVisible()
  await expect(projects.getByText(/CHECK/)).toBeVisible()
})

test('data tab pages through a table', async ({ page }) => {
  await page.goto('/a/01?tab=data')
  await page.getByLabel('Table').selectOption('employees')
  await expect(page.getByRole('status').filter({ hasText: 'Page 1 of 1' })).toBeVisible()
  await expect(page.locator('.table-wrap tbody tr')).toHaveCount(10)
  await page.goto('/a/09?tab=data')
  await expect(page.getByRole('button', { name: 'Previous' })).toBeDisabled()
})

test('a query runs and shows its rows', async ({ page }) => {
  await page.goto('/a/08?tab=queries')
  await page.getByRole('button', { name: /Run query 1:/ }).click()
  const card = page.locator('.query-card').first()
  await expect(card.getByRole('status')).toContainText('1 row')
  await expect(card.locator('th')).toContainText(['total_orders', 'units_sold', 'total_revenue', 'average_order_value'])
})

test('in a DML assignment the same query shows different rows after the scripts have run', async ({ page }) => {
  await page.goto('/a/04?tab=queries')
  await page.getByRole('button', { name: /Run query 1:/ }).click()
  const card = page.locator('.query-card').first()
  await expect(card.locator('tbody tr').first()).toBeVisible()
  const before = await card.locator('tbody').innerText()

  await page.getByLabel(/after the data-changing scripts ran/).check()
  await page.getByRole('button', { name: /Run query 1:/ }).click()
  const afterCard = page.locator('.query-card').first()
  await expect(afterCard.locator('tbody tr').first()).toBeVisible()
  expect(await afterCard.locator('tbody').innerText()).not.toBe(before)
})

test('a data-changing script runs in the sandbox, shows its effect, and saves nothing', async ({ page }) => {
  await page.goto('/a/04?tab=scripts')
  const card = page.locator('.script-card', { hasText: '03_update_salaries.sql' })
  await card.getByRole('button', { name: 'Run in sandbox' }).click()
  await expect(card.getByText('nothing was saved')).toBeVisible()
  await expect(card.locator('.steps')).toContainText('rows affected')

  // the data is exactly as before: the employees table still shows the original salary of employee 1
  await page.goto('/a/04?tab=data')
  await page.getByLabel('Table').selectOption('employees')
  const firstRow = await page.locator('.table-wrap tbody tr').first().innerText()
  await page.goto('/a/04?tab=scripts')
  await page.locator('.script-card', { hasText: '03_update_salaries.sql' }).getByRole('button', { name: 'Run in sandbox' }).click()
  await expect(page.getByText('nothing was saved')).toBeVisible()
  await page.goto('/a/04?tab=data')
  await page.getByLabel('Table').selectOption('employees')
  expect(await page.locator('.table-wrap tbody tr').first().innerText()).toBe(firstRow)
})

test('a script with its own COMMIT and ROLLBACK is explained, not obeyed', async ({ page }) => {
  await page.goto('/a/05?tab=scripts')
  const card = page.locator('.script-card', { hasText: '03_inserts_updates.sql' })
  await card.getByRole('button', { name: 'Run in sandbox' }).click()
  await expect(card.getByText('COMMIT is not saved in the sandbox').first()).toBeVisible()
  await expect(card.getByText(/ROLLBACK undoes everything/).first()).toBeVisible()
})

test('the must-fail tests all fail with the expected MySQL error', async ({ page }) => {
  await page.goto('/a/15?tab=negative')
  await page.getByRole('button', { name: /Run all 9 tests/ }).click()
  await expect(page.getByRole('status').filter({ hasText: '9 of 9 failed exactly as expected' })).toBeVisible()
  await expect(page.locator('.neg.ok')).toHaveCount(9)
  await expect(page.locator('.neg.bad')).toHaveCount(0)
})

test('playground runs a SELECT, with Ctrl+Enter, and refuses to change anything', async ({ page }) => {
  await page.goto('/a/01?tab=playground')
  const editor = page.getByLabel('SQL query')
  await editor.fill('SELECT dept_name, location FROM departments ORDER BY dept_name')
  await editor.press('Control+Enter')
  await expect(page.getByRole('status').filter({ hasText: '6 rows' })).toBeVisible()

  await editor.fill('DELETE FROM employees')
  await page.getByRole('button', { name: 'Run query' }).click()
  await expect(page.getByRole('alert')).toContainText('Only SELECT')

  await editor.fill('SELECT 1; SELECT 2')
  await page.getByRole('button', { name: 'Run query' }).click()
  await expect(page.getByRole('alert')).toContainText('one statement at a time')

  await editor.fill('SELECT * FROM no_such_table')
  await page.getByRole('button', { name: 'Run query' }).click()
  await expect(page.getByRole('alert')).toContainText('Error 1146')

  // and the table is still intact
  await editor.fill('SELECT COUNT(*) AS n FROM employees')
  await page.getByRole('button', { name: 'Run query' }).click()
  await expect(page.locator('.table-wrap tbody td').first()).toHaveText('10')
})

test('a made-up assignment or page gives a clear message', async ({ page }) => {
  await page.goto('/a/99')
  await expect(page.getByRole('alert')).toContainText('No assignment')
  await page.goto('/nothing/here')
  await expect(page.getByText('That page does not exist')).toBeVisible()
})

test('the tabs work with the keyboard and the URL keeps the chosen tab', async ({ page }) => {
  await page.goto('/a/02')
  await page.getByRole('tab', { name: 'Schema' }).click()
  await expect(page).toHaveURL(/tab=schema/)
  await page.reload()
  await expect(page.getByRole('tab', { name: 'Schema' })).toHaveAttribute('aria-selected', 'true')
  await page.getByRole('tab', { name: 'Data' }).focus()
  await page.keyboard.press('Enter')
  await expect(page).toHaveURL(/tab=data/)
})

test('on a phone the page does not scroll sideways', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 760 })
  for (const path of ['/', '/a/05', '/a/05?tab=schema', '/a/08?tab=queries', '/a/05?tab=negative', '/a/01?tab=playground']) {
    await page.goto(path)
    await page.waitForLoadState('networkidle')
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
    expect(overflow, path).toBeLessThanOrEqual(1)
  }
})
