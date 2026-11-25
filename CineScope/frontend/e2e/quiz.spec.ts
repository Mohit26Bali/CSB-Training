import type { Page } from '@playwright/test'
import { expect, test } from './fixtures'
import { seed } from './quotes'

/**
 * The famous-lines quiz in the browser, as a signed-in player (every test starts signed in as a fresh "Test Viewer", see
 * e2e/fixtures.ts). The answers are never sent to the browser before a question is answered, so the tests work them out the way a
 * knowledgeable player would: from the quotes file (e2e/quotes.ts). The server's rules (scoring, one answer per question, other
 * people's rounds, the board) are tested in depth by the backend's QuizApiTest; here the point is that the page shows them right.
 */
const CATEGORIES = ['Mixed', 'Hollywood', 'TV shows', 'Bollywood', 'Anime', 'Cast quiz', 'Blurred posters']
const PERFECT_SCORES = [10, 22, 36, 52, 70, 90, 110, 130, 150, 170] // 10 points, plus 2 for each right answer in a row, up to 5 steps

const lineOf = async (page: Page) => (await page.getByTestId('quiz-line').innerText()).replace(/^“|”$/g, '')
const optionText = async (page: Page, index: number) => (await page.getByTestId(`quiz-option-${index}`).locator('span.flex-1').innerText()).trim()

/** Which of the four options is right, worked out from the quotes file. */
async function rightIndex(page: Page): Promise<number> {
  const line = await lineOf(page)
  // textContent, not innerText: the prompt is shown in capitals by CSS, and innerText would return it that way
  const asksTitle = ((await page.getByTestId('quiz-question').locator('p').first().textContent()) ?? '').startsWith('Which film')
  const candidates = seed.filter((q) => q.line === line)
  expect(candidates.length, `"${line}" is in the quotes file`).toBeGreaterThan(0)
  const answers = new Set(candidates.map((q) => (asksTitle ? q.title : q.speaker)))
  for (let i = 0; i < 4; i++) {
    if (answers.has(await optionText(page, i))) return i
  }
  const seen = await Promise.all([0, 1, 2, 3].map((i) => optionText(page, i)))
  throw new Error(`none of the options (${JSON.stringify(seen)}) is a known answer (${JSON.stringify([...answers])}) for "${line}"`)
}

/** Answers the current question: right or deliberately wrong. */
async function answer(page: Page, correct: boolean) {
  const right = await rightIndex(page)
  await page.getByTestId(`quiz-option-${correct ? right : (right + 1) % 4}`).click()
  await expect(page.getByTestId('quiz-reveal')).toBeVisible()
  return right
}

async function play(page: Page, category: string) {
  await page.goto('/play')
  await page.getByRole('group', { name: 'Choose a category' }).getByRole('button', { name: new RegExp(category) }).click()
  await expect(page.getByTestId('quiz-question')).toBeVisible()
}

