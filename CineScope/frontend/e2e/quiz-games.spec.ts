import type { Page } from '@playwright/test'
import { BACKEND, expect, test } from './fixtures'
import { seed } from './quotes'

/**
 * The cast game ("which film or show has these actors?") and the blurred-poster game, in the browser, as a signed-in player (every test
 * starts signed in as a fresh "Test Viewer", see e2e/fixtures.ts). The fake TMDB (e2e/tmdb-stub.mjs) gives each title of the quotes file
 * five actors named "Actor {type}-{id} {n}" and a poster at /classic-poster-{type}-{id}.jpg, so a test can tell which title a clue
 * belongs to and so which option is right; every seventh id has neither and must never be asked about. The backend's
 * QuizTitleGamesApiTest tests the rules (scoring, the server-measured speed bonus) in depth.
 */
const PERFECT = [10, 22, 36, 52, 70, 90, 110, 130, 150, 170]
const optionText = async (page: Page, index: number) => (await page.getByTestId(`quiz-option-${index}`).locator('span.flex-1').innerText()).trim()

type Clue = { type: string; id: number }

/** Which title the question on screen is about, from its clue. */
async function clueOf(page: Page): Promise<Clue> {
  const actors = page.getByTestId('quiz-actors').getByRole('listitem')
  if ((await actors.count()) > 0) {
    const match = /^Actor (movie|tv)-(\d+) \d$/.exec((await actors.first().innerText()).trim())
    if (!match) throw new Error('unexpected actor name')
    return { type: match[1], id: Number(match[2]) }
  }
  const match = /classic-poster-(movie|tv)-(\d+)\.jpg/.exec((await page.getByTestId('quiz-poster').getAttribute('src')) ?? '')
  if (!match) throw new Error('unexpected poster')
  return { type: match[1], id: Number(match[2]) }
}

async function rightIndex(page: Page): Promise<number> {
  const clue = await clueOf(page)
  const title = seed.find((q) => q.type === clue.type && q.id === clue.id)?.title
  expect(title, `title ${clue.type}:${clue.id} is in the quotes file`).toBeTruthy()
  for (let i = 0; i < 4; i++) {
    if ((await optionText(page, i)) === title) return i
  }
  throw new Error(`none of the options is "${title}"`)
}

async function play(page: Page, name: string) {
  await page.goto('/play')
  await page.getByRole('group', { name: 'Choose a category' }).getByRole('button', { name: new RegExp(name) }).click()
  await expect(page.getByTestId('quiz-question')).toBeVisible()
}

