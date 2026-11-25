import { cards, expect, filter, test } from './fixtures'

/**
 * Browse with filters (/browse). Facts about the fake catalogue used below: 24 movies, 5 of them Action; Action movies
 * are rated 5.0, 8.5, 7.9, 7.3 and 6.7; no Horror movie was released in 2019; the highest-rated movie is "Daybreak
 * Protocol" (8.8); 16 TV shows in 3 genres.
 */
test.describe('browse', () => {
  test('starts with the most popular movies and loads more', async ({ page }) => {
    await page.goto('/browse')

    await expect(page.getByRole('heading', { level: 1, name: 'Movies' })).toBeVisible()
    await expect(cards(page)).toHaveCount(8)
    await expect(cards(page).first()).toHaveAttribute('href', '/title/movie/1001')
    await expect(page.getByText('Showing 8 of 24')).toBeVisible()

    await page.getByRole('button', { name: 'Load more' }).click()
    await expect(cards(page)).toHaveCount(16)
  })

  test('filters by genre, and the URL records it', async ({ page }) => {
    await page.goto('/browse')
    await filter(page, 'Genre').selectOption({ label: 'Action' })

    await expect(page).toHaveURL(/genre=28/)
    await expect(page.getByText('Showing 5 of 5')).toBeVisible()
    await expect(cards(page)).toHaveCount(5)
  })

  test('filters combine: genre and minimum rating', async ({ page }) => {
    await page.goto('/browse?type=movie&genre=28')
    await filter(page, 'Minimum rating').selectOption({ label: '7+ ★' })

    await expect(page).toHaveURL(/minRating=7/)
    await expect(page.getByText('Showing 3 of 3')).toBeVisible() // 8.5, 7.9 and 7.3
  })

  test('filters by release year', async ({ page }) => {
    await page.goto('/browse')
    await filter(page, 'Year').selectOption('2026')

    await expect(page.getByText('Showing 3 of 3')).toBeVisible()
    await expect(cards(page).first()).toContainText('2026')
  })

  test('sorts by top rated', async ({ page }) => {
    await page.goto('/browse')
    await filter(page, 'Sort by').selectOption({ label: 'Top rated' })

    await expect(page).toHaveURL(/sort=rating/)
    await expect(cards(page).first()).toHaveAttribute('href', '/title/movie/1024') // Daybreak Protocol, 8.8
  })

  test('shows a clear message when nothing matches', async ({ page }) => {
    await page.goto('/browse?type=movie&genre=27&year=2019')

    await expect(page.getByText('Nothing matches these filters')).toBeVisible()
    await expect(cards(page)).toHaveCount(0)
  })

  test('"Clear filters" resets them and is disabled when none are set', async ({ page }) => {
    await page.goto('/browse')
    const clear = page.getByRole('button', { name: 'Clear filters' })
    await expect(clear).toBeDisabled()

    await filter(page, 'Genre').selectOption({ label: 'Action' })
    await expect(clear).toBeEnabled()
    await clear.click()

    await expect(page).not.toHaveURL(/genre=/)
    await expect(page.getByText('Showing 8 of 24')).toBeVisible()
  })

  test('switches to TV shows, with their own genre list', async ({ page }) => {
    await page.goto('/browse')
    await page.getByRole('radio', { name: 'TV shows' }).click()

    await expect(page).toHaveURL(/type=tv/)
    await expect(page.getByRole('heading', { level: 1, name: 'TV shows' })).toBeVisible()
    await expect(page.getByText('Showing 8 of 16')).toBeVisible()
    await expect(filter(page, 'Genre').locator('option', { hasText: 'Action & Adventure' })).toHaveCount(1)
    await expect(filter(page, 'Genre').locator('option', { hasText: 'Science Fiction' })).toHaveCount(0) // a movie-only genre
  })

  test('switching type drops a genre that does not exist for the other type', async ({ page }) => {
    await page.goto('/browse?type=movie&genre=878')
    await page.getByRole('radio', { name: 'TV shows' }).click()

    await expect(page).not.toHaveURL(/genre=/)
  })

  test('the filters survive a reload and can be shared as a link', async ({ page }) => {
    await page.goto('/browse?type=movie&genre=28&sort=rating&minRating=7')

    await expect(filter(page, 'Genre')).toHaveValue('28')
    await expect(filter(page, 'Sort by')).toHaveValue('rating')
    await expect(filter(page, 'Minimum rating')).toHaveValue('7')
    await expect(page.getByText('Showing 3 of 3')).toBeVisible()

    await page.reload()
    await expect(filter(page, 'Genre')).toHaveValue('28')
    await expect(page.getByText('Showing 3 of 3')).toBeVisible()
  })

  test('the header Movies / TV shows links open Browse and show which one is active', async ({ page }) => {
    await page.goto('/')
    const nav = page.getByRole('navigation', { name: 'Main' })
    await nav.getByRole('link', { name: 'TV shows' }).click()

    await expect(page).toHaveURL(/browse\?type=tv/)
    await expect(page.getByRole('heading', { level: 1, name: 'TV shows' })).toBeVisible()
  })
})
