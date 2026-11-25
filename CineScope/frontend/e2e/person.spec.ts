import { expect, test } from './fixtures'

/**
 * Person pages: reached from cast, directors and search results; profile facts, biography, known-for posters and the
 * filmography with its Acting / Crew tabs; plus the quiet cases (nothing known about the person, someone who died,
 * unknown ids). The fake TMDB (e2e/tmdb-stub.mjs, function person) defines 3001 (full), 3002 (nearly empty) and
 * 3003 (deceased).
 */
test.describe('person pages', () => {
  test('the cast of a title link to their pages', async ({ page }) => {
    await page.goto('/title/movie/1001')
    await page.getByRole('link', { name: 'Ananya Rao as Lead' }).click()

    await expect(page).toHaveURL('/person/5000')
    await expect(page.getByRole('heading', { level: 1, name: 'Ananya Rao' })).toBeVisible()
  })

  test('the director’s name links to their page', async ({ page }) => {
    await page.goto('/title/movie/1001')
    await page.locator('p', { hasText: 'Director:' }).getByRole('link', { name: 'Ishaan Verma' }).click()

    await expect(page).toHaveURL('/person/6001')
    await expect(page.getByRole('heading', { level: 1, name: 'Ishaan Verma' })).toBeVisible()
    await expect(page.getByText('A director.')).toBeVisible()
  })

  test('a person found by search opens their page', async ({ page }) => {
    await page.goto('/search?q=mara')
    await page.getByRole('link', { name: /Mara Whitfield/ }).click()

    await expect(page).toHaveURL('/person/3001')
    await expect(page.getByRole('heading', { level: 1, name: 'Mara Whitfield' })).toBeVisible()
  })

  test('shows facts, age and place of birth', async ({ page }) => {
    await page.goto('/person/3001')

    const facts = page.getByRole('complementary')
    await expect(facts.getByText('Acting', { exact: true })).toBeVisible()
    await expect(facts.getByText(/12 Apr 1975 \(age \d+\)/)).toBeVisible()
    await expect(facts.getByText('Leeds, England')).toBeVisible()
    await expect(page.getByRole('img', { name: 'Photo of Mara Whitfield' })).toBeVisible()
    await expect(facts.getByText('Died')).toHaveCount(0)
  })

  test('a long biography is cut and can be expanded and collapsed', async ({ page }) => {
    await page.goto('/person/3001')
    const bio = page.getByRole('region', { name: 'Biography' })

    await expect(bio.getByText('Paragraph 1:')).toBeVisible()
    await expect(bio.getByText('Paragraph 14:')).toHaveCount(0)
    await bio.getByRole('button', { name: 'Read more' }).click()
    await expect(bio.getByText('Paragraph 14:')).toBeVisible()
    await bio.getByRole('button', { name: 'Show less' }).click()
    await expect(bio.getByText('Paragraph 14:')).toHaveCount(0)
  })

  test('known-for posters open the title', async ({ page }) => {
    await page.goto('/person/3001')
    const known = page.getByRole('region', { name: 'Known for' })

    await expect(known.locator('a[href^="/title/"]').first()).toBeVisible()
    await known.locator('a[href^="/title/"]').first().click()
    await expect(page).toHaveURL(/\/title\/movie\/\d+/)
  })

  test('the filmography has Acting and Crew tabs, newest first, with roles', async ({ page }) => {
    await page.goto('/person/3001')
    const film = page.getByRole('region', { name: 'Filmography' })

    await expect(film.getByRole('tab', { name: /^Acting\s*40$/ })).toHaveAttribute('aria-selected', 'true')
    await expect(film.getByRole('tab', { name: /^Crew\s*2$/ })).toBeVisible() // 3 jobs on 2 titles = 2 lines
    const first = film.getByRole('listitem').first()
    await expect(first).toContainText('2024')
    await expect(first).toContainText('as Role 1')

    await film.getByRole('tab', { name: /^Crew/ }).click()
    await expect(film.getByRole('listitem')).toHaveCount(2)
    await expect(film.getByRole('listitem').first()).toContainText('Director, Writer')
    await expect(film.getByRole('listitem').nth(1)).toContainText('Producer')
  })

  test('long careers show 30 lines until “Show all”, and talk-show appearances are hidden', async ({ page }) => {
    await page.goto('/person/3001')
    const film = page.getByRole('region', { name: 'Filmography' })

    await expect(film.getByRole('listitem')).toHaveCount(30)
    await film.getByRole('button', { name: 'Show all 40' }).click()
    await expect(film.getByRole('listitem')).toHaveCount(40)
    await expect(film.getByText('Late Night With Guests')).toHaveCount(0)
  })

  test('a filmography line opens that title', async ({ page }) => {
    await page.goto('/person/3001')
    await page.getByRole('region', { name: 'Filmography' }).getByRole('listitem').first().getByRole('link').click()

    await expect(page).toHaveURL(/\/title\/movie\/1\d{3}/)
  })

  test('someone we know almost nothing about still gets a calm page', async ({ page }) => {
    await page.goto('/person/3002')

    await expect(page.getByRole('heading', { level: 1, name: 'Theo Marsh' })).toBeVisible()
    await expect(page.getByText('We don’t have a biography for Theo Marsh yet.')).toBeVisible()
    await expect(page.getByRole('region', { name: 'Filmography' })).toHaveCount(0)
    await expect(page.getByRole('region', { name: 'Known for' })).toHaveCount(0)
    await expect(page.getByRole('complementary').getByText('TM')).toBeVisible() // initials instead of a photo
  })

  test('a person who died shows when, and their age at death', async ({ page }) => {
    await page.goto('/person/3003')

    const facts = page.getByRole('complementary')
    await expect(facts.getByText('01 Feb 2005 (aged 75)')).toBeVisible()
    await expect(facts.getByText(/\(age \d+\)/)).toHaveCount(0)
  })

  test('unknown and malformed ids show friendly messages', async ({ page }) => {
    await page.goto('/person/99999')
    await expect(page.getByText('Person not found')).toBeVisible()

    await page.goto('/person/abc')
    await expect(page.getByText('That person doesn’t exist')).toBeVisible()
  })

  test('the page title is the person’s name', async ({ page }) => {
    await page.goto('/person/3001')
    await expect(page).toHaveTitle('Mara Whitfield · CineScope')
  })
})