test.describe('the cast game', () => {
  test('shows three actors and four titles, and a perfect round adds up to 170', async ({ page }) => {
    await play(page, 'Cast quiz')
    await expect(page.getByTestId('quiz-progress')).toHaveText('Question 1 of 10 · Cast quiz')

    for (let i = 0; i < 10; i++) {
      await expect(page.getByTestId('quiz-actors').getByRole('listitem')).toHaveCount(3)
      await expect(page.getByRole('group', { name: 'Options' }).getByRole('button')).toHaveCount(4)
      await page.getByTestId(`quiz-option-${await rightIndex(page)}`).click()
      await expect(page.getByTestId('quiz-verdict')).toContainText('Correct!')
      await expect(page.getByTestId('quiz-score')).toHaveText(String(PERFECT[i]))
      await expect(page.getByTestId('quiz-speed-bonus')).toHaveCount(0) // the cast game has no speed bonus
      await page.getByTestId('quiz-next').click()
    }

    await expect(page.getByTestId('quiz-final-score')).toHaveText('170')
    await expect(page.getByText('Cast quiz round finished')).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Leaderboard · Cast quiz' })).toBeVisible()
  })

  test('the answer names the title and year, with no speaker, and links to it', async ({ page }) => {
    await play(page, 'Cast quiz')
    const clue = await clueOf(page)
    await page.getByTestId(`quiz-option-${await rightIndex(page)}`).click()

    const expected = seed.find((q) => q.type === clue.type && q.id === clue.id)!
    const reveal = page.getByTestId('quiz-reveal')
    await expect(reveal.getByRole('link', { name: expected.title })).toHaveAttribute('href', `/title/${expected.type}/${expected.id}`)
    await expect(reveal).toContainText(`(${expected.year})`)
    await expect(reveal).not.toContainText(' in ' + expected.title) // there is no "speaker in title" line in this game
  })

  test('a wrong answer shows the right title and ends the streak', async ({ page }) => {
    await play(page, 'Cast quiz')
    const right = await rightIndex(page)
    await page.getByTestId(`quiz-option-${(right + 1) % 4}`).click()

    await expect(page.getByTestId('quiz-verdict')).toHaveText('Not this time')
    await expect(page.getByTestId(`quiz-option-${right}`)).toContainText('Right answer')
    await expect(page.getByTestId('quiz-score')).toHaveText('0')
  })

  test('a title with no cast listed is never asked about', async ({ page }) => {
    for (let round = 0; round < 2; round++) {
      await play(page, 'Cast quiz')
      for (let i = 0; i < 10; i++) {
        const clue = await clueOf(page)
        expect(clue.id % 7, `title ${clue.type}:${clue.id} has no cast`).not.toBe(0)
        await page.keyboard.press('1')
        await expect(page.getByTestId('quiz-reveal')).toBeVisible()
        await page.keyboard.press('Enter')
      }
      await expect(page.getByTestId('quiz-summary')).toBeVisible()
    }
  })

  test('the answers are not in what the server sends', async ({ page }) => {
    await page.goto('/play')
    const started = page.waitForResponse((r) => r.url().endsWith('/api/quiz/rounds') && r.request().method() === 'POST')
    await page.getByTestId('quiz-category-CAST').click()
    const body = await (await started).text()

    expect(body).not.toMatch(/correct/i)
    expect(JSON.parse(body).questions).toHaveLength(10)
  })
})

