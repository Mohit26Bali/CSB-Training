import { BACKEND, expect, test, uniqueUser } from './fixtures'

/**
 * The administrator's "Manage accounts" screen in the browser, signed in as the demo administrator (created because the test
 * backend runs with SEED_DEMO_DATA=true, see config/DemoDataInitializer). Tests that change or delete accounts make their own
 * throw-away accounts through the API first, so the demo accounts that e2e/demo-data.spec.ts relies on are never touched.
 */
test.use({ signedIn: false })

const ADMIN_PASSWORD = 'Control-Room-26!'
const USER_PASSWORD = 'Violet-Rain-77!'
type PageT = import('@playwright/test').Page

async function signIn(page: PageT, username: string, password: string) {
  await page.goto('/login')
  await page.getByLabel('Username').fill(username)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Sign in' }).click()
}

async function signInAsAdmin(page: PageT) {
  await signIn(page, 'admin', ADMIN_PASSWORD)
  await expect(page.getByRole('button', { name: 'Account menu for Site Admin' })).toBeVisible()
}

async function adminToken(page: PageT): Promise<string> {
  const res = await page.request.post(`${BACKEND}/api/auth/login`, { data: { username: 'admin', password: ADMIN_PASSWORD } })
  return (await res.json()).token
}

/** A throw-away account made through the admin API. */
async function makeUser(page: PageT, token: string, prefix = 'tmp') {
  const username = uniqueUser(prefix)
  const res = await page.request.post(`${BACKEND}/api/admin/users`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { username, email: `${username}@example.com`, displayName: `Temp ${username}`, password: USER_PASSWORD, role: 'USER' },
  })
  expect(res.status(), 'creating a throw-away account').toBe(201)
  return username
}

const row = (page: PageT, username: string) => page.locator(`tr[data-username="${username}"]`)
const search = (page: PageT, text: string) => page.getByLabel('Search accounts').fill(text)

