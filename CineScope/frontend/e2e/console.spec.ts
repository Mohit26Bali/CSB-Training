import { test, expect } from './fixtures'

/** React reports problems such as duplicate keys through console.error; a clean page must not log any. */
test('the title page logs no React errors (for example duplicate keys)', async ({ page }) => {
  const errors: string[] = []
  page.on('console', (m) => {
    if (m.type() === 'error' && !m.text().startsWith('Failed to load resource')) errors.push(m.text())
  })
  await page.goto('/title/movie/1001')
  await expect(page.getByRole('heading', { level: 1 }).first()).toBeVisible()
  await page.waitForTimeout(1000)
  expect(errors).toEqual([])
})