test.describe('the blurred poster game', () => {
  test('starts heavily blurred, says what a quick answer is worth, and clears once answered', async ({ page }) => {
    await play(page, 'Blurred posters')
    const poster = page.getByTestId('quiz-poster')

    await expect(poster).toHaveAttribute('data-blur', '20')
    await expect(poster).toHaveCSS('filter', 'blur(20px)')
    await expect(page.getByTestId('quiz-bonus')).toHaveText('Speed bonus right now: +5')
    await expect(poster).toHaveAttribute('alt', 'A blurred film or show poster') // the picture gives nothing away to a screen reader either

    await page.getByTestId(`quiz-option-${await rightIndex(page)}`).click()
    await expect(poster).toHaveAttribute('data-blur', '0')
    await expect(page.getByTestId('quiz-bonus')).toHaveCount(0)
  })

  test('the picture sharpens in steps and the bonus on offer shrinks, following the clock', async ({ page }) => {
    await page.clock.install()
    await page.goto('/play')
    await page.getByTestId('quiz-category-POSTER').click()
    await expect(page.getByTestId('quiz-poster')).toBeVisible()
    await page.clock.pauseAt(new Date(Date.now() + 1_000))
    const poster = page.getByTestId('quiz-poster')
    const bonus = page.getByTestId('quiz-bonus')

    await expect(poster).toHaveAttribute('data-blur', '20')
    await expect(bonus).toHaveText('Speed bonus right now: +5')
    await page.clock.runFor(7_000)
    await expect(poster).toHaveAttribute('data-blur', '12')
    await expect(bonus).toHaveText('Speed bonus right now: +2')
    await page.clock.runFor(6_000)
    await expect(poster).toHaveAttribute('data-blur', '6')
    await expect(bonus).toContainText('No speed bonus now')
    await page.clock.runFor(10_000)
    await expect(poster).toHaveAttribute('data-blur', '3') // never fully clear until answered
  })

  test('a quick right answer earns the speed bonus, worked out by the server', async ({ page }) => {
    await play(page, 'Blurred posters')
    await page.getByTestId(`quiz-option-${await rightIndex(page)}`).click()

    await expect(page.getByTestId('quiz-verdict')).toHaveText('Correct! +15') // 10 + 5
    await expect(page.getByTestId('quiz-speed-bonus')).toHaveText('Includes +5 for answering quickly')
    await expect(page.getByTestId('quiz-score')).toHaveText('15')
  })

  test('a slow right answer earns no bonus, however the page looks', async ({ page }) => {
    await play(page, 'Blurred posters')
    const right = await rightIndex(page)
    await page.waitForTimeout(12_500) // past the server's 12 seconds

    await page.getByTestId(`quiz-option-${right}`).click()

    await expect(page.getByTestId('quiz-verdict')).toHaveText('Correct! +10')
    await expect(page.getByTestId('quiz-speed-bonus')).toHaveCount(0)
  })

  test('a wrong answer earns nothing even when it is quick', async ({ page }) => {
    await play(page, 'Blurred posters')
    const right = await rightIndex(page)
    await page.getByTestId(`quiz-option-${(right + 1) % 4}`).click()

    await expect(page.getByTestId('quiz-verdict')).toHaveText('Not this time')
    await expect(page.getByTestId('quiz-speed-bonus')).toHaveCount(0)
    await expect(page.getByTestId('quiz-score')).toHaveText('0')
  })

  test('the page tells the server each poster is on screen, once per question', async ({ page }) => {
    const reports: string[] = []
    page.on('request', (request) => {
      if (request.method() === 'POST' && /\/questions\/\d+\/shown$/.test(request.url())) reports.push(request.url().replace(/.*\/rounds\//, ''))
    })
    await play(page, 'Blurred posters')
    await expect.poll(() => reports.length).toBe(1)
    await page.keyboard.press('1')
    await expect(page.getByTestId('quiz-reveal')).toBeVisible()
    await page.keyboard.press('Enter')
    await expect.poll(() => reports.length).toBe(2)

    expect(reports[0]).toMatch(/\/questions\/1\/shown$/)
    expect(reports[1]).toMatch(/\/questions\/2\/shown$/)
  })

  test('a title with no poster is never asked about', async ({ page }) => {
    await play(page, 'Blurred posters')
    for (let i = 0; i < 10; i++) {
      const clue = await clueOf(page)
      expect(clue.id % 7, `title ${clue.type}:${clue.id} has no poster`).not.toBe(0)
      await page.keyboard.press('1')
      await expect(page.getByTestId('quiz-reveal')).toBeVisible()
      await page.keyboard.press('Enter')
    }
    await expect(page.getByTestId('quiz-summary')).toBeVisible()
  })

  test('on a phone the poster fits and the page does not scroll sideways', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await play(page, 'Blurred posters')
    await expect(page.getByTestId('quiz-poster')).toBeVisible()

    expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(0)
  })
})

test.describe('the leaderboards of the new games', () => {
  test('each game has its own tab', async ({ page }) => {
    await page.goto('/play')
    const tabs = page.getByRole('group', { name: 'Leaderboard category' })

    for (const name of ['Cast quiz', 'Blurred posters']) {
      await tabs.getByRole('button', { name }).click()
      await expect(tabs.getByRole('button', { name })).toHaveAttribute('aria-pressed', 'true')
      await expect(page.getByTestId('quiz-board').or(page.getByText(new RegExp(`Nobody has finished a ${name} round yet`)))).toBeVisible()
    }
  })

  test('the server refuses a made-up game', async ({ page }) => {
    const response = await page.request.post(`${BACKEND}/api/quiz/rounds`, { data: { category: 'EMOJI' } })
    expect(response.status()).toBe(400)
  })
})
