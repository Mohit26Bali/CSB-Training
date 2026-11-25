import { createAccount, expect, signInViaForm, signOut, test, uniqueUser } from './fixtures'

/**
 * The watchlist in the browser: adding from a title page, the status / rating / note controls, that everything is
 * really saved (reload), the /watchlist page with its tabs, counts, sorting and removal, and that two users never see
 * each other's list. Titles come from the fake TMDB (movies 1001..., shows 2001...).
 */
const status = (page: import('@playwright/test').Page, name: string) => page.getByRole('group', { name: 'Status' }).getByRole('button', { name })

async function addTitle(page: import('@playwright/test').Page, path: string): Promise<string> {
  await page.goto(path)
  const name = (await page.getByRole('heading', { level: 1 }).innerText()).replace(/\s*\(\d{4}\)\s*$/, '')
  await page.getByRole('button', { name: 'Add to watchlist' }).click()
  await expect(page.getByText('On your watchlist')).toBeVisible()
  return name
}

// these tests create and sign in their own users, so they start signed out
test.use({ signedIn: false })

test.describe('watchlist', () => {
  test('a visitor opening a title is asked to sign in and returns to it afterwards', async ({ page }) => {
    const username = uniqueUser()
    await createAccount(page, username)
    await page.goto('/title/movie/1001')
    await expect(page).toHaveURL(/\/login\?next=%2Ftitle%2Fmovie%2F1001/)
    await page.getByLabel('Username').fill(username)
    await page.getByLabel('Password').fill('E2e-Pass-123!')
    await page.getByRole('button', { name: 'Sign in' }).click()
    await expect(page).toHaveURL('/title/movie/1001')
    await expect(page.getByRole('button', { name: 'Add to watchlist' })).toBeVisible()
  })

  test('adding a title puts it on the list as "Want to watch" and it stays after a reload', async ({ page }) => {
    const username = uniqueUser()
    await createAccount(page, username)
    await signInViaForm(page, username)

    await addTitle(page, '/title/movie/1001')
    await expect(status(page, 'Want to watch')).toHaveAttribute('aria-pressed', 'true')
    await expect(page.getByRole('group', { name: 'Your rating' })).toHaveCount(0)

    await page.reload()
    await expect(page.getByText('On your watchlist')).toBeVisible()
    await expect(status(page, 'Want to watch')).toHaveAttribute('aria-pressed', 'true')
  })

  test('a rating appears only once the title is watched, can be changed, and disappears with the status', async ({ page }) => {
    const username = uniqueUser()
    await createAccount(page, username)
    await signInViaForm(page, username)
    await addTitle(page, '/title/movie/1001')

    await status(page, 'Watched').click()
    const rating = page.getByRole('group', { name: 'Your rating' })
    await expect(rating).toBeVisible()
    await page.getByRole('button', { name: 'Rate 8 out of 10' }).click()
    await expect(page.getByText('Your rating: 8/10')).toBeVisible()
    await page.getByRole('button', { name: 'Rate 5 out of 10' }).click()
    await expect(page.getByText('Your rating: 5/10')).toBeVisible()

    await page.reload()
    await expect(page.getByText('Your rating: 5/10')).toBeVisible()

    await status(page, 'Watching').click()
    await expect(rating).toHaveCount(0)
    await status(page, 'Watched').click()
    await expect(page.getByText(/Your rating:/)).toHaveCount(0) // moving away cleared the rating
  })

  test('a note is saved only when asked, and survives a reload', async ({ page }) => {
    const username = uniqueUser()
    await createAccount(page, username)
    await signInViaForm(page, username)
    await addTitle(page, '/title/movie/1002')

    const save = page.getByRole('button', { name: 'Save note' })
    await expect(save).toBeDisabled()
    await page.getByLabel('Note').fill('Recommended by Asha')
    await expect(save).toBeEnabled()
    await save.click()
    await expect(save).toBeDisabled()

    await page.reload()
    await expect(page.getByLabel('Note')).toHaveValue('Recommended by Asha')
  })

  test('removing from the title page takes it off the list', async ({ page }) => {
    const username = uniqueUser()
    await createAccount(page, username)
    await signInViaForm(page, username)
    await addTitle(page, '/title/movie/1001')

    await page.getByRole('button', { name: 'Remove from list' }).click()
    await expect(page.getByRole('button', { name: 'Add to watchlist' })).toBeVisible()
    await page.goto('/watchlist')
    await expect(page.getByText('Your watchlist is empty')).toBeVisible()
  })

  test('an empty list says so and points to browsing', async ({ page }) => {
    const username = uniqueUser()
    await createAccount(page, username)
    await signInViaForm(page, username, '/watchlist')
    await expect(page.getByText('Your watchlist is empty')).toBeVisible()
    await expect(page.getByRole('link', { name: 'Find something to watch' })).toBeVisible()
    await expect(page.getByRole('tab', { name: /^All\s*0$/ })).toBeVisible()
  })

  test('the list page shows tabs with counts, filters by status, sorts and removes', async ({ page }) => {
    const username = uniqueUser()
    await createAccount(page, username)
    await signInViaForm(page, username)

    const first = await addTitle(page, '/title/movie/1001') // stays WANT
    const second = await addTitle(page, '/title/movie/1002')
    await status(page, 'Watching').click()
    await expect(status(page, 'Watching')).toHaveAttribute('aria-pressed', 'true')
    const third = await addTitle(page, '/title/tv/2001')
    await status(page, 'Watched').click()
    await page.getByRole('button', { name: 'Rate 9 out of 10' }).click()
    await expect(page.getByText('Your rating: 9/10')).toBeVisible()

    await page.goto('/watchlist')
    await expect(page.getByRole('tab', { name: /^All\s*3$/ })).toHaveAttribute('aria-selected', 'true')
    await expect(page.getByRole('tab', { name: /^Want to watch\s*1$/ })).toBeVisible()
    await expect(page.getByRole('tab', { name: /^Watching\s*1$/ })).toBeVisible()
    await expect(page.getByRole('tab', { name: /^Watched\s*1$/ })).toBeVisible()
    await expect(page.getByRole('listitem')).toHaveCount(3)
    // newest first
    await expect(page.getByRole('listitem').first()).toContainText(third)

    await page.getByRole('tab', { name: /^Watched/ }).click()
    await expect(page).toHaveURL(/status=WATCHED/)
    await expect(page.getByRole('listitem')).toHaveCount(1)
    await expect(page.getByRole('listitem').first()).toContainText(third)
    await expect(page.getByRole('listitem').first()).toContainText('9/10')

    await page.getByRole('tab', { name: /^All/ }).click()
    await page.getByLabel('Sort by').selectOption('title')
    await expect(page).toHaveURL(/sort=title/)
    const titles = await page.getByRole('listitem').locator('a.clamp-2').allInnerTexts()
    expect(titles).toEqual([...titles].sort((a, b) => a.localeCompare(b)))
    expect(titles).toEqual(expect.arrayContaining([first, second, third]))

    await page.getByRole('button', { name: `Remove ${second}` }).click()
    await expect(page.getByRole('listitem')).toHaveCount(2)
    await expect(page.getByRole('tab', { name: /^All\s*2$/ })).toBeVisible()
    await expect(page.getByRole('tab', { name: /^Watching\s*0$/ })).toBeVisible()

    await page.getByRole('tab', { name: /^Watching/ }).click()
    await expect(page.getByText('Nothing in “Watching” yet')).toBeVisible()
  })

  test('the tab and sort are in the URL, so a reload shows the same view', async ({ page }) => {
    const username = uniqueUser()
    await createAccount(page, username)
    await signInViaForm(page, username)
    await addTitle(page, '/title/movie/1001')
    await page.goto('/watchlist?status=WANT&sort=title')
    await page.reload()
    await expect(page.getByRole('tab', { name: /^Want to watch/ })).toHaveAttribute('aria-selected', 'true')
    await expect(page.getByLabel('Sort by')).toHaveValue('title')
    await expect(page.getByRole('listitem')).toHaveCount(1)
  })

  test('one user never sees another user’s list', async ({ page }) => {
    const alice = uniqueUser('alice')
    const bob = uniqueUser('bob')
    await createAccount(page, alice, 'Alice A')
    await createAccount(page, bob, 'Bob B')

    await signInViaForm(page, alice)
    const name = await addTitle(page, '/title/movie/1003')
    await page.goto('/watchlist')
    await expect(page.getByRole('listitem')).toHaveCount(1)
    await signOut(page)

    await signInViaForm(page, bob, '/watchlist')
    await expect(page.getByText('Your watchlist is empty')).toBeVisible()
    await page.goto('/title/movie/1003')
    await expect(page.getByRole('button', { name: 'Add to watchlist' })).toBeVisible() // not Alice's entry
    await signOut(page)

    await signInViaForm(page, alice, '/watchlist')
    await expect(page.getByRole('listitem').first()).toContainText(name)
  })
})
