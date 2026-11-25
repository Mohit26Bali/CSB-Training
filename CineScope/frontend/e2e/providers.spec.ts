import { expect, stubStats, test } from './fixtures'

/**
 * "Where to watch" on the title page: India first, the four groups, another country, a title nobody lists in India,
 * the JustWatch credit, a failing lookup that must not break the rest of the page, and that the choice is remembered.
 * The fake TMDB decides availability from the title id (see providersFor in e2e/tmdb-stub.mjs): 1001 and 1004 have
 * everything in India, 1002 and 1005 only the US, 1003 only rentals in India, and 1006 fails this lookup alone.
 */
const section = (page: import('@playwright/test').Page) => page.getByRole('region', { name: 'Where to watch' })
const group = (page: import('@playwright/test').Page, name: string) => section(page).getByRole('group', { name })

test.describe('where to watch', () => {
  test('shows India’s services in stream / free / rent / buy groups, most prominent first', async ({ page }) => {
    await page.goto('/title/movie/1001')

    await expect(section(page).getByLabel('Country')).toHaveValue('IN')
    await expect(group(page, 'Stream').getByRole('listitem')).toHaveText(['Netflix', 'JioHotstar'])
    await expect(group(page, 'Free').getByRole('listitem')).toHaveText(['MX Player', 'Pluto'])
    await expect(group(page, 'Rent').getByRole('listitem')).toHaveText(['Apple TV'])
    await expect(group(page, 'Buy').getByRole('listitem')).toHaveText(['Google Play Movies'])
  })

  test('credits JustWatch and links to TMDB for more options', async ({ page }) => {
    await page.goto('/title/movie/1001')

    await expect(section(page).getByText('Availability data provided by JustWatch.')).toBeVisible()
    const more = section(page).getByRole('link', { name: /More options/ })
    await expect(more).toHaveAttribute('href', /themoviedb\.org\/title\/1001\/watch\?locale=IN/)
    await expect(more).toHaveAttribute('target', '_blank')
  })

  test('a group with no services is left out', async ({ page }) => {
    await page.goto('/title/movie/1003') // India: rent only
    await expect(group(page, 'Rent').getByRole('listitem')).toHaveText(['Apple TV'])
    await expect(group(page, 'Stream')).toHaveCount(0)
    await expect(group(page, 'Free')).toHaveCount(0)
    await expect(group(page, 'Buy')).toHaveCount(0)
  })

  test('a title with nothing listed for India says so, and another country can be chosen', async ({ page }) => {
    await page.goto('/title/movie/1002') // only the US has data
    await expect(section(page).getByText('No streaming, rental or purchase options are listed for India yet.')).toBeVisible()

    await section(page).getByLabel('Country').selectOption('US')
    await expect(group(page, 'Stream').getByRole('listitem')).toHaveText(['Hulu'])
    await expect(section(page).getByRole('link', { name: /More options/ })).toHaveAttribute('href', /locale=US/)
  })

  test('the chosen country is remembered for the next title', async ({ page }) => {
    await page.goto('/title/movie/1002')
    await section(page).getByLabel('Country').selectOption('US')
    await expect(group(page, 'Stream')).toBeVisible()

    await page.goto('/title/movie/1005') // again only the US
    await expect(section(page).getByLabel('Country')).toHaveValue('US')
    await expect(group(page, 'Stream').getByRole('listitem')).toHaveText(['Hulu'])
  })

  test('TV shows have it too', async ({ page }) => {
    await page.goto('/title/tv/2002') // only the US, like 1002
    await section(page).getByLabel('Country').selectOption('US')
    await expect(group(page, 'Stream').getByRole('listitem')).toHaveText(['Hulu'])
  })

  test('one lookup is shared by every country switch (TMDB is asked once per title)', async ({ page }) => {
    await page.goto('/title/movie/1004')
    await expect(group(page, 'Stream')).toBeVisible()
    await section(page).getByLabel('Country').selectOption('US')
    await expect(group(page, 'Stream').getByRole('listitem')).toHaveText(['Hulu'])
    await section(page).getByLabel('Country').selectOption('IN')
    await expect(group(page, 'Stream').getByRole('listitem')).toHaveText(['Netflix', 'JioHotstar'])

    const stats = await stubStats(page)
    expect(stats.counts['/3/movie/1004/watch/providers']).toBe(1)
  })

  test('if the lookup fails the rest of the page still works and it can be retried', async ({ page }) => {
    await page.goto('/title/movie/1006') // detail works, the providers call answers 500
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
    await expect(section(page).getByRole('alert')).toContainText('Couldn’t check where to watch')
    await expect(page.getByRole('region', { name: 'Overview' })).toBeVisible()

    await section(page).getByRole('button', { name: 'Try again' }).click()
    await expect(section(page).getByRole('alert')).toBeVisible() // still failing, but the page is intact
  })
})
