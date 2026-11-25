import { cards, expect, test } from './fixtures'

/**
 * Search. Facts about the fake catalogue: "lighthouse" matches one movie; "harbo" matches the movie "Quiet Harbour" and the
 * show "Harbor Lights"; "paper" matches the movie "Paper Moons" and the show "Paper Kingdoms"; "mara" matches only the
 * person "Mara Whitfield"; "zzzz" matches nothing.
 */
test.describe('search', () => {
  test('finds a movie by part of its name', async ({ page }) => {
    await page.goto('/search?q=lighthouse')

    await expect(page.getByRole('heading', { level: 1, name: 'Search' })).toBeVisible()
    await expect(cards(page)).toHaveCount(1)
    await expect(cards(page).first()).toHaveAttribute('href', '/title/movie/1001')
    await expect(page).toHaveTitle('Search: lighthouse · CineScope')
  })

  test('typing in the page search box updates the address and the results', async ({ page }) => {
    await page.goto('/search')
    await expect(page.getByText('Find something to watch')).toBeVisible()

    await page.locator('#search-page-input').fill('lighthouse')
    await expect(page).toHaveURL(/q=lighthouse/)
    await expect(cards(page)).toHaveCount(1)

    await page.locator('#search-page-input').fill('')
    await expect(page.getByText('Find something to watch')).toBeVisible()
  })

  test('the header search box goes to the search page', async ({ page }) => {
    await page.goto('/')
    await page.locator('#site-search').fill('paper')
    await page.locator('#site-search').press('Enter')

    await expect(page).toHaveURL(/\/search\?q=paper/)
    await expect(cards(page)).toHaveCount(2)
    await expect(page.locator('#search-page-input')).toHaveValue('paper')
  })

  test('tabs split the results into movies, TV shows and people with counts', async ({ page }) => {
    await page.goto('/search?q=harbo')

    await expect(page.getByRole('tab', { name: /All/ })).toContainText('2')
    await expect(page.getByRole('tab', { name: /Movies/ })).toContainText('1')
    await expect(page.getByRole('tab', { name: /TV shows/ })).toContainText('1')
    await expect(page.getByRole('tab', { name: /People/ })).toContainText('0')

    await page.getByRole('tab', { name: /TV shows/ }).click()
    await expect(cards(page)).toHaveCount(1)
    await expect(cards(page).first()).toHaveAttribute('href', '/title/tv/2001')

    await page.getByRole('tab', { name: /Movies/ }).click()
    await expect(cards(page).first()).toHaveAttribute('href', '/title/movie/1005')
  })

  test('shows people with what they are known for, and puts a best-match person first', async ({ page }) => {
    await page.goto('/search?q=mara')

    const people = page.getByRole('region', { name: 'People' })
    await expect(people).toContainText('Mara Whitfield')
    await expect(people).toContainText('Known for The Last Lighthouse, Paper Moons')
    await expect(cards(page)).toHaveCount(0)

    // a person without a photo still gets a tile (initials)
    await page.goto('/search?q=theo')
    await expect(page.getByRole('region', { name: 'People' })).toContainText('TM')
  })

  test('explains when nothing was found', async ({ page }) => {
    await page.goto('/search?q=zzzz')

    await expect(page.getByText('No results for “zzzz”')).toBeVisible()
    await expect(cards(page)).toHaveCount(0)
  })

  test('a result opens its detail page', async ({ page }) => {
    await page.goto('/search?q=lighthouse')
    await cards(page).first().click()

    await expect(page).toHaveURL('/title/movie/1001')
  })

  test('special characters in the text reach the server intact', async ({ page }) => {
    // "&" and "%" would break a hand-built query string; the app must encode them exactly once
    await page.goto('/search?q=' + encodeURIComponent('salt & ember'))
    await expect(page.getByText('No results for “salt & ember”')).toBeVisible()

    await page.goto('/search?q=' + encodeURIComponent('salt and ember'))
    await expect(cards(page)).toHaveCount(1) // "Salt and Ember"
  })
})
