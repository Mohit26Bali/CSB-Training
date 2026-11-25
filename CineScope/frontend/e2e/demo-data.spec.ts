import { expect, test } from './fixtures'

/**
 * The demo accounts that the backend creates when SEED_DEMO_DATA=true (config/DemoDataInitializer; playwright.config.ts turns
 * it on for the test backend): signing in as them through the real form and seeing their ready-made watchlists. The lists
 * hold fixed snapshots (title, poster, year), so the fake TMDB is not involved.
 */
test.use({ signedIn: false })

const DEMO_PASSWORD = 'Cinema-Night-26!'

async function signInAs(page: import('@playwright/test').Page, username: string, password = DEMO_PASSWORD) {
  await page.goto('/login')
  await page.getByLabel('Username').fill(username)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Sign in' }).click()
}

/** Signs in and waits until it has worked (the account menu appears), so the next step can navigate safely. */
async function signedInAs(page: import('@playwright/test').Page, username: string, password = DEMO_PASSWORD) {
  await signInAs(page, username, password)
  await expect(page.getByRole('button', { name: /Account menu/ })).toBeVisible()
}

const tab = (page: import('@playwright/test').Page, name: string, count: number) => page.getByRole('tab', { name: new RegExp(`^${name}\\s*${count}$`) })
const rows = (page: import('@playwright/test').Page) => page.getByRole('listitem')

test.describe('demo accounts', () => {
  test('asha signs in and finds her list ready, with counts, ratings and notes', async ({ page }) => {
    await signInAs(page, 'asha')
    await expect(page.getByRole('button', { name: 'Account menu for Asha Rao' })).toBeVisible()

    await page.goto('/watchlist')
    await expect(tab(page, 'All', 9)).toHaveAttribute('aria-selected', 'true')
    await expect(tab(page, 'Want to watch', 3)).toBeVisible()
    await expect(tab(page, 'Watching', 2)).toBeVisible()
    await expect(tab(page, 'Watched', 4)).toBeVisible()
    await expect(rows(page)).toHaveCount(9)

    await page.getByRole('tab', { name: /^Watched/ }).click()
    await expect(rows(page)).toHaveCount(4)
    const interstellar = rows(page).filter({ hasText: 'Interstellar' })
    await expect(interstellar).toContainText('10/10')
    await expect(interstellar).toContainText('Cried at the docking scene.')
    await expect(interstellar).toContainText('2014')
    await expect(rows(page).filter({ hasText: '3 Idiots' })).toContainText('All is well.')
  })

  test('each demo account has its own list', async ({ page }) => {
    await signedInAs(page, 'ravi')
    await page.goto('/watchlist')
    await expect(tab(page, 'All', 6)).toBeVisible()
    await expect(rows(page).filter({ hasText: 'Fight Club' })).toHaveCount(1)
    await expect(rows(page).filter({ hasText: 'Inception' })).toHaveCount(0) // that is asha's
    await expect(rows(page).filter({ hasText: 'Game of Thrones' })).toContainText('Watching')
  })

  test('meera’s list is mostly series, and sam, who lives in the US, has five titles', async ({ page }) => {
    await signedInAs(page, 'meera')
    await page.goto('/watchlist?type=tv')
    await expect(tab(page, 'All', 5)).toBeVisible()
    await expect(rows(page).filter({ hasText: 'The Office' })).toContainText('9/10')

    await page.getByRole('button', { name: 'Account menu for Meera Iyer' }).click()
    await page.getByRole('menuitem', { name: 'Sign out' }).click()
    await signedInAs(page, 'sam')
    await page.goto('/watchlist')
    await expect(tab(page, 'All', 5)).toBeVisible()
    await expect(rows(page).filter({ hasText: 'Good Will Hunting' })).toContainText('It’s not your fault.'.replace('’', "'"))
  })

  test('karan is brand new: an empty list with the invitation to find something', async ({ page }) => {
    await signedInAs(page, 'karan')
    await page.goto('/watchlist')

    await expect(page.getByText('Your watchlist is empty')).toBeVisible()
    await expect(tab(page, 'All', 0)).toBeVisible()
  })

  test('the demo administrator has a password of its own: the shared one is refused', async ({ page }) => {
    await signInAs(page, 'admin') // with the shared demo password
    await expect(page.getByRole('alert')).toHaveText('Invalid username or password')

    await signInAs(page, 'admin', 'Control-Room-26!')
    await expect(page.getByRole('button', { name: 'Account menu for Site Admin' })).toBeVisible()
  })

  test('the demo password works only with the right username, and nothing else about them is exposed', async ({ page }) => {
    await signInAs(page, 'asha', 'Wrong-Pass-1!')
    await expect(page.getByRole('alert')).toHaveText('Invalid username or password')
    await expect(page).toHaveURL(/\/login/)
  })

  test('removing a seeded title from the list works like any other', async ({ page }) => {
    await signedInAs(page, 'sam')
    await page.goto('/watchlist')
    await expect(tab(page, 'All', 5)).toBeVisible()

    await page.getByRole('button', { name: 'Remove The Truman Show' }).click()
    await expect(rows(page)).toHaveCount(4)
    await expect(tab(page, 'All', 4)).toBeVisible()
  })
})
