import { BACKEND, expect, STUB, stubStats, test } from './fixtures'

/**
 * Cross-cutting behaviour: failures, caching, the secret token, API documentation, theme and the phone layout.
 * The special ids come from the fake TMDB: movie 666 -> HTTP 500, movie 667 -> HTTP 429, anything unknown -> 404.
 */
test.describe('when TMDB misbehaves', () => {
  test('an unknown title is "not found"', async ({ page }) => {
    await page.goto('/title/movie/99999')

    await expect(page.getByText('Title not found')).toBeVisible()
  })

  test('a TMDB server error shows a retryable error, not a blank page', async ({ page }) => {
    await page.goto('/title/movie/666')

    const alert = page.getByRole('alert')
    await expect(alert).toContainText('We couldn’t load this')
    await expect(alert).toContainText('TMDB could not be reached right now')
    await expect(alert.getByRole('button', { name: 'Try again' })).toBeVisible()
  })

  test('TMDB rate limiting is reported as "busy"', async ({ page }) => {
    await page.goto('/title/movie/667')

    await expect(page.getByRole('alert')).toContainText('TMDB is busy right now')
  })

  test('a malformed link shows a friendly message', async ({ page }) => {
    await page.goto('/title/book/1')
    await expect(page.getByText('That title doesn’t exist')).toBeVisible()

    await page.goto('/title/movie/abc')
    await expect(page.getByText('That title doesn’t exist')).toBeVisible()
  })

  test('an unknown page is the 404 page', async ({ page }) => {
    await page.goto('/there-is-no-such-page')

    await expect(page.getByText('This scene was cut')).toBeVisible()
    await page.getByRole('link', { name: 'Back to the home page' }).click()
    await expect(page).toHaveURL('/')
  })
})

test.describe('the backend between the browser and TMDB', () => {
  test('caches TMDB answers: two requests, one call to TMDB', async ({ page }) => {
    await page.request.get(`${STUB}/__reset`)

    const first = await page.request.get('/api/titles/movie/1020')
    const second = await page.request.get('/api/titles/movie/1020')

    expect(first.status()).toBe(200)
    expect(second.status()).toBe(200)
    expect(await second.json()).toEqual(await first.json())
    expect((await stubStats(page)).counts['/3/movie/1020']).toBe(1)
  })

  test('keeps the TMDB token on the server', async ({ page }) => {
    await page.request.get(`${STUB}/__reset`)
    const response = await page.request.get('/api/titles/movie/1006')

    expect(await response.text()).not.toContain('e2e-token') // never in anything the browser receives
    expect((await stubStats(page)).lastAuth).toBe('Bearer e2e-token') // but TMDB did receive it
  })

  test('answers bad requests itself, without bothering TMDB', async ({ page }) => {
    await page.request.get(`${STUB}/__reset`)

    for (const url of ['/api/titles/book/1', '/api/titles?category=bogus', '/api/titles?page=0', '/api/search?query=%20', '/api/discover?sort=zzz', '/api/genres?type=anime']) {
      const response = await page.request.get(url)
      expect(response.status(), url).toBe(400)
      expect((await response.json()).message).toBeTruthy()
    }
    expect(Object.keys((await stubStats(page)).counts)).toHaveLength(0)
  })

  test('every error has the same JSON shape', async ({ page }) => {
    const response = await page.request.get('/api/titles/movie/99999')

    expect(response.status()).toBe(404)
    expect(await response.json()).toMatchObject({ status: 404, error: 'Not Found', message: 'Not found' })
  })

  test('the home endpoint returns the heroes and all eight rows in one response', async ({ page }) => {
    const home = await (await page.request.get('/api/home')).json()

    expect(home.hero.title).toBe('The Last Lighthouse')
    expect(home.heroes).toHaveLength(6)
    expect(home.heroes[0].id).toBe(home.hero.id)
    expect(new Set(home.heroes.map((h: { id: number }) => h.id)).size).toBe(6) // no title twice
    expect(home.rows.map((r: { key: string }) => r.key)).toEqual([
      'trending-today', 'trending', 'popular-movies', 'now-playing', 'top-rated-movies', 'upcoming', 'popular-tv', 'top-rated-tv',
    ])
  })
})

test.describe('API documentation', () => {
  test('Swagger UI and the OpenAPI document are served', async ({ page }) => {
    const doc = await (await page.request.get(`${BACKEND}/v3/api-docs`)).json()
    expect(doc.info.title).toBe('CineScope API')
    expect(Object.keys(doc.paths)).toEqual(expect.arrayContaining(['/api/home', '/api/search', '/api/titles/{type}/{id}', '/api/discover']))

    await page.goto(`${BACKEND}/swagger-ui/index.html`)
    await expect(page.getByRole('heading', { name: /CineScope API/ })).toBeVisible()
  })
})

test.describe('theme', () => {
  test('is dark by default, can be switched to light, and is remembered', async ({ page }) => {
    await page.goto('/')
    await expect(page.locator('html')).toHaveClass(/dark/)

    await page.getByRole('button', { name: 'Switch to light mode' }).click()
    await expect(page.locator('html')).not.toHaveClass(/dark/)

    await page.reload()
    await expect(page.locator('html')).not.toHaveClass(/dark/)
    await page.getByRole('button', { name: 'Switch to dark mode' }).click()
    await expect(page.locator('html')).toHaveClass(/dark/)
  })
})

test.describe('on a phone', () => {
  test.use({ viewport: { width: 390, height: 844 } })

  test('the home page fits the screen and shows the section links under the header', async ({ page }) => {
    await page.goto('/')

    await expect(page.getByRole('heading', { level: 1, name: 'The Last Lighthouse' })).toBeVisible()
    await expect(page.getByRole('navigation', { name: 'Sections' }).getByRole('link', { name: 'TV shows' })).toBeVisible()
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
    expect(overflow).toBeLessThanOrEqual(0)
  })

  test('search opens from the header icon', async ({ page }) => {
    await page.goto('/')
    await expect(page.locator('#site-search')).toBeHidden()

    await page.getByRole('button', { name: 'Toggle search' }).click()
    await page.locator('#site-search').fill('lighthouse')
    await page.locator('#site-search').press('Enter')

    await expect(page).toHaveURL(/search\?q=lighthouse/)
  })

  test('the detail page fits the screen', async ({ page }) => {
    await page.goto('/title/movie/1001')

    await expect(page.getByRole('heading', { level: 1 })).toContainText('The Last Lighthouse')
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
    expect(overflow).toBeLessThanOrEqual(0)
  })

  test('browse filters stack in one column and still work', async ({ page }) => {
    await page.goto('/browse')
    await page.locator('label', { hasText: 'Genre' }).locator('select').selectOption({ label: 'Action' })

    await expect(page.getByText('Showing 5 of 5')).toBeVisible()
  })
})
