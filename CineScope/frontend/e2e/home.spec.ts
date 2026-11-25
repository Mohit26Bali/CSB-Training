import { cards, expect, test } from './fixtures'

/**
 * The home page and the full-list pages, as a visitor sees them. Data comes from the fake TMDB (e2e/tmdb-stub.mjs):
 * the most popular title is the movie "The Last Lighthouse" (2019), so it is the hero.
 */
test.describe('home page', () => {
  test('shows the hero with the top trending title and the eight rows', async ({ page }) => {
    await page.goto('/')

    const hero = page.getByRole('region', { name: 'Featured title' })
    await expect(hero.getByRole('heading', { level: 1, name: 'The Last Lighthouse' })).toBeVisible()
    await expect(hero.getByText('Trending today')).toBeVisible()
    await expect(hero.getByText('2019')).toBeVisible()
    await expect(hero.getByText('The story of The Last Lighthouse')).toBeVisible()

    for (const row of ['Trending today', 'Trending this week', 'Popular movies', 'In cinemas now', 'Top rated movies', 'Coming soon', 'Popular TV shows', 'Top rated TV shows']) {
      await expect(page.getByRole('region', { name: row })).toBeVisible()
    }
    await expect(page).toHaveTitle('CineScope')
  })

  test('the hero rotates through six of today’s trending titles with dots, arrows and a pause button', async ({ page }) => {
    await page.goto('/')
    const hero = page.getByRole('region', { name: 'Featured title' })
    const dots = hero.getByRole('group', { name: 'Choose a featured title' }).getByRole('button')
    const heading = hero.getByRole('heading', { level: 1 })

    await expect(dots).toHaveCount(6)
    await expect(dots.nth(0)).toHaveAttribute('aria-current', 'true')
    const first = await heading.innerText()
    const firstLink = await hero.getByRole('link', { name: 'More info' }).getAttribute('href')

    await hero.getByRole('button', { name: 'Next featured title' }).click()
    await expect(dots.nth(1)).toHaveAttribute('aria-current', 'true')
    await expect(heading).not.toHaveText(first)
    await expect(hero.getByRole('link', { name: 'More info' })).not.toHaveAttribute('href', firstLink!)

    await dots.nth(4).click()
    await expect(dots.nth(4)).toHaveAttribute('aria-current', 'true')
    await hero.getByRole('button', { name: 'Previous featured title' }).click()
    await expect(dots.nth(3)).toHaveAttribute('aria-current', 'true')

    await dots.nth(0).click()
    await hero.getByRole('button', { name: 'Previous featured title' }).click() // wraps round to the last title
    await expect(dots.nth(5)).toHaveAttribute('aria-current', 'true')
    await hero.getByRole('button', { name: 'Next featured title' }).click() // ...and round again to the first
    await expect(heading).toHaveText(first)
  })

  test('the hero changes by itself, and stops while hovered, paused, or when motion is reduced', async ({ page }) => {
    await page.clock.install()
    await page.goto('/')
    const hero = page.getByRole('region', { name: 'Featured title' })
    const heading = hero.getByRole('heading', { level: 1 })
    const dots = hero.getByRole('group', { name: 'Choose a featured title' }).getByRole('button')
    await expect(dots).toHaveCount(6)
    await page.clock.pauseAt(new Date(Date.now() + 1_000)) // from here on time moves only when the test says so

    const first = await heading.innerText()
    await page.clock.runFor(7_200)
    await expect(heading).not.toHaveText(first)
    await expect(dots.nth(1)).toHaveAttribute('aria-current', 'true')

    // hovering holds it
    await hero.hover()
    const held = await heading.innerText()
    await page.clock.runFor(30_000)
    await expect(heading).toHaveText(held)
    await page.mouse.move(0, 0)
    await page.clock.runFor(7_200)
    await expect(heading).not.toHaveText(held)

    // the pause button holds it too, and play lets it go again
    await hero.getByRole('button', { name: 'Pause slideshow' }).click()
    await page.mouse.move(0, 0)
    const paused = await heading.innerText()
    await page.clock.runFor(30_000)
    await expect(heading).toHaveText(paused)
    await hero.getByRole('button', { name: 'Play slideshow' }).click()
    await page.mouse.move(0, 0)
    await page.clock.runFor(7_200)
    await expect(heading).not.toHaveText(paused)
  })

  test('people who asked for reduced motion get a banner that never moves by itself', async ({ page }) => {
    await page.emulateMedia({ reducedMotion: 'reduce' })
    await page.clock.install()
    await page.goto('/')
    const hero = page.getByRole('region', { name: 'Featured title' })
    const heading = hero.getByRole('heading', { level: 1 })
    await expect(heading).toBeVisible()
    await page.clock.pauseAt(new Date(Date.now() + 1_000))

    const first = await heading.innerText()
    await page.clock.runFor(60_000)
    await expect(heading).toHaveText(first)
    await hero.getByRole('button', { name: 'Next featured title' }).click() // still works by hand
    await expect(heading).not.toHaveText(first)
  })

  test('the trailer button plays the trailer of the title currently shown', async ({ page }) => {
    await page.goto('/')
    const hero = page.getByRole('region', { name: 'Featured title' })
    await hero.getByRole('button', { name: 'Next featured title' }).click()
    const href = await hero.getByRole('link', { name: 'More info' }).getAttribute('href')
    const id = href!.split('/').pop()

    await hero.getByRole('button', { name: 'Watch trailer' }).click()
    await expect(page.getByRole('dialog').locator('iframe')).toHaveAttribute('src', new RegExp(`youtube-nocookie\\.com/embed/(trailer|teaser)${id}`))
  })

  test('"Trending today" is its own row, not the week’s again, and links to its own list', async ({ page }) => {
    await page.goto('/')
    const today = page.getByRole('region', { name: 'Trending today' }).filter({ hasNot: page.getByRole('heading', { level: 1 }) })
    const week = page.getByRole('region', { name: 'Trending this week' })

    await expect(today.getByRole('link', { name: 'See all' })).toHaveAttribute('href', '/list/trending_today/all')
    const hrefs = (row: import('@playwright/test').Locator) => row.locator('a[href^="/title/"]').evaluateAll((links) => links.map((a) => a.getAttribute('href')))
    const todayTitles = await hrefs(today)
    const weekTitles = await hrefs(week)
    expect(todayTitles.length).toBeGreaterThan(0)
    expect(todayTitles[0]).toBe(weekTitles[0]) // the same number one...
    expect(todayTitles).not.toEqual(weekTitles) // ...but not the same row

    await today.getByRole('link', { name: 'See all' }).click()
    await expect(page.getByRole('heading', { level: 1, name: 'Trending today' })).toBeVisible()
  })

  test('a row lists poster cards with the title, year and kind, linking to the detail page', async ({ page }) => {
    await page.goto('/')

    const popular = page.getByRole('region', { name: 'Popular movies' })
    await expect(popular.getByRole('link')).toHaveCount(9) // 8 cards + "See all"
    const first = popular.getByRole('link', { name: 'The Last Lighthouse, 2019' })
    await expect(first).toHaveAttribute('href', '/title/movie/1001')
    await expect(first).toContainText('Movie')

    // shows are labelled as TV
    const tvRow = page.getByRole('region', { name: 'Popular TV shows' })
    await expect(tvRow.getByRole('link', { name: 'Harbor Lights, 2015' })).toContainText('TV')
  })

  test('the hero "More info" button opens the detail page', async ({ page }) => {
    await page.goto('/')
    await page.getByRole('link', { name: 'More info' }).click()

    await expect(page).toHaveURL('/title/movie/1001')
    await expect(page.getByRole('heading', { level: 1 })).toContainText('The Last Lighthouse')
  })

  test('the hero "Watch trailer" button plays the trailer in a dialog that Escape closes', async ({ page }) => {
    await page.goto('/')
    await page.getByRole('button', { name: 'Watch trailer' }).click()

    const dialog = page.getByRole('dialog')
    await expect(dialog).toBeVisible()
    await expect(dialog.locator('iframe')).toHaveAttribute('src', /youtube-nocookie\.com\/embed\/trailer1001/)
    await page.keyboard.press('Escape')
    await expect(dialog).toBeHidden()
  })

  test('each row has a "See all" link to its full list, and the arrows scroll the row', async ({ page }) => {
    await page.goto('/')
    const popular = page.getByRole('region', { name: 'Popular movies' })
    await expect(popular.getByRole('link', { name: 'See all' })).toHaveAttribute('href', '/list/popular/movie')
    await expect(page.getByRole('region', { name: 'Trending this week' }).getByRole('link', { name: 'See all' })).toHaveAttribute('href', '/list/trending/all')

    await popular.hover()
    const scroller = popular.locator('div.no-scrollbar')
    const before = await scroller.evaluate((el) => el.scrollLeft)
    await popular.getByRole('button', { name: 'Scroll Popular movies right' }).click()
    await expect.poll(() => scroller.evaluate((el) => el.scrollLeft)).toBeGreaterThan(before)
  })
})

