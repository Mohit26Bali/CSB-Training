import { expect, test } from '@playwright/test'
import { createCustomer, grouped, inr, loginAs, openAccount } from './helpers'

/**
 * The customer's money journey through the real UI and the real backend: open accounts, deposit, withdraw, transfer
 * (including the error cases) and read the history. Each test starts from a fresh customer made through the API.
 */
test.describe('banking operations', () => {
  test('open an account with an opening deposit from the dashboard', async ({ page, request }) => {
    const customer = await createCustomer(request)
    await loginAs(page, customer)

    await page.getByRole('button', { name: 'New account' }).click()
    const dialog = page.getByRole('dialog', { name: 'Open a new account' })
    await dialog.getByRole('radio', { name: /Current account/ }).click()
    await dialog.getByLabel('Opening deposit (optional)').fill('10000')
    await dialog.getByRole('button', { name: 'Open account' }).click()

    // lands on the new account's page
    await expect(page).toHaveURL(/\/accounts\/\d{10}/)
    await expect(page.getByText('Current account').first()).toBeVisible()
    await expect(page.getByText(inr(10000)).first()).toBeVisible()
    await expect(page.getByText('Opening deposit')).toBeVisible() // first ledger line
  })

  test('deposit and withdraw update the balance and the history', async ({ page, request }) => {
    const customer = await createCustomer(request)
    const { accountNumber } = await openAccount(request, customer, 'SAVINGS', 10000)
    await loginAs(page, customer)
    await page.goto(`/accounts/${accountNumber}`)

    await page.getByRole('button', { name: 'Deposit' }).click()
    const deposit = page.getByRole('dialog', { name: 'Deposit money' })
    await deposit.getByLabel('Amount').fill('2500')
    await deposit.getByLabel('Note (optional)').fill('Bonus')
    await deposit.getByRole('button', { name: 'Deposit' }).click()
    await expect(page.getByText(`${inr(2500)} deposited. New balance ${inr(12500)}`)).toBeVisible()

    await page.getByRole('button', { name: 'Withdraw' }).click()
    const withdraw = page.getByRole('dialog', { name: 'Withdraw money' })
    await withdraw.getByLabel('Amount').fill('500')
    await withdraw.getByRole('button', { name: 'Withdraw' }).click()
    await expect(page.getByText(`${inr(500)} withdrawn. New balance ${inr(12000)}`)).toBeVisible()

    // balance on the card and both ledger lines
    await expect(page.getByLabel(/Savings account .* balance/)).toContainText(inr(12000))
    await expect(page.getByText('Bonus')).toBeVisible()
    await expect(page.getByText('+' + inr(2500))).toBeVisible()
    await expect(page.getByText('−' + inr(500))).toBeVisible()
  })

  test('the amount is validated and an overdraw is stopped with a clear message', async ({ page, request }) => {
    const customer = await createCustomer(request)
    const { accountNumber } = await openAccount(request, customer, 'SAVINGS', 1000)
    await loginAs(page, customer)
    await page.goto(`/accounts/${accountNumber}`)

    await page.getByRole('button', { name: 'Withdraw' }).click()
    const dialog = page.getByRole('dialog', { name: 'Withdraw money' })
    const submit = dialog.getByRole('button', { name: 'Withdraw' })

    await submit.click()
    await expect(dialog.getByText('Enter an amount')).toBeVisible()
    await dialog.getByLabel('Amount').fill('0')
    await submit.click()
    await expect(dialog.getByText('Amount must be greater than zero')).toBeVisible()
    await dialog.getByLabel('Amount').fill('12.345')
    await submit.click()
    await expect(dialog.getByText('Use numbers only, with at most 2 decimals')).toBeVisible()
    await dialog.getByLabel('Amount').fill('1000.01')
    await submit.click()
    await expect(dialog.getByText(`Amount exceeds your available balance of ${inr(1000)}`)).toBeVisible()

    await dialog.getByRole('button', { name: 'Cancel' }).click()
    await expect(page.getByLabel(/Savings account .* balance/)).toContainText(inr(1000)) // unchanged
  })

  test('transfer between my own accounts shows a receipt and moves the money', async ({ page, request }) => {
    const customer = await createCustomer(request)
    const savings = await openAccount(request, customer, 'SAVINGS', 10000)
    const current = await openAccount(request, customer, 'CURRENT', 0)
    await loginAs(page, customer)
    await page.goto(`/transfer?from=${savings.accountNumber}`)

    await page.getByRole('radio', { name: 'My accounts' }).click()
    await page.getByLabel('Destination account').selectOption(current.accountNumber)
    await page.getByLabel('Amount').fill('3000')
    await page.getByLabel('Note (optional)').fill('Move to current')
    await page.getByRole('button', { name: /Review transfer/ }).click()

    const review = page.getByRole('dialog', { name: 'Confirm transfer' })
    await expect(review.getByText(inr(3000))).toBeVisible()
    await expect(review.getByText(grouped(current.accountNumber))).toBeVisible()
    await expect(review.getByText(inr(7000))).toBeVisible() // balance after
    await review.getByRole('button', { name: 'Confirm & send' }).click()

    await expect(page.getByRole('heading', { name: 'Transfer successful' })).toBeVisible()
    await expect(page.getByText(inr(7000))).toBeVisible() // new balance on the receipt

    // both accounts changed, and each history shows its own side of the transfer
    await page.goto(`/accounts/${savings.accountNumber}`)
    await expect(page.getByLabel(/Savings account .* balance/)).toContainText(inr(7000))
    await expect(page.getByText(`Transfer to ${grouped(current.accountNumber)}`)).toBeVisible()
    await page.goto(`/accounts/${current.accountNumber}`)
    await expect(page.getByLabel(/Current account .* balance/)).toContainText(inr(3000))
    await expect(page.getByText(`Transfer from ${grouped(savings.accountNumber)}`)).toBeVisible()
  })

  test('transfer to another customer by account number', async ({ page, request }) => {
    const sender = await createCustomer(request)
    const receiver = await createCustomer(request)
    const from = await openAccount(request, sender, 'SAVINGS', 5000)
    const to = await openAccount(request, receiver, 'SAVINGS', 100)
    await loginAs(page, sender)
    await page.goto('/transfer')

    await page.getByLabel('Recipient account number').fill(to.accountNumber)
    await page.getByLabel('Amount').fill('1250.50')
    await page.getByRole('button', { name: /Review transfer/ }).click()
    await page.getByRole('dialog', { name: 'Confirm transfer' }).getByRole('button', { name: 'Confirm & send' }).click()

    await expect(page.getByRole('heading', { name: 'Transfer successful' })).toBeVisible()
    await expect(page.getByText(inr(3749.5))).toBeVisible()
    await page.goto(`/accounts/${from.accountNumber}`)
    await expect(page.getByLabel(/Savings account .* balance/)).toContainText(inr(3749.5))
  })

  test('transfer errors: unknown account, same account, too much money', async ({ page, request }) => {
    const customer = await createCustomer(request)
    const account = await openAccount(request, customer, 'SAVINGS', 500)
    await loginAs(page, customer)
    await page.goto('/transfer')

    const recipient = page.getByLabel('Recipient account number')
    const amount = page.getByLabel('Amount')
    const review = page.getByRole('button', { name: /Review transfer/ })

    // client-side checks
    await recipient.fill('123')
    await amount.fill('10')
    await review.click()
    await expect(page.getByText('Enter a valid 10-digit account number')).toBeVisible()

    await recipient.fill(account.accountNumber)
    await review.click()
    await expect(page.getByText('Source and destination accounts must be different')).toBeVisible()

    await recipient.fill('9999999999')
    await amount.fill('600')
    await review.click()
    await expect(page.getByText(`Amount exceeds your available balance of ${inr(500)}`)).toBeVisible()

    // server-side check: the account does not exist
    await amount.fill('100')
    await review.click()
    const dialog = page.getByRole('dialog', { name: 'Confirm transfer' })
    await dialog.getByRole('button', { name: 'Confirm & send' }).click()
    await expect(dialog.getByRole('alert')).toContainText('Account not found: 9999999999')

    // nothing moved
    await dialog.getByRole('button', { name: 'Edit' }).click()
    await page.goto(`/accounts/${account.accountNumber}`)
    await expect(page.getByLabel(/Savings account .* balance/)).toContainText(inr(500))
  })

  test('pressing Confirm twice quickly moves the money only once', async ({ page, request }) => {
    const sender = await createCustomer(request)
    const receiver = await createCustomer(request)
    const from = await openAccount(request, sender, 'SAVINGS', 1000)
    const to = await openAccount(request, receiver, 'SAVINGS', 0)
    await loginAs(page, sender)
    await page.goto('/transfer')

    await page.getByLabel('Recipient account number').fill(to.accountNumber)
    await page.getByLabel('Amount').fill('400')
    await page.getByRole('button', { name: /Review transfer/ }).click()
    const confirm = page.getByRole('dialog', { name: 'Confirm transfer' }).getByRole('button', { name: 'Confirm & send' })
    await confirm.dblclick()

    await expect(page.getByRole('heading', { name: 'Transfer successful' })).toBeVisible()
    await page.goto(`/accounts/${from.accountNumber}`)
    await expect(page.getByLabel(/Savings account .* balance/)).toContainText(inr(600)) // 1000 - 400, not 200
  })

  test('a customer cannot open someone else’s account page', async ({ page, request }) => {
    const owner = await createCustomer(request)
    const intruder = await createCustomer(request)
    const { accountNumber } = await openAccount(request, owner, 'SAVINGS', 100)
    await loginAs(page, intruder)

    await page.goto(`/accounts/${accountNumber}`)
    await expect(page.getByText('Account not found')).toBeVisible()
    await expect(page.getByText(inr(100))).toHaveCount(0)
  })
})