test.describe('manage accounts (administrator)', () => {
  test('the admin sees an Admin link and the accounts table with the demo accounts and their watchlist sizes', async ({ page }) => {
    await signInAsAdmin(page)
    await page.getByRole('navigation', { name: 'Main' }).getByRole('link', { name: 'Admin' }).click()

    await expect(page).toHaveURL('/admin/users')
    await expect(page.getByRole('heading', { level: 1, name: 'Manage accounts' })).toBeVisible()
    await search(page, 'asha')
    await expect(page.locator('tbody tr')).toHaveCount(1)
    await expect(row(page, 'asha')).toContainText('Asha Rao')
    await expect(row(page, 'asha')).toContainText('asha@example.com')
    await expect(row(page, 'asha')).toContainText('User')
    await expect(row(page, 'asha')).toContainText('Enabled')
    await expect(row(page, 'asha').locator('td').nth(4)).toHaveText('9') // her seeded watchlist

    await search(page, 'admin')
    await expect(row(page, 'admin')).toContainText('(you)')
    await expect(row(page, 'admin')).toContainText('Administrator')
  })

  test('search, role and status filters narrow the list, and live in the address', async ({ page }) => {
    const token = await adminToken(page)
    const username = await makeUser(page, token)
    await signInAsAdmin(page)
    await page.goto('/admin/users')

    await search(page, username.toUpperCase()) // any letter case
    await expect(page).toHaveURL(new RegExp(`q=${username}`, 'i'))
    await expect(page.locator('tbody tr')).toHaveCount(1)

    await page.getByLabel('Role').selectOption('ADMIN')
    await expect(page.getByText('No accounts match')).toBeVisible()
    await page.getByLabel('Role').selectOption('')
    await page.getByLabel('Status').selectOption('false')
    await expect(page.getByText('No accounts match')).toBeVisible()

    await search(page, '')
    await page.getByLabel('Status').selectOption('')
    await page.getByLabel('Role').selectOption('ADMIN')
    await expect(row(page, 'admin')).toBeVisible()
    await expect(row(page, 'asha')).toHaveCount(0)

    await page.reload()
    await expect(page.getByLabel('Role')).toHaveValue('ADMIN') // the view survives a reload
  })

  test('long lists are paged, ten at a time', async ({ page }) => {
    const token = await adminToken(page)
    const prefix = uniqueUser('pg')
    for (let i = 0; i < 12; i++) {
      await makeUser(page, token, `${prefix}${i}`)
    }
    await signInAsAdmin(page)
    await page.goto('/admin/users')
    await search(page, prefix)

    await expect(page.locator('tbody tr')).toHaveCount(10)
    await expect(page.getByText('Showing 10 of 12 accounts')).toBeVisible()
    await expect(page.getByText('Page 1 of 2')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Previous' })).toBeDisabled()

    await page.getByRole('button', { name: 'Next' }).click()
    await expect(page).toHaveURL(/page=2/)
    await expect(page.locator('tbody tr')).toHaveCount(2)
    await expect(page.getByRole('button', { name: 'Next' })).toBeDisabled()
    await page.getByRole('button', { name: 'Previous' }).click()
    await expect(page.getByText('Page 1 of 2')).toBeVisible()
  })

  test('creating an account: the password rules apply, duplicates are refused, and the new person can sign in', async ({ page }) => {
    const username = uniqueUser('new')
    await signInAsAdmin(page)
    await page.goto('/admin/users')
    await page.getByRole('button', { name: 'New account' }).click()
    const dialog = page.getByRole('dialog', { name: 'New account' })

    await dialog.getByLabel('Username').fill(username)
    await dialog.getByLabel('Email').fill(`${username}@example.com`)
    await dialog.getByLabel('Display name').fill('New Person')
    await dialog.getByLabel('Password').fill('password1')
    await expect(dialog.locator('[data-rule="upper"]')).toBeVisible() // the same live checklist as at sign-up
    await dialog.getByRole('button', { name: 'Create account' }).click()
    await expect(dialog.getByRole('alert')).toHaveText('Password must include an uppercase letter and a symbol such as ! @ # or -')

    await dialog.getByLabel('Password').fill(`${username}-A1!`)
    await dialog.getByRole('button', { name: 'Create account' }).click()
    await expect(dialog.getByRole('alert')).toHaveText('Password must not contain your username')

    await dialog.getByLabel('Username').fill('asha') // taken (a demo account)
    await dialog.getByLabel('Email').fill('another@example.com')
    await dialog.getByLabel('Password').fill(USER_PASSWORD)
    await dialog.getByRole('button', { name: 'Create account' }).click()
    await expect(dialog.getByRole('alert')).toContainText("Username 'asha' is already taken")

    await dialog.getByLabel('Username').fill(username)
    await dialog.getByLabel('Email').fill(`${username}@example.com`)
    await dialog.getByRole('button', { name: 'Create account' }).click()
    await expect(page.getByRole('status')).toHaveText(`Account ${username} was created.`)
    await search(page, username)
    await expect(row(page, username)).toContainText('New Person')
    await expect(row(page, username)).toContainText('Enabled')

    await page.getByRole('button', { name: 'Account menu for Site Admin' }).click()
    await page.getByRole('menuitem', { name: 'Sign out' }).click()
    await signIn(page, username, USER_PASSWORD)
    await expect(page.getByRole('button', { name: 'Account menu for New Person' })).toBeVisible()
  })

  test('editing: name, country and role are saved; making someone an administrator ends their old session', async ({ page }) => {
    const token = await adminToken(page)
    const username = await makeUser(page, token)
    const userToken = (await (await page.request.post(`${BACKEND}/api/auth/login`, { data: { username, password: USER_PASSWORD } })).json()).token
    await signInAsAdmin(page)
    await page.goto('/admin/users')
    await search(page, username)

    await row(page, username).getByRole('button', { name: `Edit ${username}` }).click()
    const dialog = page.getByRole('dialog', { name: `Edit ${username}` })
    await expect(dialog.getByLabel('Username')).toBeDisabled()
    await dialog.getByLabel('Display name').fill('Promoted Person')
    await dialog.getByLabel('Country').selectOption({ label: 'United Kingdom' })
    await dialog.getByLabel('Role').selectOption('ADMIN')
    await dialog.getByRole('button', { name: 'Save changes' }).click()

    await expect(page.getByRole('status')).toHaveText(`Changes to ${username} were saved.`)
    await expect(row(page, username)).toContainText('Promoted Person')
    await expect(row(page, username)).toContainText('Administrator')
    await expect(row(page, username)).toContainText('United Kingdom')
    // the old session ended with the role change; signing in again gives the new rights
    expect((await page.request.get(`${BACKEND}/api/profile`, { headers: { Authorization: `Bearer ${userToken}` } })).status()).toBe(401)
    const fresh = (await (await page.request.post(`${BACKEND}/api/auth/login`, { data: { username, password: USER_PASSWORD } })).json()).token
    expect((await page.request.get(`${BACKEND}/api/admin/users`, { headers: { Authorization: `Bearer ${fresh}` } })).status()).toBe(200)
  })

  test('disabling an account locks the person out at once, and enabling lets them back in', async ({ page }) => {
    const token = await adminToken(page)
    const username = await makeUser(page, token)
    const userToken = (await (await page.request.post(`${BACKEND}/api/auth/login`, { data: { username, password: USER_PASSWORD } })).json()).token
    await signInAsAdmin(page)
    await page.goto('/admin/users')
    await search(page, username)

    await row(page, username).getByRole('button', { name: `Edit ${username}` }).click()
    await page.getByRole('dialog').getByLabel('Account enabled').uncheck()
    await page.getByRole('dialog').getByRole('button', { name: 'Save changes' }).click()
    await expect(row(page, username)).toContainText('Disabled')

    expect((await page.request.get(`${BACKEND}/api/profile`, { headers: { Authorization: `Bearer ${userToken}` } })).status()).toBe(401)
    const refused = await page.request.post(`${BACKEND}/api/auth/login`, { data: { username, password: USER_PASSWORD } })
    expect(refused.status()).toBe(401)
    expect((await refused.json()).message).toBe('Invalid username or password')

    await row(page, username).getByRole('button', { name: `Edit ${username}` }).click()
    await page.getByRole('dialog').getByLabel('Account enabled').check()
    await page.getByRole('dialog').getByRole('button', { name: 'Save changes' }).click()
    await expect(row(page, username)).toContainText('Enabled')
    expect((await page.request.post(`${BACKEND}/api/auth/login`, { data: { username, password: USER_PASSWORD } })).status()).toBe(200)
  })

  test('deleting asks first, can be cancelled, and removes the account', async ({ page }) => {
    const token = await adminToken(page)
    const username = await makeUser(page, token)
    await signInAsAdmin(page)
    await page.goto('/admin/users')
    await search(page, username)

    await row(page, username).getByRole('button', { name: `Delete ${username}` }).click()
    const dialog = page.getByRole('dialog', { name: `Delete ${username}?` })
    await expect(dialog).toContainText(`@${username}`)
    await expect(dialog).toContainText('0 watchlist entries')
    await dialog.getByRole('button', { name: 'Cancel' }).click()
    await expect(dialog).toHaveCount(0)
    await expect(row(page, username)).toBeVisible() // cancelled: still there

    await row(page, username).getByRole('button', { name: `Delete ${username}` }).click()
    await dialog.getByRole('button', { name: 'Delete account' }).click()
    await expect(page.getByRole('status')).toHaveText(`Account ${username} was deleted.`)
    await expect(row(page, username)).toHaveCount(0)
    expect((await page.request.post(`${BACKEND}/api/auth/login`, { data: { username, password: USER_PASSWORD } })).status()).toBe(401)
  })

  test('administrators cannot lock themselves out: no delete, and role and enabled are locked on their own account', async ({ page }) => {
    await signInAsAdmin(page)
    await page.goto('/admin/users')
    await search(page, 'admin')

    await expect(row(page, 'admin').getByRole('button', { name: 'Delete admin' })).toBeDisabled()
    await row(page, 'admin').getByRole('button', { name: 'Edit admin' }).click()
    const dialog = page.getByRole('dialog', { name: 'Edit admin' })
    await expect(dialog.getByLabel('Role')).toBeDisabled()
    await expect(dialog.getByLabel('Account enabled')).toBeDisabled()
    await expect(dialog.getByText('This is your own account, so you cannot change your role or disable it.')).toBeVisible()

    // even if the page were bypassed, the server says no
    const token = await adminToken(page)
    const list = await (await page.request.get(`${BACKEND}/api/admin/users?q=admin`, { headers: { Authorization: `Bearer ${token}` } })).json()
    const me = list.items.find((u: { username: string }) => u.username === 'admin')
    const demote = await page.request.put(`${BACKEND}/api/admin/users/${me.id}`, {
      headers: { Authorization: `Bearer ${token}` },
      data: { displayName: 'Site Admin', region: 'IN', role: 'USER', enabled: true },
    })
    expect(demote.status()).toBe(400)
    const remove = await page.request.delete(`${BACKEND}/api/admin/users/${me.id}`, { headers: { Authorization: `Bearer ${token}` } })
    expect(remove.status()).toBe(400)
  })
})