test.describe('the quiz', () => {
  test('Play is in the header and opens the categories and the leaderboard', async ({ page }) => {
    await page.goto('/')
    await page.getByRole('navigation').getByRole('link', { name: 'Play' }).first().click()

    await expect(page).toHaveURL('/play')
    await expect(page.getByRole('heading', { level: 1, name: 'Guess the line' })).toBeVisible()
    const choices = page.getByRole('group', { name: 'Choose a category' }).getByRole('button')
    await expect(choices).toHaveCount(7)
    for (const name of CATEGORIES) await expect(choices.filter({ hasText: name })).toHaveCount(1)
    await expect(page.getByRole('heading', { name: 'Leaderboard' })).toBeVisible()
  })

  test('a perfect round adds up to 170, shows every answer as it goes and ends on a result with the leaderboard', async ({ page }) => {
    await play(page, 'Hollywood')
    await expect(page.getByTestId('quiz-progress')).toHaveText('Question 1 of 10 · Hollywood')

    for (let i = 0; i < 10; i++) {
      await expect(page.getByTestId('quiz-progress')).toContainText(`Question ${i + 1} of 10`)
      const right = await answer(page, true)
      await expect(page.getByTestId(`quiz-option-${right}`)).toContainText('Right answer')
      await expect(page.getByTestId('quiz-verdict')).toContainText('Correct!')
      await expect(page.getByTestId('quiz-score')).toHaveText(String(PERFECT_SCORES[i]))
      await expect(page.getByTestId('quiz-streak')).toHaveText(String(i + 1))
      await page.getByTestId('quiz-next').click()
    }

    await expect(page.getByTestId('quiz-summary')).toBeVisible()
    await expect(page.getByTestId('quiz-final-score')).toHaveText('170')
    await expect(page.getByTestId('quiz-correct-count')).toHaveText('10')
    await expect(page.getByText('A perfect round!')).toBeVisible()
    await expect(page.getByTestId('quiz-board')).toBeVisible()
    // the board shows the top ten; this player is on it, or told their own place under it
    await expect(page.locator('li[data-you="true"]').or(page.getByText(/^Your best:/)).first()).toBeVisible()
  })

  test('a wrong answer earns nothing, shows the right one, and ends the streak', async ({ page }) => {
    await play(page, 'TV shows')

    await answer(page, true)
    await expect(page.getByTestId('quiz-score')).toHaveText('10')
    await page.getByTestId('quiz-next').click()

    const right = await answer(page, false)
    await expect(page.getByTestId('quiz-verdict')).toHaveText('Not this time')
    await expect(page.getByTestId(`quiz-option-${right}`)).toContainText('Right answer')
    await expect(page.getByRole('group', { name: 'Options' }).getByText('Your answer')).toHaveCount(1)
    await expect(page.getByTestId('quiz-score')).toHaveText('10') // unchanged
    await expect(page.getByTestId('quiz-streak')).toHaveText('0')
    for (let i = 0; i < 4; i++) await expect(page.getByTestId(`quiz-option-${i}`)).toBeDisabled() // no changing your mind
  })

  test('the reveal says who spoke the line and in what, with a link to the title', async ({ page }) => {
    await play(page, 'Anime')
    const line = await lineOf(page)
    await answer(page, true)

    const expected = seed.find((q) => q.line === line)!
    const reveal = page.getByTestId('quiz-reveal')
    await expect(reveal).toContainText(expected.speaker)
    await expect(reveal.getByRole('link', { name: expected.title })).toHaveAttribute('href', `/title/${expected.type}/${expected.id}`)
    await expect(reveal).toContainText(`(${expected.year})`)
  })

  test('keys 1 to 4 answer and Enter goes on', async ({ page }) => {
    await play(page, 'Bollywood')

    await page.keyboard.press('2')
    await expect(page.getByTestId('quiz-reveal')).toBeVisible()
    await page.keyboard.press('3') // a second key press on the same question changes nothing
    await expect(page.getByTestId('quiz-progress')).toContainText('Question 1 of 10')
    await page.keyboard.press('Enter')

    await expect(page.getByTestId('quiz-progress')).toContainText('Question 2 of 10')
    await expect(page.getByTestId('quiz-reveal')).toHaveCount(0)
  })

  test('every question comes from the chosen category', async ({ page }) => {
    await play(page, 'Anime')

    for (let i = 0; i < 10; i++) {
      const line = await lineOf(page)
      expect(seed.some((q) => q.category === 'ANIME' && q.line === line), `"${line}" is an anime line`).toBe(true)
      await page.keyboard.press('1')
      await expect(page.getByTestId('quiz-reveal')).toBeVisible()
      await page.keyboard.press('Enter')
      if (i < 9) await expect(page.getByTestId('quiz-progress')).toContainText(`Question ${i + 2} of 10`)
    }
    await expect(page.getByTestId('quiz-summary')).toBeVisible()
  })

  test('the right answer is not in what the server sends before the question is answered', async ({ page }) => {
    await page.goto('/play')
    const started = page.waitForResponse((r) => r.url().endsWith('/api/quiz/rounds') && r.request().method() === 'POST')
    await page.getByTestId('quiz-category-TV').click()
    const body = await (await started).text()

    expect(body).not.toMatch(/correct/i)
    expect(body).not.toMatch(/index/i)
    expect(JSON.parse(body).questions).toHaveLength(10)
  })

  test('a second round in the same category does not repeat a line from the first', async ({ page }) => {
    async function roundLines() {
      const lines: string[] = []
      for (let i = 0; i < 10; i++) {
        lines.push(await lineOf(page))
        await page.keyboard.press('1')
        await expect(page.getByTestId('quiz-reveal')).toBeVisible()
        await page.keyboard.press('Enter')
      }
      return lines
    }
    await play(page, 'Anime')
    const first = await roundLines()
    await expect(page.getByTestId('quiz-summary')).toBeVisible()

    await page.getByTestId('quiz-again').click()
    await expect(page.getByTestId('quiz-question')).toBeVisible()
    const second = await roundLines()

    expect(new Set(first).size).toBe(10)
    expect(second.filter((line) => first.includes(line))).toEqual([])
  })

  test('leaving a round goes back to the categories', async ({ page }) => {
    await play(page, 'TV shows')
    await page.getByRole('button', { name: 'Leave this round' }).click()

    await expect(page.getByRole('heading', { level: 1, name: 'Guess the line' })).toBeVisible()
    await expect(page.getByTestId('quiz-question')).toHaveCount(0)
  })

  test('the leaderboard tabs switch category and say so when nobody has played', async ({ page }) => {
    await page.goto('/play')
    const tabs = page.getByRole('group', { name: 'Leaderboard category' })

    await tabs.getByRole('button', { name: 'Bollywood' }).click()
    await expect(tabs.getByRole('button', { name: 'Bollywood' })).toHaveAttribute('aria-pressed', 'true')
    await expect(page.getByTestId('quiz-board').or(page.getByText(/Nobody has finished a Bollywood round yet/))).toBeVisible()
  })

  test('on a phone nothing scrolls sideways, on the choices or in a question', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await page.goto('/play')
    const overflow = () => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
    expect(await overflow()).toBeLessThanOrEqual(0)

    await page.getByTestId('quiz-category-HOLLYWOOD').click()
    await expect(page.getByTestId('quiz-question')).toBeVisible()
    expect(await overflow()).toBeLessThanOrEqual(0)
    await page.keyboard.press('1')
    await expect(page.getByTestId('quiz-reveal')).toBeVisible()
    expect(await overflow()).toBeLessThanOrEqual(0)
  })
})

test.describe('the quiz for visitors', () => {
  test.use({ signedIn: false })

  test('asks for a sign-in and comes back to the quiz afterwards', async ({ page }) => {
    await page.goto('/play')
    await expect(page).toHaveURL(/\/login\?next=%2Fplay/)
  })
})
