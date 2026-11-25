import { expect, test } from './fixtures'

/**
 * Reviews and discussion on a title page, as a signed-in person sees them (every test starts signed in as a fresh "Test
 * Viewer", see e2e/fixtures.ts).
 *
 * Two kinds of title are used. Inception (movie 27205) carries the seeded demo discussion (backend config/DemoDataInitializer):
 * three reviews rated 9, 7 and 5 by Asha, Meera and Sam, two replies, and helpful marks. Its numbers are worked out by hand in the
 * comments below, so a change to the processing rules shows up here. The fake films 1001-1003 have no comments, so tests that
 * write use them. TMDB's rating for Inception in the fake TMDB (e2e/tmdb-stub.mjs) is 8.2.
 */
const region = (page: import('@playwright/test').Page) => page.getByRole('region', { name: 'Reviews and discussion' })
const article = (page: import('@playwright/test').Page, author: string) => region(page).getByRole('article', { name: `Comment by ${author}` })
const step = (page: import('@playwright/test').Page, name: string) => region(page).getByRole('region', { name: 'Processing result' }).locator(`[data-step="${name}"]`)

async function postReview(page: import('@playwright/test').Page, text: string, rating?: number | null) {
  const box = region(page).getByLabel('Write a review')
  await box.fill(text)
  if (rating) await region(page).getByRole('button', { name: `Review rating: ${rating} out of 10` }).first().click()
  await region(page).getByRole('button', { name: 'Post comment' }).click()
}

test.describe('the seeded discussion of Inception', () => {
  test('the summary adds up the ratings, mood and topics', async ({ page }) => {
    await page.goto('/title/movie/27205')
    const summary = region(page).getByTestId('discussion-summary')

    // ratings 9, 7 and 5: average 7.0; blended with TMDB's 8.2 as (21 + 5 * 8.2) / 8 = 7.75, shown as 7.8
    await expect(summary.getByTestId('community-average')).toHaveText('7.0 (3 ratings)')
    await expect(summary.getByTestId('community-score')).toContainText('7.8')
    await expect(summary.getByText('8.2', { exact: true })).toBeVisible()
    for (const [rating, count] of [['5', '1'], ['7', '1'], ['9', '1'], ['8', '0'], ['10', '0']]) {
      await expect(summary.locator(`[data-rating="${rating}"]`)).toHaveAttribute('data-count', count)
    }
    // five published comments (3 reviews + 2 replies): 3 positive, 1 neutral, 1 critical
    await expect(summary.locator('[data-mood="positive"]')).toContainText('60%')
    await expect(summary.locator('[data-mood="neutral"]')).toContainText('20%')
    await expect(summary.locator('[data-mood="negative"]')).toContainText('20%')
    await expect(summary.locator('[data-topic="ending"]')).toContainText('×2') // Asha's review and Ravi's reply both mention it
  })

  test('comments come most helpful first, with the replies inside and the author’s rating and mood', async ({ page }) => {
    await page.goto('/title/movie/27205')

    await expect(region(page).getByRole('heading', { name: /Comments/ })).toContainText('(3)')
    const first = region(page).getByRole('article').first()
    await expect(first).toHaveAttribute('data-author', 'asha') // three helpful marks beat one and none
    await expect(first.getByRole('button', { name: /^Helpful · 3$/ })).toBeVisible()
    await expect(first.getByLabel('Rated 9 out of 10')).toBeVisible()
    await expect(first.getByTestId('sentiment').first()).toHaveText('Positive (+0.50)')
    const replies = first.getByRole('group', { name: 'Replies to Asha Rao' })
    await expect(replies.getByText('Same here! That spinning top shot is the best ending ever.')).toBeVisible()
  })

  test('"How was this processed?" shows the five stages and the sentiment working', async ({ page }) => {
    await page.goto('/title/movie/27205')
    const asha = article(page, 'Asha Rao').first()

    await asha.getByRole('button', { name: 'How was this processed?' }).first().click()

    const steps = asha.getByRole('list', { name: 'Processing steps' }).first()
    await expect(steps.getByRole('listitem')).toHaveCount(5)
    await expect(steps.locator('[data-step="Cleaned"]')).toContainText('Nothing to tidy')
    await expect(steps.locator('[data-step="Spam check"]')).toContainText('No links, no shouting')
    await expect(steps.locator('[data-step="Profanity filter"]')).toContainText('No bad language')
    await expect(steps.locator('[data-step="Sentiment analysis"]')).toContainText('Positive (+0.50) from 1 emotion word')
    await expect(steps.locator('[data-step="Decision"]')).toContainText('Published')
  })

  test('the comments can be sorted by helpful, newest, most positive and most critical', async ({ page }) => {
    await page.goto('/title/movie/27205')
    const authors = () => region(page).locator('article[data-comment-id]').evaluateAll((els) => els.filter((e) => e.getAttribute('aria-label')?.startsWith('Comment by') && !e.parentElement?.closest("article")).map((e) => e.getAttribute('data-author')))
    const sorter = region(page).getByRole('group', { name: 'Sort comments' })

    await expect(sorter.getByRole('button', { name: 'Most helpful' })).toHaveAttribute('aria-pressed', 'true')
    await expect.poll(async () => (await authors())[0]).toBe('asha')

    await sorter.getByRole('button', { name: 'Most critical' }).click()
    await expect(sorter.getByRole('button', { name: 'Most critical' })).toHaveAttribute('aria-pressed', 'true')
    await expect.poll(async () => (await authors())[0]).toBe('sam') // "a bit overrated ... confusing ... slow"

    await sorter.getByRole('button', { name: 'Newest' }).click()
    await expect.poll(async () => (await authors())[0]).toBe('sam') // the most recent of the three

    await sorter.getByRole('button', { name: 'Most positive' }).click()
    await expect.poll(async () => {
      const names = await authors()
      return names[names.length - 1]
    }).toBe('sam') // the least positive goes last
  })

  test('helpful can be marked and taken back, but not on your own comment', async ({ page }) => {
    await page.goto('/title/movie/27205')
    const asha = article(page, 'Asha Rao').first()
    const helpful = asha.getByRole('button', { name: /^Helpful/ }).first()

    await expect(helpful).toHaveText(/Helpful · 3/)
    await helpful.click()
    await expect(helpful).toHaveText(/Helpful · 4/)
    await expect(helpful).toHaveAttribute('aria-pressed', 'true')
    await helpful.click()
    await expect(helpful).toHaveText(/Helpful · 3/)
    await expect(helpful).toHaveAttribute('aria-pressed', 'false')
  })
})