test.describe('full lists ("See all")', () => {
  test('a list loads page by page', async ({ page }) => {
    await page.goto('/list/popular/movie')

    await expect(page.getByRole('heading', { level: 1, name: 'Popular movies' })).toBeVisible()
    await expect(cards(page)).toHaveCount(8)
    await expect(page.getByText('Showing 8 of 24')).toBeVisible()

    await page.getByRole('button', { name: 'Load more' }).click()
    await expect(cards(page)).toHaveCount(16)
    await page.getByRole('button', { name: 'Load more' }).click()
    await expect(cards(page)).toHaveCount(24)
    await expect(page.getByText('Showing 24 of 24')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Load more' })).toHaveCount(0) // nothing left
  })

  test('the trending list mixes movies and shows', async ({ page }) => {
    await page.goto('/list/trending/all')

    await expect(page.getByRole('heading', { level: 1, name: /Trending this week/ })).toBeVisible()
    await expect(cards(page).first()).toHaveAttribute('href', '/title/movie/1001')
    await expect(page.locator('main a[href^="/title/tv/"]').first()).toBeVisible()
  })

  test('an impossible combination shows the server’s explanation', async ({ page }) => {
    await page.goto('/list/now_playing/tv')

    await expect(page.getByRole('alert')).toContainText('We couldn’t load this')
    await expect(page.getByRole('alert')).toContainText("Category 'now_playing' is only available for [movie]")
  })
})
