import { cards, expect, test } from './fixtures'

/**
 * The detail page of a movie and of a TV show. Movie 1001 is "The Last Lighthouse" (2019, Action, 90 minutes, Hindi,
 * Indian rating UA, budget $1M); movie 1002 has no website; show 2001 is "Harbor Lights" (2015, 1 season, only a US rating).
 */
test.describe('movie details', () => {
  test('shows the facts of the movie', async ({ page }) => {
    await page.goto('/title/movie/1001')

    await expect(page.getByRole('heading', { level: 1 })).toHaveText('The Last Lighthouse (2019)')
    await expect(page).toHaveTitle('The Last Lighthouse · CineScope')
    await expect(page.getByText('Tagline of The Last Lighthouse')).toBeVisible()
    await expect(page.getByText('UA', { exact: true })).toBeVisible() // the Indian age rating, not the US PG-13
    await expect(page.getByText('1h 30m')).toBeVisible()
    await expect(page.getByRole('link', { name: 'Action' })).toBeVisible()
    await expect(page.getByText('100 votes')).toBeVisible()
    await expect(page.getByRole('img', { name: 'User score 5.0 out of 10' })).toBeVisible()
    await expect(page.locator('p', { hasText: 'Director:' })).toContainText('Ishaan Verma')
    await expect(page.getByText('The story of The Last Lighthouse', { exact: false })).toBeVisible()
  })

  test('shows the facts panel with formatted values', async ({ page }) => {
    await page.goto('/title/movie/1001')

    const facts = page.getByRole('region', { name: 'Facts' })
    await expect(facts).toContainText('Released')
    await expect(facts).toContainText('Hindi') // the language code "hi", spelled out
    await expect(facts).toContainText('15 Jan 2019')
    await expect(facts).toContainText('$1,000,000')
    await expect(facts).toContainText('$2,500,000')
    await expect(facts).not.toContainText('Seasons') // movies have none
  })

  test('lists the top cast, using initials when there is no photo', async ({ page }) => {
    await page.goto('/title/movie/1001')

    const cast = page.getByRole('region', { name: 'Top cast' })
    await expect(cast).toContainText('Ananya Rao')
    await expect(cast).toContainText('Dr. Iyer')
    await expect(cast).toContainText('MH') // Marcus Hale has no photo in the data
  })

  test('has an official-site link only when the movie has a website', async ({ page }) => {
    await page.goto('/title/movie/1001')
    await expect(page.getByRole('link', { name: 'Official site' })).toHaveAttribute('href', 'https://example.test/1001')

    await page.goto('/title/movie/1002')
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Midnight Orchard')
    await expect(page.getByRole('link', { name: 'Official site' })).toHaveCount(0)
    await expect(page.getByRole('region', { name: 'Facts' })).toContainText('English')
  })

  test('plays the main trailer and any listed video in a dialog', async ({ page }) => {
    await page.goto('/title/movie/1001')

    await page.getByRole('button', { name: 'Watch trailer' }).click()
    await expect(page.getByRole('dialog').locator('iframe')).toHaveAttribute('src', /embed\/trailer1001/)
    await page.getByRole('button', { name: 'Close' }).click()
    await expect(page.getByRole('dialog')).toBeHidden()

    const videos = page.getByRole('region', { name: 'Videos' })
    await expect(videos.getByRole('button')).toHaveCount(2) // trailer + teaser
    await videos.getByRole('button', { name: 'Play Teaser' }).click()
    await expect(page.getByRole('dialog').locator('iframe')).toHaveAttribute('src', /embed\/teaser1001/)
  })

  test('a genre chip opens Browse filtered to that genre', async ({ page }) => {
    await page.goto('/title/movie/1001')
    await page.getByRole('link', { name: 'Action' }).click()

    await expect(page).toHaveURL(/\/browse\?type=movie&genre=28/)
    await expect(page.getByText('Showing 5 of 5')).toBeVisible() // five Action movies in the data
  })

  test('similar titles link to their own detail pages', async ({ page }) => {
    await page.goto('/title/movie/1001')

    const similar = page.getByRole('region', { name: 'More like The Last Lighthouse' })
    await expect(similar.getByRole('link')).toHaveCount(3)
    await similar.getByRole('link', { name: /Midnight Orchard/ }).click()

    await expect(page).toHaveURL('/title/movie/1002')
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Midnight Orchard')
    await expect(page.getByRole('region', { name: 'More like Midnight Orchard' })).toBeVisible()
  })
})

test.describe('TV show details', () => {
  test('shows seasons, episodes, creators and falls back to the US rating', async ({ page }) => {
    await page.goto('/title/tv/2001')

    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Harbor Lights (2015)')
    await expect(page.getByText('TV show', { exact: true })).toBeVisible()
    await expect(page.getByText('TV-14')).toBeVisible() // no Indian rating, so the US one
    await expect(page.getByText('42m / episode')).toBeVisible()
    await expect(page.locator('p', { hasText: 'Created by:' })).toContainText('Rhea Kapoor')

    const facts = page.getByRole('region', { name: 'Facts' })
    await expect(facts).toContainText('Seasons')
    await expect(facts).toContainText('Episodes')
    await expect(facts).not.toContainText('Budget')
  })

  test('similar shows keep their type in the link', async ({ page }) => {
    await page.goto('/title/tv/2001')

    await expect(cards(page).first()).toHaveAttribute('href', /\/title\/tv\/\d+/)
  })
})