test.describe('writing on a title', () => {
  test('a rated review shows its processing step by step, then appears with the rating and updates the numbers', async ({ page }) => {
    await page.goto('/title/movie/1001')
    await expect(region(page).getByText('No comments yet. Be the first to share what you thought.')).toBeVisible()

    await postReview(page, 'Absolutely brilliant film, I loved it', 9)

    // the stages appear one after another; the last one is the decision
    await expect(step(page, 'Cleaned')).toBeVisible()
    await expect(step(page, 'Sentiment analysis')).toContainText('Positive')
    await expect(step(page, 'Decision')).toContainText('Published')
    await expect(region(page).getByRole('status').filter({ hasText: 'Published. Thanks for sharing!' })).toBeVisible()

    const mine = article(page, 'Test Viewer')
    await expect(mine).toContainText('Absolutely brilliant film, I loved it')
    await expect(mine.getByLabel('Rated 9 out of 10')).toBeVisible()
    await expect(mine.getByText('You', { exact: true })).toBeVisible()
    await expect(region(page).getByTestId('community-average')).toHaveText('9.0 (1 rating)')
    await expect(region(page).locator('[data-rating="9"]')).toHaveAttribute('data-count', '1')
    await expect(region(page).getByLabel('Write a review')).toHaveValue('') // ready for the next thought
  })

  test('mild bad language is masked; the processing says so', async ({ page }) => {
    await page.goto('/title/movie/1002')

    await postReview(page, 'That was damn good')

    await expect(step(page, 'Profanity filter')).toContainText('Masked 1 word')
    await expect(article(page, 'Test Viewer')).toContainText('That was d*** good')
    await expect(article(page, 'Test Viewer')).not.toContainText('damn')
  })

  test('strong language is held for a moderator: the author is told, and it does not count publicly', async ({ page }) => {
    await page.goto('/title/movie/1003')

    await postReview(page, 'What a shit ending, honestly', 2)

    await expect(step(page, 'Profanity filter')).toContainText('held for a moderator')
    await expect(step(page, 'Decision')).toContainText('Held for review')
    await expect(region(page).getByRole('status').filter({ hasText: 'Held for review. Only you and the moderators can see it for now.' })).toBeVisible()
    await expect(article(page, 'Test Viewer')).toContainText('Held for review. Only you and the moderators can see this for now.')
    await expect(region(page).getByTestId('community-average')).toHaveText('no ratings yet') // the held 2-star is not counted
    await expect(region(page).getByText('No comments yet. Start the conversation!')).toHaveCount(0)
    await expect(region(page).getByTestId('discussion-summary').getByText('No comments yet. Be the first to share what you thought.')).toBeVisible()
  })

  test('a title can be rated only once; a second rated review is refused with a clear message', async ({ page }) => {
    await page.goto('/title/movie/1004')
    await postReview(page, 'My first honest review of this film', 7)
    await expect(article(page, 'Test Viewer')).toBeVisible()

    await postReview(page, 'A second review with another rating', 4)

    await expect(region(page).getByRole('alert')).toHaveText('You have already rated this title. Edit your review to change the rating')
  })

  test('replying nests the answer under the comment; replies cannot carry a rating or be replied to', async ({ page }) => {
    await page.goto('/title/movie/1005')
    await postReview(page, 'Worth a conversation')
    const parent = article(page, 'Test Viewer').first()
    await expect(parent).toContainText('Worth a conversation')

    await parent.getByRole('button', { name: 'Reply' }).first().click()
    await expect(parent.getByRole('group', { name: 'Review rating (optional)' })).toHaveCount(0) // no rating on a reply
    await parent.getByLabel('Reply to Test Viewer').fill('Adding a thought of my own')
    await parent.getByRole('button', { name: 'Post reply' }).click()

    const replies = parent.getByRole('group', { name: 'Replies to Test Viewer' })
    await expect(replies).toContainText('Adding a thought of my own')
    await expect(replies.getByRole('button', { name: 'Reply' })).toHaveCount(0) // one level deep
  })

  test('your own comment can be edited (and is analysed again) and then deleted', async ({ page }) => {
    await page.goto('/title/movie/1006')
    await postReview(page, 'Boring and slow', 3)
    const mine = article(page, 'Test Viewer').first()
    await expect(mine.getByTestId('sentiment').first()).toContainText('Critical')

    await mine.getByRole('button', { name: 'Edit' }).click()
    await mine.getByLabel('Edit your comment').fill('On a second watch it is brilliant')
    await mine.getByRole('button', { name: 'Review rating: 9 out of 10' }).click()
    await mine.getByRole('button', { name: 'Save changes' }).click()

    await expect(mine).toContainText('On a second watch it is brilliant')
    await expect(mine.getByLabel('Rated 9 out of 10')).toBeVisible()
    await expect(mine.getByTestId('sentiment').first()).toContainText('Positive')

    await mine.getByRole('button', { name: 'Delete' }).click()
    const dialog = page.getByRole('dialog', { name: 'Delete this comment?' })
    await dialog.getByRole('button', { name: 'Cancel' }).click()
    await expect(mine).toBeVisible() // cancelled
    await mine.getByRole('button', { name: 'Delete' }).click()
    await dialog.getByRole('button', { name: 'Delete comment' }).click()
    await expect(region(page).getByText('No comments yet. Start the conversation!')).toBeVisible()
  })

  test('your own comment cannot be marked helpful by you', async ({ page }) => {
    await page.goto('/title/movie/1007')
    await postReview(page, 'A modest remark about the film')

    await expect(article(page, 'Test Viewer').first().getByRole('button', { name: /^Helpful/ }).first()).toBeDisabled()
  })

  test('too short a text is stopped before sending, and the counter follows the typing', async ({ page }) => {
    await page.goto('/title/movie/1008')
    const box = region(page).getByLabel('Write a review')

    await box.fill('x')
    await expect(region(page).getByRole('button', { name: 'Post comment' })).toBeDisabled()
    await box.fill('Long enough now')
    await expect(region(page).getByRole('button', { name: 'Post comment' })).toBeEnabled()
    await expect(region(page).getByText('15/1000')).toBeVisible()
  })
})
