import { BACKEND, expect, test } from './fixtures'

/**
 * The franchise tracker on a film's page, as a signed-in person sees it (every test starts signed in as a fresh "Test Viewer", see
 * e2e/fixtures.ts). The fake TMDB (e2e/tmdb-stub.mjs) has one series, "Harbor Saga Collection": films 1001-1003, already out, and
 * a fourth that is not out until 2999. Films 1004 and up, and every show, belong to no series. The backend's FranchiseApiTest tests the
 * rules in depth; here the point is what the page shows and does.
 */
const section = (page: import('@playwright/test').Page) => page.getByRole('region', { name: 'The series' })
const putStatus = (page: import('@playwright/test').Page, id: number, data: { status: string; rating?: number }) =>
  page.request.put(`${BACKEND}/api/watchlist/movie/${id}`, { data })

test.describe('the series of a film', () => {
  test('shows the series with its films oldest first, what is out and what is not', async ({ page }) => {
    await page.goto('/title/movie/1002')

    await expect(section(page).getByRole('heading', { name: 'Harbor Saga Collection' })).toBeVisible()
    await expect(section(page).getByTestId('franchise-progress')).toHaveText('0 of 3')
    await expect(section(page)).toContainText('1 more to come')
    const parts = section(page).locator('li[data-part]')
    await expect(parts).toHaveCount(4)
    await expect(parts.nth(0)).toHaveAttribute('data-part', '1001')
    await expect(parts.nth(3)).toHaveAttribute('data-part', '1090')
    await expect(section(page).getByTestId('franchise-badge-1001')).toHaveText('Not on my list')
    await expect(section(page).getByTestId('franchise-badge-1090')).toHaveText('Not out yet')
    await expect(section(page).getByRole('link', { name: /Harbor Saga: Forever/ })).toBeVisible()
    await expect(section(page).locator('a[aria-current="page"]')).toHaveCount(1) // the film being looked at is marked
    await expect(section(page).getByTestId('franchise-add')).toHaveText('Add the other 3 to my watchlist')
  })

  test('your progress follows your watchlist', async ({ page }) => {
    await putStatus(page, 1001, { status: 'WATCHED', rating: 9 })
    await putStatus(page, 1002, { status: 'WANT' })
    await page.goto('/title/movie/1003')

    await expect(section(page).getByTestId('franchise-progress')).toHaveText('1 of 3')
    await expect(section(page).getByTestId('franchise-badge-1001')).toHaveText('Watched')
    await expect(section(page).getByTestId('franchise-badge-1002')).toHaveText('On my list')
    await expect(section(page).getByTestId('franchise-badge-1003')).toHaveText('Not on my list')
    await expect(section(page).getByRole('progressbar', { name: 'Series progress' })).toHaveAttribute('aria-valuenow', '1')
    await expect(section(page).getByTestId('franchise-add')).toHaveText('Add the other 1 to my watchlist')
  })

  test('one click puts the rest on the watchlist and leaves what was already there alone', async ({ page }) => {
    await putStatus(page, 1002, { status: 'WATCHED', rating: 8 })
    await page.goto('/title/movie/1001')

    await section(page).getByTestId('franchise-add').click()

    await expect(section(page).getByTestId('franchise-result')).toHaveText('Added 2 films to your watchlist.')
    await expect(section(page).getByTestId('franchise-badge-1001')).toHaveText('On my list')
    await expect(section(page).getByTestId('franchise-badge-1002')).toHaveText('Watched') // not reset to want-to-watch
    await expect(section(page).getByTestId('franchise-badge-1090')).toHaveText('Not out yet') // never added
    await expect(section(page).getByTestId('franchise-add')).toHaveCount(0)
    await expect(section(page)).toContainText('Every film that is out is on your list.')

    const kept = await (await page.request.get(`${BACKEND}/api/watchlist/movie/1002`)).json()
    expect(kept).toMatchObject({ status: 'WATCHED', rating: 8 })
    const added = await (await page.request.get(`${BACKEND}/api/watchlist/movie/1003`)).json()
    expect(added).toMatchObject({ status: 'WANT', title: expect.any(String) })
    expect((await page.request.get(`${BACKEND}/api/watchlist/movie/1090`)).status()).toBe(404)

    await page.goto('/watchlist')
    await expect(page.getByRole('tab', { name: /^All\s*3$/ })).toHaveAttribute('aria-selected', 'true')
  })

  test('the title page itself knows the film is now on the list', async ({ page }) => {
    await page.goto('/title/movie/1003')
    await page.getByTestId('franchise-add').click()
    await expect(page.getByTestId('franchise-result')).toBeVisible()

    await expect(page.getByRole('button', { name: 'Want to watch' })).toHaveAttribute('aria-pressed', 'true')
  })

  test('a part opens its own page', async ({ page }) => {
    await page.goto('/title/movie/1001')
    await section(page).locator('li[data-part="1003"] a').click()

    await expect(page).toHaveURL('/title/movie/1003')
    await expect(section(page).locator('li[data-part="1003"] a[aria-current="page"]')).toHaveCount(1)
  })

  test('a film in no series, and every show, has no series section', async ({ page }) => {
    await page.goto('/title/movie/1004')
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
    await expect(section(page)).toHaveCount(0)

    await page.goto('/title/tv/2001')
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
    await expect(section(page)).toHaveCount(0)
  })

  test('people see only their own progress', async ({ page, playwright }) => {
    await putStatus(page, 1001, { status: 'WATCHED' })
    const other = await playwright.request.newContext({ baseURL: BACKEND })
    const stranger = `viewer${Date.now()}`
    const registered = await other.post('/api/auth/register', { data: { username: stranger, email: `${stranger}@example.com`, password: 'Violet-Rain-77!', displayName: 'Stranger' } })
    const token = (await registered.json()).token as string

    const theirs = await (await other.get('/api/titles/movie/1001/franchise', { headers: { Authorization: `Bearer ${token}` } })).json()
    expect(theirs.watched).toBe(0)
    const mine = await (await page.request.get(`${BACKEND}/api/titles/movie/1001/franchise`)).json()
    expect(mine.watched).toBe(1)
  })

  test('on a phone the series scrolls inside its row and the page does not scroll sideways', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await page.goto('/title/movie/1001')
    await expect(section(page)).toBeVisible()

    expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(0)
  })
})

test.describe('the series for visitors', () => {
  test.use({ signedIn: false })

  test('the franchise endpoint needs a login', async ({ page }) => {
    expect((await page.request.get(`${BACKEND}/api/titles/movie/1001/franchise`)).status()).toBe(401)
    expect((await page.request.post(`${BACKEND}/api/collections/9001/watchlist`)).status()).toBe(401)
  })
})
