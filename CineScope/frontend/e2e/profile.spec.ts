import { BACKEND, expect, PASSWORD, test } from './fixtures'

/**
 * "My profile" in the browser. Every test starts signed in as a brand-new ordinary user (the default of e2e/fixtures.ts), so
 * changing the password or deleting the account here only ever touches that one throw-away account.
 */
const NEW_PASSWORD = 'Violet-Rain-77!'
const card = (page: import('@playwright/test').Page, name: string) => page.getByRole('region', { name })

async function openProfile(page: import('@playwright/test').Page) {
  await page.goto('/')
  await page.getByRole('button', { name: /Account menu/ }).click()
  await page.getByRole('menuitem', { name: 'My profile' }).click()
  await expect(page).toHaveURL('/profile')
}

async function fillPasswordForm(page: import('@playwright/test').Page, current: string, next: string, confirm = next) {
  const form = card(page, 'Change password')
  await form.getByLabel('Current password').fill(current)
  await form.getByLabel('New password', { exact: true }).fill(next)
  await form.getByLabel('Confirm new password').fill(confirm)
  await form.getByRole('button', { name: 'Change password' }).click()
}

test.describe('my profile', () => {
  test('is reached from the account menu and shows my details, with the username and email fixed', async ({ page, account }) => {
    await openProfile(page)

    const details = card(page, 'Your details')
    await expect(page.getByRole('heading', { level: 1, name: 'My profile' })).toBeVisible()
    await expect(details.getByLabel('Username')).toHaveValue(account!.username)
    await expect(details.getByLabel('Username')).toBeDisabled()
    await expect(details.getByLabel('Email')).toHaveValue(`${account!.username}@example.com`)
    await expect(details.getByLabel('Email')).toBeDisabled()
    await expect(details.getByLabel('Display name')).toHaveValue('Test Viewer')
    await expect(page.getByText('Administrator', { exact: true })).toHaveCount(0)
  })

  test('changing the name and country is saved, shown in the header and still there after a reload', async ({ page }) => {
    await openProfile(page)
    const details = card(page, 'Your details')

    await details.getByLabel('Display name').fill('Priya Nair')
    await details.getByLabel('Country').selectOption({ label: 'United States' })
    await details.getByRole('button', { name: 'Save details' }).click()

    await expect(details.getByRole('status')).toHaveText('Your details were saved.')
    await expect(page.getByRole('button', { name: 'Account menu for Priya Nair' })).toHaveText('PN')

    await page.reload()
    await expect(details.getByLabel('Display name')).toHaveValue('Priya Nair')
    await expect(details.getByLabel('Country')).toHaveValue('US')
  })

  test('an empty name is stopped before anything is sent', async ({ page }) => {
    await openProfile(page)
    const details = card(page, 'Your details')

    await details.getByLabel('Display name').fill('   ')
    await details.getByRole('button', { name: 'Save details' }).click()

    await expect(details.getByRole('alert')).toHaveText('Enter your name')
    await expect(page.getByRole('button', { name: 'Account menu for Test Viewer' })).toBeVisible()
  })

  // ---- password ----

  test('a wrong current password is explained and does not sign anyone out', async ({ page }) => {
    await openProfile(page)

    await fillPasswordForm(page, 'Not-The-One-1!', NEW_PASSWORD)

    await expect(card(page, 'Change password').getByRole('alert')).toHaveText('The current password is incorrect')
    await page.goto('/watchlist')
    await expect(page.getByRole('heading', { level: 1, name: 'My watchlist' })).toBeVisible() // still signed in
  })

  test('a weak, mismatched or unchanged new password is stopped with a clear message', async ({ page }) => {
    await openProfile(page)
    const form = card(page, 'Change password')

    await fillPasswordForm(page, PASSWORD, 'password1')
    await expect(form.getByRole('alert')).toHaveText('Password must include an uppercase letter and a symbol such as ! @ # or -')
    await expect(form.locator('[data-rule="upper"]')).toBeVisible() // the checklist shows what is still needed

    await fillPasswordForm(page, PASSWORD, NEW_PASSWORD, 'Violet-Rain-78!')
    await expect(form.getByRole('alert')).toHaveText('The new passwords do not match')

    await fillPasswordForm(page, PASSWORD, PASSWORD)
    await expect(form.getByRole('alert')).toHaveText('The new password must be different from the current one')
  })

  test('changing the password signs out every other session, keeps this one, and the new password works', async ({ page, account }) => {
    const oldToken = account!.token
    // The default header of the test fixture (the OLD token) would override the app's new token on every browser request;
    // this test is about the app handling the new one, so the browser sends only what the app sends.
    await page.context().setExtraHTTPHeaders({})
    await openProfile(page)

    await fillPasswordForm(page, PASSWORD, NEW_PASSWORD)

    await expect(card(page, 'Change password').getByRole('status')).toHaveText('Password changed. Your other sessions were signed out.')
    // the old token is dead for the API...
    expect((await page.request.get(`${BACKEND}/api/profile`, { headers: { Authorization: `Bearer ${oldToken}` } })).status()).toBe(401)
    // ...but this browser carries a new one and carries on
    const newToken = await page.evaluate(() => JSON.parse(sessionStorage.getItem('cinescope.session')!).token as string)
    expect(newToken).not.toBe(oldToken)
    await page.goto('/watchlist')
    await expect(page.getByRole('heading', { level: 1, name: 'My watchlist' })).toBeVisible()

    // signing out and in again: the new password works, the old one does not
    await page.getByRole('button', { name: /Account menu/ }).click()
    await page.getByRole('menuitem', { name: 'Sign out' }).click()
    await expect(page).toHaveURL('/login')
    await page.getByLabel('Username').fill(account!.username)
    await page.getByLabel('Password').fill(PASSWORD)
    await page.getByRole('button', { name: 'Sign in' }).click()
    await expect(page.getByRole('alert')).toHaveText('Invalid username or password')
    await page.getByLabel('Password').fill(NEW_PASSWORD)
    await page.getByRole('button', { name: 'Sign in' }).click()
    await expect(page.getByRole('button', { name: /Account menu/ })).toBeVisible()
  })

  // ---- deleting the account ----

  test('deleting the account needs the password, can be cancelled, and then removes the account for good', async ({ page, account }) => {
    await openProfile(page)
    const danger = card(page, 'Delete account')

    await danger.getByRole('button', { name: /Delete my account/ }).click()
    const dialog = page.getByRole('dialog', { name: 'Delete your account?' })
    await dialog.getByRole('button', { name: 'Cancel' }).click()
    await expect(dialog).toHaveCount(0)
    await page.reload()
    await expect(page.getByRole('heading', { level: 1, name: 'My profile' })).toBeVisible() // nothing happened

    await danger.getByRole('button', { name: /Delete my account/ }).click()
    await dialog.getByLabel('Your password').fill('Not-The-One-1!')
    await dialog.getByRole('button', { name: 'Delete my account' }).click()
    await expect(dialog.getByRole('alert')).toHaveText('The current password is incorrect')
    await expect(page).toHaveURL('/profile')

    await dialog.getByLabel('Your password').fill(PASSWORD)
    await dialog.getByRole('button', { name: 'Delete my account' }).click()
    await expect(page).toHaveURL('/login')

    await page.getByLabel('Username').fill(account!.username)
    await page.getByLabel('Password').fill(PASSWORD)
    await page.getByRole('button', { name: 'Sign in' }).click()
    await expect(page.getByRole('alert')).toHaveText('Invalid username or password')
    expect((await page.request.get(`${BACKEND}/api/profile`)).status()).toBe(401) // the account's token no longer opens anything
  })

  // ---- not an administrator ----

  test('an ordinary user sees no admin link, a "no access" page at the admin address, and the server refuses too', async ({ page }) => {
    await page.goto('/')
    await expect(page.getByRole('navigation', { name: 'Main' }).getByRole('link', { name: 'Admin' })).toHaveCount(0)

    await page.goto('/admin/users')
    await expect(page.getByText('You don’t have access to this page')).toBeVisible()
    await expect(page.getByRole('table')).toHaveCount(0)

    expect((await page.request.get('/api/admin/users')).status()).toBe(403)
  })
})
