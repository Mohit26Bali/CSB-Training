import { expect, test, type Page } from '@playwright/test'
import { createCustomer, deposit, grouped, inr, loginAs, openAccount, transfer, withdraw, type Customer } from './helpers'

/**
 * The transaction-history filters in the browser. The history is built through the API (fast), then the filter panel
 * is exercised on the Transactions page. Ledger of the test account, oldest to newest:
 *   Deposit 1 000, Withdrawal 200, Transfer out 3 000, Deposit 50 (after a failed withdrawal of 999 999).
 */
async function seedHistory(request: Parameters<typeof createCustomer>[0]) {
  const customer = await createCustomer(request)
  const { accountNumber } = await openAccount(request, customer, 'SAVINGS', 10000) // opening deposit 10 000
  const other = await openAccount(request, await createCustomer(request), 'SAVINGS', 0)
  await deposit(request, customer, accountNumber, 1000)
  await withdraw(request, customer, accountNumber, 200)
  await transfer(request, customer, accountNumber, other.accountNumber, 3000)
  expect(await withdraw(request, customer, accountNumber, 999999)).toBe(422) // recorded as a FAILED row
  await deposit(request, customer, accountNumber, 50)
  return { customer, accountNumber, other }
}

async function openFilters(page: Page) {
  await page.getByRole('button', { name: /^Filters/ }).click()
}

test.describe('transaction history filters', () => {
  let customer: Customer
  let accountNumber: string

  test.beforeEach(async ({ page, request }) => {
    ;({ customer, accountNumber } = await seedHistory(request))
    await loginAs(page, customer)
    await page.goto('/transactions')
    await expect(page.getByText('6 transactions')).toBeVisible()
  })

  const rowsOf = (page: Page) => page.locator('ul.divide-y > li')

  test('without filters every ledger line is listed, newest first', async ({ page }) => {
    const rows = rowsOf(page)
    await expect(rows).toHaveCount(6)
    await expect(rows.first()).toContainText('Deposit') // the 50 deposit is the newest
    await expect(rows.first()).toContainText('+' + inr(50))
  })

  test('filter by type', async ({ page }) => {
    await openFilters(page)
    await page.getByLabel('Type').selectOption('WITHDRAWAL')
    await expect(page.getByText('2 transactions')).toBeVisible() // 200 (success) + 999 999 (failed)
    await expect(rowsOf(page)).toHaveCount(2)
    await expect(rowsOf(page).filter({ hasText: 'Deposit' })).toHaveCount(0)

    await page.getByLabel('Type').selectOption('TRANSFER')
    await expect(page.getByText('1 transaction', { exact: true })).toBeVisible()
    await expect(rowsOf(page)).toHaveCount(1)
    await expect(rowsOf(page).first()).toContainText('Transfer to')
  })

  test('filter by status shows the failed attempt with its reason', async ({ page }) => {
    await openFilters(page)
    await page.getByLabel('Status').selectOption('FAILED')

    await expect(page.getByText('1 transaction', { exact: true })).toBeVisible()
    await expect(page.getByText('Transaction Failed: Insufficient balance.')).toBeVisible()
    await expect(page.getByText(inr(999999))).toBeVisible()
  })

  test('filter by money in / out', async ({ page }) => {
    await openFilters(page)
    await page.getByLabel('Money').selectOption('CREDIT')
    await expect(page.getByText('3 transactions')).toBeVisible() // 10 000, 1 000, 50
    await page.getByLabel('Money').selectOption('DEBIT')
    await expect(page.getByText('3 transactions')).toBeVisible() // 200, 3 000, failed 999 999
  })

  test('filter by amount range applies on Enter and on leaving the box', async ({ page }) => {
    await openFilters(page)
    await page.getByLabel('Min amount (₹)').fill('1000')
    await page.getByLabel('Min amount (₹)').press('Enter')
    await expect(page.getByText('4 transactions')).toBeVisible() // 10 000, 1 000, 3 000, 999 999

    await page.getByLabel('Max amount (₹)').fill('5000')
    await page.getByLabel('Max amount (₹)').blur()
    await expect(page.getByText('2 transactions')).toBeVisible() // 1 000 and 3 000 (10 000 and 999 999 are above the max)
  })

  test('inconsistent filters are explained and not sent', async ({ page }) => {
    await openFilters(page)
    await page.getByLabel('Min amount (₹)').fill('500')
    await page.getByLabel('Max amount (₹)').fill('100')
    await page.getByLabel('Max amount (₹)').blur()
    await expect(page.getByRole('alert')).toContainText('Minimum must not be above maximum')
    // the minimum alone (500) was valid and applied when the box lost focus; the conflicting maximum (100) was not sent
    await expect(page.getByText('4 transactions')).toBeVisible() // 1 000, 3 000, 10 000 and the failed 999 999

    await page.getByLabel('Min amount (₹)').fill('abc')
    await page.getByLabel('Min amount (₹)').blur()
    await expect(page.getByRole('alert')).toContainText('Enter amounts as positive numbers')
  })

  test('date range: presets, custom dates and the empty result', async ({ page }) => {
    // everything happened today, so "Last 7 days" keeps all six
    await page.getByRole('button', { name: 'Last 7 days' }).click()
    await expect(page.getByText('6 transactions')).toBeVisible()
    await expect(page.getByRole('button', { name: /^Filters/ })).toContainText('2') // from + to

    // a range in the past matches nothing
    await openFilters(page)
    await page.getByLabel('From', { exact: true }).fill('2020-01-01')
    await page.getByLabel('To', { exact: true }).fill('2020-01-31')
    await expect(page.getByText('No transactions match these filters.')).toBeVisible()

    // clearing restores the full list
    await page.getByRole('button', { name: 'Clear all' }).click()
    await expect(page.getByText('6 transactions')).toBeVisible()
  })

  test('filters combine, and "Clear all" resets them', async ({ page }) => {
    await openFilters(page)
    await page.getByLabel('Type').selectOption('DEPOSIT')
    await page.getByLabel('Money').selectOption('CREDIT')
    await page.getByLabel('Min amount (₹)').fill('100')
    await page.getByLabel('Min amount (₹)').press('Enter')
    await expect(page.getByText('2 transactions')).toBeVisible() // 10 000 and 1 000 (the 50 deposit is below 100)
    await expect(page.getByRole('button', { name: /^Filters/ })).toContainText('3')

    await page.getByRole('button', { name: 'Clear all' }).click()
    await expect(page.getByText('6 transactions')).toBeVisible()
  })

  test('the same filters work on the account details page', async ({ page }) => {
    await page.goto(`/accounts/${accountNumber}`)
    await expect(page.getByText(grouped(accountNumber)).first()).toBeVisible()
    await openFilters(page)
    await page.getByLabel('Type').selectOption('TRANSFER')
    await expect(page.getByText('1 transaction', { exact: true })).toBeVisible()
  })
})
