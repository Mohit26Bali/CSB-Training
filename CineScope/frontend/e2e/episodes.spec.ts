import { BACKEND, expect, test } from './fixtures'

/**
 * The episode-rating heatmap on a show's page, as a signed-in person sees it (every test starts signed in as a fresh "Test Viewer",
 * see e2e/fixtures.ts). The fake TMDB (e2e/tmdb-stub.mjs, seasonEpisodes) gives every show eight episodes per season with ratings
 * that follow a fixed pattern, repeated here so the numbers on the page can be checked against an independent calculation. Show 2003
 * (three seasons) also has: S1E3 "The Turn" 9.6 (the best), S2E4 "The Dud" 4.2 (the worst), S2E6 with no votes, and S3E8 not aired
 * until 2999. Show 2001 has a single season. The backend's EpisodeApiTest tests the rules in depth.
 */
const heatmap = (page: import('@playwright/test').Page) => page.getByRole('region', { name: 'Episode ratings' })

/** The stub's rating of an episode, or null when the stub gives it no votes. */
function stubRating(show: number, season: number, episode: number): number | null {
  if (show === 2003) {
    if (season === 1 && episode === 3) return 9.6
    if (season === 2 && episode === 4) return 4.2
    if (season === 2 && episode === 6) return null
    if (season === 3 && episode === 8) return null
  }
  return Math.round((6.5 + ((season * 7 + episode * 3) % 20) / 10) * 10) / 10
}
const round1 = (x: number) => Math.round(x * 10) / 10
const mean = (values: number[]) => values.reduce((a, b) => a + b, 0) / values.length
function seasonAverage(show: number, season: number): number {
  const ratings = Array.from({ length: 8 }, (_, k) => stubRating(show, season, k + 1)).filter((r): r is number => r !== null)
  return round1(mean(ratings))
}

