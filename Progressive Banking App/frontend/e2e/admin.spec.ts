import { expect, test } from '@playwright/test'
import { createCustomer, deposit, grouped, inr, login, loginAs, openAccount } from './helpers'

/**
 * The administrator's screens, and what an administrator's decision does to a customer. Two browser contexts are used
 * together: one signed in as the seeded admin, one as a freshly registered customer.
 */
test.describe('administrator', () => {
  test('the overview summarises the bank', async ({ page, request }) => {
    const customer = await createCustomer(request)
    await openAccount(request, customer, 'CURRENT', 2500)
    await login(page, 'admin', 'admin12345')

    await expect(page.getByRole('heading', { name: 'Overview' })).toBeVisible()
    await expect(page.getByText('Total deposits held')).toBeVisible()
    await expect(page.getByText('Total accounts')).toBeVisible()
    await expect(page.getByText('Accounts by status')).toBeVisible()
    await expect(page.getByText('Accounts by type')).toBeVisible()
    await expect(page.getByText('Recently opened')).toBeVisible()
  })

  test('search and filters narrow the account list', async ({ page, request }) => {
    const customer = await createCustomer(request)
    const { accountNumber } = await openAccount(request, customer, 'CURRENT', 100)
    await login(page, 'admin', 'admin12345')
    await page.goto('/admin/accounts')

    const search = page.getByLabel('Search accounts')
    await search.fill(customer.fullName)
    const rows = page.locator('ul.divide-y > li')
    await expect(rows).toHaveCount(1)
    await expect(rows.first()).toContainText(grouped(accountNumber))

    await search.fill(accountNumber)
    await expect(rows).toHaveCount(1)

    await page.getByLabel('Filter by type').selectOption('SAVINGS')
    await expect(page.getByText('No accounts match')).toBeVisible() // the account is CURRENT

    await page.getByLabel('Filter by type').selectOption('ALL')
    await page.getByLabel('Filter by status').selectOption('BLOCKED')
    await expect(page.getByText('No accounts match')).toBeVisible()
  })

  test('blocking an account stops the customer; activating it again restores access', async ({ browser, page, request }) => {
    const customer = await createCustomer(request)
    const { accountNumber } = await openAccount(request, customer, 'SAVINGS', 1000)

    // the customer in their own browser context
    const customerContext = await browser.newContext()
    const customerPage = await customerContext.newPage()
    await loginAs(customerPage, customer)
    await customerPage.goto(`/accounts/${accountNumber}`)
    await expect(customerPage.getByRole('button', { name: 'Deposit' })).toBeEnabled()

    // the admin blocks the account
    await login(page, 'admin', 'admin12345')
    await page.goto('/admin/accounts')
    await page.getByLabel('Search accounts').fill(accountNumber)
    const row = page.locator('ul.divide-y > li', { hasText: customer.fullName })
    await row.getByRole('combobox', { name: /Change status/ }).selectOption({ label: 'Block' })
    const confirm = page.getByRole('dialog', { name: 'Block account?' })
    await expect(confirm.getByText(/will not be able to deposit, withdraw, transfer/)).toBeVisible()
    await confirm.getByRole('button', { name: 'Block' }).click()
    await expect(page.getByText(`is now blocked`)).toBeVisible()
    await expect(row).toContainText('Blocked')

    // the customer now sees a warning and cannot move money
    await customerPage.reload()
    await expect(customerPage.getByText('This account is blocked')).toBeVisible()
    await expect(customerPage.getByRole('button', { name: 'Deposit' })).toBeDisabled()
    await customerPage.goto('/transfer')
    await expect(customerPage.getByText('You need an active account to transfer money')).toBeVisible()

    // the admin activates it again
    await row.getByRole('combobox', { name: /Change status/ }).selectOption({ label: 'Activate' })
    await page.getByRole('dialog', { name: 'Activate account?' }).getByRole('button', { name: 'Activate' }).click()
    await expect(row).toContainText('Active')

    await customerPage.goto(`/accounts/${accountNumber}`)
    await expect(customerPage.getByRole('button', { name: 'Deposit' })).toBeEnabled()
    await customerContext.close()
  })

  test('the admin can read an account’s history but has no way to move money', async ({ page, request }) => {
    const customer = await createCustomer(request)
    const { accountNumber } = await openAccount(request, customer, 'SAVINGS', 1000)
    await deposit(request, customer, accountNumber, 250)
    await login(page, 'admin', 'admin12345')
    await page.goto('/admin/accounts')
    await page.getByLabel('Search accounts').fill(accountNumber)
    await expect(page.locator('ul.divide-y > li')).toHaveCount(1) // the search is debounced; wait for it

    await page.getByRole('button', { name: 'History' }).click()
    const dialog = page.getByRole('dialog', { name: 'Transaction history' })
    await expect(dialog.getByText('+' + inr(250))).toBeVisible()
    await expect(dialog.getByText('+' + inr(1000))).toBeVisible()
    await expect(dialog.getByRole('button', { name: /Deposit|Withdraw/ })).toHaveCount(0)
  })
})
