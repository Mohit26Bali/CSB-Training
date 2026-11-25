import { BACKEND, expect, test, uniqueUser } from './fixtures'

/**
 * Moderation in the browser: the administrator's queue of comments the processing pipeline held back, and what approving or
 * rejecting does to what other people see. The two comments held in the demo data (written by "karan" on Fight Club) are only
 * looked at; every test that decides makes its own held comment first, through the API, as a throw-away user.
 */
test.use({ signedIn: false })

const ADMIN_PASSWORD = 'Control-Room-26!'
const USER_PASSWORD = 'Violet-Rain-77!'
type PageT = import('@playwright/test').Page

async function signInAsAdmin(page: PageT) {
  await page.goto('/login')
  await page.getByLabel('Username').fill('admin')
  await page.getByLabel('Password').fill(ADMIN_PASSWORD)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('button', { name: 'Account menu for Site Admin' })).toBeVisible()
}

async function login(page: PageT, username: string, password: string): Promise<string> {
  const res = await page.request.post(`${BACKEND}/api/auth/login`, { data: { username, password } })
  return (await res.json()).token
}

/** A throw-away user (made by the administrator) who posts a comment that the pipeline holds. Returns who and which comment. */
async function heldCommentBy(page: PageT, text: string) {
  const admin = await login(page, 'admin', ADMIN_PASSWORD)
  const username = uniqueUser('mod')
  const created = await page.request.post(`${BACKEND}/api/admin/users`, {
    headers: { Authorization: `Bearer ${admin}` },
    data: { username, email: `${username}@example.com`, displayName: `Poster ${username}`, password: USER_PASSWORD, role: 'USER' },
  })
  expect(created.status()).toBe(201)
  const token = await login(page, username, USER_PASSWORD)
  const posted = await page.request.post(`${BACKEND}/api/titles/movie/1001/comments`, { headers: { Authorization: `Bearer ${token}` }, data: { body: text, rating: 3 } })
  expect(posted.status()).toBe(201)
  const comment = await posted.json()
  expect(comment.status).toBe('PENDING')
  return { username, token, id: comment.id as number }
}

const card = (page: PageT, username: string) => page.locator(`article[data-author="${username}"]`)
const visibleTo = async (page: PageT, token: string, id: number) => {
  const list = await (await page.request.get(`${BACKEND}/api/titles/movie/1001/comments?size=30`, { headers: { Authorization: `Bearer ${token}` } })).json()
  return (list.items as { id: number; status: string }[]).filter((c) => c.id === id).map((c) => c.status)
}

test.describe('moderation (administrator)', () => {
  test('the queue shows the comments the checks held back, with the reason, and a count in the tab', async ({ page }) => {
    await signInAsAdmin(page)
    await page.goto('/admin/moderation')

    await expect(page.getByRole('heading', { level: 1, name: 'Moderation' })).toBeVisible()
    await expect(page.getByTestId('moderation-count')).toBeVisible() // at least the two seeded comments
    const seeded = card(page, 'karan')
    await expect(seeded.first()).toBeVisible()
    expect(await seeded.count()).toBeGreaterThanOrEqual(2)
    // the strong-language one is masked even here, and each card lists why it is held
    await expect(page.getByText('Absolute s*** ending, what a waste of two hours.')).toBeVisible()
    await expect(page.getByText('Watch it free here http://a.example http://b.example http://c.example')).toBeVisible()
    await expect(seeded.first().locator('[data-step="Decision"]')).toContainText('Held for review')
    await expect(page.locator('[data-step="Spam check"][data-outcome="HELD"]').first()).toContainText('3 links found')
    await expect(page.locator('[data-step="Profanity filter"][data-outcome="HELD"]').first()).toContainText('Strong language')
  })

  test('approving a held comment publishes it for everyone', async ({ page }) => {
    const held = await heldCommentBy(page, 'This ending is shit but the acting is brilliant')
    const stranger = await login(page, 'ravi', 'Cinema-Night-26!')
    expect(await visibleTo(page, stranger, held.id)).toEqual([]) // nobody else sees it while it waits
    expect(await visibleTo(page, held.token, held.id)).toEqual(['PENDING'])

    await signInAsAdmin(page)
    await page.goto('/admin/moderation')
    await card(page, held.username).getByRole('button', { name: `Approve comment by ${held.username}` }).click()

    await expect(card(page, held.username)).toHaveCount(0) // gone from the waiting list
    expect(await visibleTo(page, stranger, held.id)).toEqual(['PUBLISHED'])
    await page.getByRole('group', { name: 'Show comments' }).getByRole('button', { name: 'Published' }).click()
    await expect(card(page, held.username).locator('[data-step="Moderator review"]')).toContainText('Approved by admin')
  })

  test('rejecting hides it from everyone except its author, who is told', async ({ page }) => {
    const held = await heldCommentBy(page, 'Pure shit, avoid it')
    const stranger = await login(page, 'meera', 'Cinema-Night-26!')

    await signInAsAdmin(page)
    await page.goto('/admin/moderation')
    await card(page, held.username).getByRole('button', { name: `Reject comment by ${held.username}` }).click()
    await expect(card(page, held.username)).toHaveCount(0)

    expect(await visibleTo(page, stranger, held.id)).toEqual([])
    expect(await visibleTo(page, held.token, held.id)).toEqual(['REJECTED'])
    await page.getByRole('group', { name: 'Show comments' }).getByRole('button', { name: 'Rejected' }).click()
    await expect(card(page, held.username)).toBeVisible()
    await expect(card(page, held.username).locator('[data-step="Moderator review"]')).toContainText('Rejected by admin')
  })

  test('an ordinary user cannot open the moderation page, and the server refuses too', async ({ page }) => {
    const user = await login(page, 'asha', 'Cinema-Night-26!')
    await page.goto('/login')
    await page.getByLabel('Username').fill('asha')
    await page.getByLabel('Password').fill('Cinema-Night-26!')
    await page.getByRole('button', { name: 'Sign in' }).click()
    await expect(page.getByRole('button', { name: /Account menu/ })).toBeVisible()

    await page.goto('/admin/moderation')
    await expect(page.getByText('You don’t have access to this page')).toBeVisible()
    expect((await page.request.get(`${BACKEND}/api/admin/comments`, { headers: { Authorization: `Bearer ${user}` } })).status()).toBe(403)
  })

  test('the Moderation entry is in the administrator’s menu and the two admin sections are linked', async ({ page }) => {
    await signInAsAdmin(page)
    await page.getByRole('button', { name: /Account menu/ }).click()
    await page.getByRole('menuitem', { name: 'Moderation' }).click()
    await expect(page).toHaveURL('/admin/moderation')

    await page.getByRole('navigation', { name: 'Admin sections' }).getByRole('link', { name: 'Accounts' }).click()
    await expect(page).toHaveURL('/admin/users')
    await expect(page.getByRole('heading', { level: 1, name: 'Manage accounts' })).toBeVisible()
  })
})
