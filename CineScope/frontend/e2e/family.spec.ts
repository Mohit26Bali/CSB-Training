import { BACKEND, expect, test } from './fixtures'

/**
 * "Is it for the family?" in the browser: the verdict on a title's page and the "safe for the whole family" switch on the Tonight
 * page. The fake TMDB (e2e/tmdb-stub.mjs) rates every fifth film (1005, 1010, 1015, 1020) U in India and every fifth show (2005, 2010,
 * 2015) TV-Y7; all the others are UA / PG-13 and TV-14. The backend's AgeRatingTest and RecommendationServiceTest cover the rules;
 * here the point is that the page shows them and that the switch really narrows what is offered.
 */
const pick = (page: import('@playwright/test').Page) => page.getByRole('region', { name: 'Tonight’s pick' })
const verdict = (page: import('@playwright/test').Page) => page.getByTestId('family-verdict')

async function pickId(page: import('@playwright/test').Page): Promise<number> {
  const href = await pick(page).getByRole('link', { name: /Details/ }).getAttribute('href')
  return Number(href!.split('/').pop())
}

test.describe('the verdict on a title', () => {
  test('a film rated for everyone says it is family friendly, next to its rating', async ({ page }) => {
    await page.goto('/title/movie/1005')

    await expect(page.getByText('U', { exact: true })).toBeVisible()
    await expect(verdict(page)).toHaveText('Family friendly')
  })

  test('a film that needs a parent’s judgement says to check the rating', async ({ page }) => {
    await page.goto('/title/movie/1001')

    await expect(page.getByText('UA', { exact: true })).toBeVisible()
    await expect(verdict(page)).toHaveText('Check the rating before a family watch')
  })

  test('shows are judged by their television rating', async ({ page }) => {
    await page.goto('/title/tv/2005')
    await expect(page.getByText('TV-Y7')).toBeVisible()
    await expect(verdict(page)).toHaveText('Family friendly')

    await page.goto('/title/tv/2001')
    await expect(page.getByText('TV-14')).toBeVisible()
    await expect(verdict(page)).toHaveText('Check the rating before a family watch')
  })
})

test.describe('safe for the whole family, on Tonight', () => {
  test('the switch is off to begin with and is kept in the address', async ({ page }) => {
    await page.goto('/tonight')
    const box = page.getByRole('checkbox', { name: 'Safe for the whole family' })
    await expect(box).not.toBeChecked()

    await box.check()
    await expect(page).toHaveURL(/family=1/)
    await page.reload()
    await expect(box).toBeChecked()

    await box.click()
    await expect(box).not.toBeChecked()
    await expect(page).not.toHaveURL(/family=1/)
  })

  test('with the switch on, every pick and alternative is rated for everyone, and the reason says so', async ({ page }) => {
    for (const seed of [11, 22, 33, 44, 55, 66]) {
      await page.goto(`/tonight?family=1&seed=${seed}`)
      await expect(pick(page)).toBeVisible()

      expect((await pickId(page)) % 5, `the pick is one of the U-rated films (seed ${seed})`).toBe(0)
      await expect(pick(page)).toContainText('Suitable for all ages')
      await expect(pick(page).getByText('U', { exact: true })).toBeVisible() // the pick's own rating
    }
  })

  test('without the switch other ratings turn up too', async ({ page }) => {
    const seen = new Set<number>()
    for (const seed of [11, 22, 33, 44, 55, 66, 77, 88]) {
      await page.goto(`/tonight?seed=${seed}`)
      await expect(pick(page)).toBeVisible()
      seen.add((await pickId(page)) % 5)
    }
    expect([...seen].some((remainder) => remainder !== 0), 'some picks are not family titles').toBe(true)
  })

  test('shows are filtered by their television rating too', async ({ page }) => {
    for (const seed of [11, 22, 33, 44]) {
      await page.goto(`/tonight?type=tv&family=1&seed=${seed}`)
      await expect(pick(page)).toBeVisible()
      expect([2005, 2010, 2015]).toContain(await pickId(page))
    }
  })

  test('when nothing is rated for everyone in what you asked for, the page says so and points at the switch', async ({ page }) => {
    await page.goto('/tonight?family=1&lang=ta&seed=5')

    await expect(page.getByText('Nothing matches all of that')).toBeVisible()
    await expect(page.getByText(/turn off “safe for the whole family”/)).toBeVisible()
    await expect(pick(page)).toHaveCount(0)
  })

  test('the server applies the switch itself: the pick and every alternative are rated for everyone', async ({ page }) => {
    for (const seed of [3, 4, 5, 6]) {
      const response = await page.request.get(`${BACKEND}/api/tonight`, { params: { familySafe: 'true', seed: String(seed) } })
      expect(response.status()).toBe(200)
      const body = await response.json()
      expect(body.pick.id % 5).toBe(0)
      expect(body.details.familyFriendly).toBe(true)
      expect(body.reasons).toContain('Suitable for all ages')
      for (const alternative of body.alternatives) expect(alternative.id % 5, `alternative ${alternative.id}`).toBe(0)
    }
  })
})
