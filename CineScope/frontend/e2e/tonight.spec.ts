import { expect, test } from './fixtures'

/**
 * "What should I watch tonight?" in the browser: the wishes form, a pick with its reasons, where to watch it and
 * alternatives; "Show me another"; that every wish really narrows the answer; the empty answer; state kept in the address;
 * and what a signed-in user changes. The fake TMDB (e2e/tmdb-stub.mjs) makes the outcomes predictable:
 *   - movies 1001... have genres cycling Action, Drama, Comedy, Sci-Fi, Horror; runtime 90 + position; original language
 *     Hindi when the position is a multiple of 3, otherwise English; TMDB has no Thriller or Romance genre here, so those
 *     moods match nothing;
 *   - a title streams in India when its position is a multiple of 3 (and then has Netflix and JioHotstar).
 */
const wishes = (page: import('@playwright/test').Page) => page.getByRole('region', { name: 'Your wishes' })
const pick = (page: import('@playwright/test').Page) => page.getByRole('region', { name: 'Tonight’s pick' })

async function pickId(page: import('@playwright/test').Page): Promise<number> {
  const href = await pick(page).getByRole('link', { name: /Details/ }).getAttribute('href')
  return Number(href!.split('/').pop())
}

test.describe('what to watch tonight', () => {
  test('the page is in the header and starts with the wishes but no result', async ({ page }) => {
    await page.goto('/')
    await page.getByRole('navigation', { name: 'Main' }).getByRole('link', { name: 'Tonight' }).click()

    await expect(page).toHaveURL('/tonight')
    await expect(page.getByRole('heading', { level: 1, name: 'What should I watch tonight?' })).toBeVisible()
    await expect(wishes(page).getByRole('button', { name: 'Find something' })).toBeVisible()
    await expect(pick(page)).toHaveCount(0)
  })

  test('finding something shows one pick with its reasons, where to watch and alternatives', async ({ page }) => {
    await page.goto('/tonight?mood=laugh')
    await wishes(page).getByRole('button', { name: 'Find something' }).click()

    await expect(pick(page)).toBeVisible()
    await expect(pick(page).getByRole('heading', { level: 2 })).not.toBeEmpty()
    await expect(pick(page).getByText('Comedy', { exact: true })).toBeVisible() // genre chip of the details
    await expect(pick(page).getByRole('list', { name: 'Why this one' }).getByText('Make me laugh')).toBeVisible()
    await expect(pick(page).getByRole('link', { name: /Details/ })).toHaveAttribute('href', /\/title\/movie\/\d+/)
    await expect(page.getByRole('region', { name: 'Or maybe one of these' })).toBeVisible()
    await expect(page).toHaveURL(/seed=\d+/)
  })

  test('"Show me another" draws again with a new seed, and the same seed repeats the answer after a reload', async ({ page }) => {
    await page.goto('/tonight?seed=4242')
    await expect(pick(page)).toBeVisible()
    const first = await pickId(page)

    await page.reload()
    await expect(pick(page)).toBeVisible()
    expect(await pickId(page)).toBe(first)

    const seeds = new Set<string>(['4242'])
    const picks = new Set<number>([first])
    for (let i = 0; i < 6; i++) {
      await wishes(page).getByRole('button', { name: 'Show me another' }).click()
      await expect(page).not.toHaveURL(new RegExp(`seed=(${[...seeds].join('|')})(&|$)`))
      seeds.add(new URL(page.url()).searchParams.get('seed')!)
      await expect(wishes(page).getByRole('button', { name: 'Show me another' })).toBeEnabled() // the new answer has arrived
      await expect(pick(page)).toBeVisible()
      picks.add(await pickId(page))
    }
    expect(picks.size).toBeGreaterThan(1)
  })

  test('a mood only gives titles of that kind', async ({ page }) => {
    await page.goto('/tonight?mood=scary&seed=1')
    await expect(pick(page)).toBeVisible()
    await expect(pick(page).getByText('Horror', { exact: true })).toBeVisible()
    for (const seed of [2, 3, 4]) {
      await page.goto(`/tonight?mood=scary&seed=${seed}`)
      await expect(pick(page).getByText('Horror', { exact: true })).toBeVisible()
    }
  })

  test('language and "only what I can stream" narrow the answer, and the pick really streams in India', async ({ page }) => {
    for (const seed of [1, 2, 3]) {
      await page.goto(`/tonight?lang=hi&stream=1&seed=${seed}`)
      await expect(pick(page)).toBeVisible()
      const reasons = pick(page).getByRole('list', { name: 'Why this one' })
      await expect(reasons.getByText('Language: HI')).toBeVisible()
      await expect(reasons.getByText('Streaming in IN')).toBeVisible()
      await expect(pick(page).getByRole('group', { name: 'Where to watch it' }).getByText('Netflix')).toBeVisible()
      expect(((await pickId(page)) - 1001) % 3).toBe(0) // Hindi + streaming in India = positions 0, 3, 6 ...
    }
  })

  test('the wishes form drives the address and a re-draw: choosing a mood on a shown result updates it', async ({ page }) => {
    await page.goto('/tonight?seed=7')
    await expect(pick(page)).toBeVisible()

    await wishes(page).getByRole('button', { name: 'Spooky' }).click()
    await expect(page).toHaveURL(/mood=scary/)
    await expect(wishes(page).getByRole('button', { name: 'Spooky' })).toHaveAttribute('aria-pressed', 'true')
    await expect(pick(page).getByText('Horror', { exact: true })).toBeVisible()

    await wishes(page).getByRole('button', { name: 'Anything' }).first().click()
    await expect(page).not.toHaveURL(/mood=/)
  })

  test('TV shows can be chosen, with the time wish meaning minutes per episode', async ({ page }) => {
    await page.goto('/tonight?type=tv&seed=3')
    await expect(pick(page).getByRole('link', { name: /Details/ })).toHaveAttribute('href', /\/title\/tv\/\d+/)
    await expect(wishes(page).getByRole('group', { name: 'Time per episode' })).toBeVisible()

    await page.goto('/tonight?type=tv&time=120&seed=3')
    await expect(pick(page).getByRole('list', { name: 'Why this one' }).getByText('Up to 120 min per episode')).toBeVisible()
  })

  test('when nothing matches the page says so and suggests loosening the wishes', async ({ page }) => {
    await page.goto('/tonight?time=90&seed=1') // no fake movie is both under 90 minutes and well rated
    await expect(page.getByText('Nothing matches all of that')).toBeVisible()
    await expect(page.getByText(/Try fewer filters/)).toBeVisible()
    await expect(pick(page)).toHaveCount(0)

    await wishes(page).getByRole('button', { name: 'Any length' }).click()
    await expect(pick(page)).toBeVisible()
  })

  test('a mood the fake TMDB has no genre for also ends in the empty answer, not an error', async ({ page }) => {
    await page.goto('/tonight?mood=romance&seed=1')
    await expect(page.getByText('Nothing matches all of that')).toBeVisible()
    await expect(page.getByRole('alert')).toHaveCount(0)
  })

  test('you are never offered what you are watching or have watched', async ({ page }) => {

    await page.goto('/tonight?mood=laugh&seed=11')
    await expect(pick(page)).toBeVisible()
    const seen = await pickId(page)

    await page.goto(`/title/movie/${seen}`)
    await page.getByRole('button', { name: 'Add to watchlist' }).click()
    await page.getByRole('group', { name: 'Status' }).getByRole('button', { name: 'Watched' }).click()
    await expect(page.getByRole('button', { name: 'Rate 7 out of 10' })).toBeVisible() // saved: the rating buttons appeared

    for (const seed of [11, 12, 13, 14]) {
      await page.goto(`/tonight?mood=laugh&seed=${seed}`)
      await expect(pick(page)).toBeVisible()
      expect(await pickId(page)).not.toBe(seen)
      const alternatives = page.getByRole('region', { name: 'Or maybe one of these' })
      if (await alternatives.count()) await expect(alternatives.locator(`a[href="/title/movie/${seen}"]`)).toHaveCount(0)
    }
  })

  test('a title on your want-to-watch list is flagged and preferred', async ({ page }) => {

    // Hindi + streaming gives positions 0, 3, 6 ... ; list one of them as "want to watch"
    await page.goto('/title/movie/1004')
    await page.getByRole('button', { name: 'Add to watchlist' }).click()
    await expect(page.getByText('On your watchlist')).toBeVisible()

    let preferred = 0
    for (const seed of [1, 2, 3, 4, 5, 6]) {
      await page.goto(`/tonight?lang=hi&stream=1&seed=${seed}`)
      await expect(pick(page)).toBeVisible()
      if ((await pickId(page)) === 1004) {
        preferred++
        await expect(pick(page).getByRole('list', { name: 'Why this one' }).getByText('On your watchlist')).toBeVisible()
      }
    }
    // the other candidates (positions 9, 15, 21) are rated 7.2 to 7.4; the boost of 3 beats any random bonus (up to 2)
    expect(preferred, 'the wanted title wins every draw').toBe(6)
  })
})