test.describe('the episode-rating heatmap', () => {
  test('a show shows one row per season and one cell per episode, without the specials', async ({ page }) => {
    await page.goto('/title/tv/2003')

    await expect(heatmap(page).getByRole('heading', { name: 'Episode ratings' })).toBeVisible()
    const rows = heatmap(page).locator('tbody tr')
    await expect(rows).toHaveCount(3)
    await expect(heatmap(page).getByRole('rowheader', { name: 'S1' })).toBeVisible()
    await expect(heatmap(page).getByRole('rowheader', { name: 'S0' })).toHaveCount(0)
    for (let season = 1; season <= 3; season++) await expect(heatmap(page).locator(`tr[data-season="${season}"] td[data-episode]`)).toHaveCount(8)
  })

  test('every cell prints its rating, and the numbers match an independent calculation', async ({ page }) => {
    await page.goto('/title/tv/2003')

    for (const [season, episode] of [[1, 1], [1, 5], [2, 1], [2, 8], [3, 4]]) {
      const cell = heatmap(page).locator(`tr[data-season="${season}"] td[data-episode="${episode}"]`)
      const expected = stubRating(2003, season, episode)!
      await expect(cell).toHaveAttribute('data-rating', String(expected))
      await expect(cell).toContainText(expected.toFixed(1))
    }
    for (let season = 1; season <= 3; season++) {
      await expect(heatmap(page).getByTestId(`season-average-${season}`)).toHaveText(seasonAverage(2003, season).toFixed(1))
    }
    const all = [1, 2, 3].flatMap((s) => Array.from({ length: 8 }, (_, k) => stubRating(2003, s, k + 1))).filter((r): r is number => r !== null)
    await expect(heatmap(page).getByTestId('heatmap-average')).toHaveText(round1(mean(all)).toFixed(1))
    await expect(heatmap(page)).toContainText(`across ${all.length} rated episodes`)
  })

  test('the best and weakest episodes are named', async ({ page }) => {
    await page.goto('/title/tv/2003')

    await expect(heatmap(page).getByTestId('heatmap-best')).toHaveText('S1E3 “The Turn” 9.6')
    await expect(heatmap(page).getByTestId('heatmap-worst')).toHaveText('S2E4 “The Dud” 4.2')
  })

  test('the strongest and weakest seasons follow the season averages', async ({ page }) => {
    await page.goto('/title/tv/2003')
    const averages = [1, 2, 3].map((s) => ({ season: s, average: seasonAverage(2003, s) }))
    const strongest = averages.reduce((a, b) => (b.average > a.average ? b : a))
    const weakest = averages.reduce((a, b) => (b.average < a.average ? b : a))

    await expect(heatmap(page).getByTestId('heatmap-strongest')).toHaveText(`Season ${strongest.season} · ${strongest.average.toFixed(1)}`)
    await expect(heatmap(page).getByTestId('heatmap-weakest')).toHaveText(`Season ${weakest.season} · ${weakest.average.toFixed(1)}`)
  })

  test('colour follows the rating: green for the best episode, red for the worst', async ({ page }) => {
    await page.goto('/title/tv/2003')
    const channels = async (selector: string) =>
      heatmap(page).locator(selector).evaluate((el) => (getComputedStyle(el).backgroundColor.match(/\d+(\.\d+)?/g) ?? []).slice(0, 3).map(Number))

    const [br, bg] = await channels('tr[data-season="1"] td[data-episode="3"]') // 9.6
    const [wr, wg] = await channels('tr[data-season="2"] td[data-episode="4"]') // 4.2
    expect(bg, 'the best episode is greener than red').toBeGreaterThan(br)
    expect(wr, 'the worst episode is redder than green').toBeGreaterThan(wg)
  })

  test('an episode nobody has rated is a dash and one not aired yet says soon, never a zero', async ({ page }) => {
    await page.goto('/title/tv/2003')

    const unrated = heatmap(page).locator('tr[data-season="2"] td[data-episode="6"]')
    await expect(unrated).toHaveAttribute('data-state', 'unrated')
    await expect(unrated).toContainText('–')
    await expect(unrated).not.toHaveAttribute('data-rating', /.*/)
    const unaired = heatmap(page).locator('tr[data-season="3"] td[data-episode="8"]')
    await expect(unaired).toHaveAttribute('data-state', 'unaired')
    await expect(unaired).toContainText('soon')
    await expect(heatmap(page)).not.toContainText('0.0')
  })

  test('each cell says what it is: season, episode, name, votes', async ({ page }) => {
    await page.goto('/title/tv/2003')

    const cell = heatmap(page).locator('tr[data-season="2"] td[data-episode="4"]')
    await expect(cell).toHaveAttribute('title', /S2E4 · The Dud · 4\.2\/10 \(300 votes\)/)
    await expect(heatmap(page).getByRole('cell', { name: /Season 2, episode 4, “The Dud”/ })).toBeVisible() // what a screen reader reads
    await expect(heatmap(page).getByRole('cell', { name: /Season 3, episode 8.*not aired yet/ })).toBeVisible()
    await expect(heatmap(page).getByRole('table', { name: 'Average rating of each episode, by season' })).toBeVisible()
  })

  test('a one-season show still shows its episodes and best and worst, but compares no seasons', async ({ page }) => {
    await page.goto('/title/tv/2001')

    await expect(heatmap(page).locator('tbody tr')).toHaveCount(1)
    await expect(heatmap(page).getByTestId('heatmap-best')).toBeVisible()
    await expect(heatmap(page).getByTestId('heatmap-worst')).toBeVisible()
    await expect(heatmap(page).getByTestId('heatmap-strongest')).toHaveCount(0)
    await expect(heatmap(page).getByTestId('heatmap-weakest')).toHaveCount(0)
  })

  test('a film has no heatmap', async ({ page }) => {
    await page.goto('/title/movie/1001')
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()

    await expect(heatmap(page)).toHaveCount(0)
  })

  test('a season that could not be loaded says so and the rest still show', async ({ page }) => {
    await page.route('**/api/titles/tv/2003/episodes', async (route) => {
      const response = await route.fetch()
      const body = await response.json()
      body.seasons[2] = { number: 3, average: null, unavailable: true, episodes: [] }
      await route.fulfill({ response, json: body })
    })
    await page.goto('/title/tv/2003')

    await expect(heatmap(page).getByText('This season could not be loaded right now.')).toBeVisible()
    await expect(heatmap(page).locator('tr[data-season="1"] td[data-episode]')).toHaveCount(8)
  })

  test('when nobody has rated anything the page says so instead of drawing an empty grid of numbers', async ({ page }) => {
    await page.route('**/api/titles/tv/2003/episodes', async (route) => {
      const response = await route.fetch()
      const body = await response.json()
      body.ratedCount = 0
      body.average = null
      body.best = null
      body.worst = null
      for (const season of body.seasons) {
        season.average = null
        for (const episode of season.episodes) episode.rating = null
      }
      await route.fulfill({ response, json: body })
    })
    await page.goto('/title/tv/2003')

    await expect(heatmap(page)).toContainText('Nobody has rated the episodes yet.')
    await expect(heatmap(page).getByTestId('heatmap-best')).toHaveCount(0)
  })

  test('on a phone the page does not scroll sideways', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await page.goto('/title/tv/2003')
    await expect(heatmap(page)).toBeVisible()

    expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(0)
  })
})

test.describe('the heatmap for visitors', () => {
  test.use({ signedIn: false })

  test('the episodes endpoint needs a login', async ({ page }) => {
    expect((await page.request.get(`${BACKEND}/api/titles/tv/2003/episodes`)).status()).toBe(401)
  })
})
