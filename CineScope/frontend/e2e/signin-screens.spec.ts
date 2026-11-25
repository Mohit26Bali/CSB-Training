import { BACKEND, expect, test } from './fixtures'
import { seed } from './quotes'

/**
 * The sign-in and sign-up screens as a visitor sees them first: film and show pictures with famous lines beside the form. The
 * fake TMDB (e2e/tmdb-stub.mjs, CLASSICS) knows every title in the backend's seed file of quotes
 * (backend/src/main/resources/seed/quotes.psv, read here through e2e/quotes.ts); Deewaar is made to fail, so it can never appear. The backend picks
 * twelve quotes at random from all four categories, so these tests check what must hold for any pick, not a fixed list. Time is
 * driven by the browser clock so the rotation can be checked without waiting.
 */
test.use({ signedIn: false })

type Slide = { quoteId: number; id: number; type: string; category: string; title: string; quote: string; speaker: string; backdropPath: string }
const quote = (page: import('@playwright/test').Page) => page.getByTestId('showcase-quote')
const showcaseResponse = (page: import('@playwright/test').Page) => page.waitForResponse((r) => r.url().includes('/api/showcase') && r.request().method() === 'GET')

test.describe('sign-in and sign-up screens', () => {
  test('show a famous line, who says it and the film, next to the form', async ({ page }) => {
    await page.goto('/login')

    await expect(quote(page)).toBeVisible()
    await expect(quote(page)).toHaveText(/^“.+”$/)
    await expect(page.getByTestId('showcase-speaker')).not.toBeEmpty()
    await expect(page.getByTestId('showcase-film')).not.toBeEmpty()
    await expect(page.getByRole('heading', { level: 1, name: 'Welcome back' })).toBeVisible()
    await expect(page.getByText('Film data and images from TMDB. Not endorsed or certified by TMDB.')).toBeVisible()
  })

  test('the sign-up screen has the pictures too', async ({ page }) => {
    await page.goto('/register')

    await expect(quote(page)).toBeVisible()
    await expect(page.getByRole('heading', { level: 1, name: 'Create your account' })).toBeVisible()
  })

  test('the lines change by themselves, one title after another', async ({ page }) => {
    await page.clock.install()
    await page.goto('/login')
    await expect(quote(page)).toBeVisible()
    await page.clock.pauseAt(new Date(Date.now() + 1_000))

    const seen = new Set<string>([await quote(page).innerText()])
    for (let i = 0; i < 4; i++) {
      const before = await quote(page).innerText()
      await page.clock.runFor(6_600)
      await expect(quote(page)).not.toHaveText(before)
      seen.add(await quote(page).innerText())
    }
    expect(seen.size).toBe(5)
  })

  test('every line on show is paired with its real speaker and title, as written in the quotes file', async ({ page }) => {
    const response = showcaseResponse(page)
    await page.goto('/login')
    const slides = (await (await response).json()) as Slide[]

    expect(slides.length).toBeGreaterThan(0)
    for (const s of slides) {
      const match = seed.find((q) => q.line === s.quote && q.id === s.id && q.type === s.type)
      expect(match, `"${s.quote}" is in the quotes file for ${s.title}`).toBeTruthy()
      expect(s.speaker).toBe(match!.speaker)
      expect(s.title).toBe(match!.title) // the fake TMDB serves the title the file records
      expect(s.category).toBe(match!.category)
    }
  })

  test('people who asked for reduced motion get a still picture', async ({ page }) => {
    await page.emulateMedia({ reducedMotion: 'reduce' })
    await page.clock.install()
    await page.goto('/login')
    await expect(quote(page)).toBeVisible()
    await page.clock.pauseAt(new Date(Date.now() + 1_000))

    const first = await quote(page).innerText()
    await page.clock.runFor(60_000)
    await expect(quote(page)).toHaveText(first)
  })

  test('on a phone the pictures sit behind the form and the line is left out', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await page.goto('/login')

    await expect(page.getByRole('heading', { level: 1, name: 'Welcome back' })).toBeVisible()
    await expect(page.getByLabel('Username')).toBeVisible()
    await expect(quote(page)).toBeHidden()
    await expect(page.getByTestId('showcase')).toBeAttached()
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
    expect(overflow, 'no sideways scrolling').toBeLessThanOrEqual(0)
  })

  test('if the pictures cannot be loaded the form still works with a plain background', async ({ page }) => {
    await page.route('**/api/showcase**', (route) => route.fulfill({ status: 500, contentType: 'application/json', body: '{"message":"down"}' }))
    await page.goto('/login')

    await expect(page.getByText('Your next favourite film is waiting.')).toBeVisible()
    await expect(quote(page)).toHaveCount(0)
    await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible()
  })

  test('the next visit shows different lines: the browser remembers what it showed and asks for others', async ({ page }) => {
    const firstResponse = showcaseResponse(page)
    await page.goto('/login')
    const firstDone = await firstResponse
    const first = (await firstDone.json()) as Slide[]
    expect(new URL(firstDone.url()).searchParams.get('exclude'), 'the first visit has nothing to exclude').toBeNull()

    const secondResponse = showcaseResponse(page)
    await page.reload()
    const second = await secondResponse
    const asked = new URL(second.url()).searchParams.get('exclude')?.split(',').map(Number) ?? []
    const secondSlides = (await second.json()) as Slide[]

    expect(asked, 'the second visit tells the server what the first one showed').toEqual(expect.arrayContaining(first.map((s) => s.quoteId)))
    const firstIds = new Set(first.map((s) => s.quoteId))
    expect(secondSlides.filter((s) => firstIds.has(s.quoteId)), 'no line from the first visit comes back').toHaveLength(0)
  })

  test('with storage blocked the screens still work and simply show a fresh mix', async ({ page }) => {
    await page.addInitScript(() => {
      Object.defineProperty(window, 'localStorage', {
        get() {
          throw new Error('blocked')
        },
      })
    })
    await page.goto('/login')

    await expect(quote(page)).toBeVisible()
  })

  test('the endpoint behind them is open to visitors and returns a mix of twelve distinct lines, never the one that failed', async ({ page }) => {
    const res = await page.request.get(`${BACKEND}/api/showcase`)
    expect(res.status()).toBe(200)
    const slides = (await res.json()) as Slide[]

    expect(slides).toHaveLength(12)
    expect(new Set(slides.map((s) => s.quoteId)).size).toBe(12)
    expect(slides.some((s) => s.id === 22460 && s.type === 'movie'), 'Deewaar fails at the fake TMDB').toBe(false)
    expect(new Set(slides.map((s) => s.category)).size, 'several categories in one pick').toBeGreaterThanOrEqual(3)
    for (const s of slides) {
      expect(s.quote.length, s.title).toBeGreaterThan(3)
      expect(s.speaker).not.toBe('')
      expect(s.backdropPath).toMatch(/^\/classic-backdrop-\d+\.jpg$/)
    }
  })

  test('the endpoint leaves out the lines it is told were already shown', async ({ page }) => {
    const first = (await (await page.request.get(`${BACKEND}/api/showcase`)).json()) as Slide[]
    const ids = first.map((s) => s.quoteId).join(',')

    const second = (await (await page.request.get(`${BACKEND}/api/showcase`, { params: { exclude: ids } })).json()) as Slide[]

    expect(second).toHaveLength(12)
    expect(second.map((s) => s.quoteId).filter((id) => ids.split(',').includes(String(id)))).toEqual([])
  })
})
