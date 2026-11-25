import { expect, test } from '@playwright/test'
import { createCustomer, loginAs } from './helpers'

/** API documentation reachability, light / dark theme and the responsive layout. */

const BACKEND = 'http://localhost:8091'

test.describe('API documentation', () => {
  test('the OpenAPI document and Swagger UI are served without logging in', async ({ page, request }) => {
    const response = await request.get(`${BACKEND}/v3/api-docs`)
    expect(response.status()).toBe(200)
    const doc = await response.json()
    expect(doc.info.title).toBe('Crestline Bank API')
    expect(Object.keys(doc.paths)).toEqual(expect.arrayContaining(['/api/transfers', '/api/auth/login']))

    await page.goto(`${BACKEND}/swagger-ui/index.html`)
    await expect(page.getByRole('heading', { name: /Crestline Bank API/ })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Authorize' })).toBeVisible()
    await expect(page.getByText('Transactions', { exact: true }).first()).toBeVisible()
  })
})

test.describe('theme', () => {
  test('dark mode can be switched on and is remembered after a reload', async ({ page }) => {
    await page.goto('/login')
    await expect(page.locator('html')).not.toHaveClass(/dark/)

    await page.getByRole('button', { name: 'Switch to dark mode' }).click()
    await expect(page.locator('html')).toHaveClass(/dark/)

    await page.reload()
    await expect(page.locator('html')).toHaveClass(/dark/)
    await page.getByRole('button', { name: 'Switch to light mode' }).click()
    await expect(page.locator('html')).not.toHaveClass(/dark/)
  })
})

test.describe('responsive layout', () => {
  test.use({ viewport: { width: 390, height: 844 } })

  test('on a phone the login photo panel is hidden and the form fits without sideways scrolling', async ({ page }) => {
    await page.goto('/login')
    await expect(page.getByText('Banking that moves')).toBeHidden()
    await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible()
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
    expect(overflow).toBeLessThanOrEqual(0)
  })

  test('on a phone navigation lives in a drawer that closes after choosing a page', async ({ page, request }) => {
    const customer = await createCustomer(request)
    await loginAs(page, customer)

    await expect(page.getByRole('link', { name: 'My accounts' })).toBeHidden() // sidebar is not shown on a phone
    await page.getByRole('button', { name: 'Open navigation' }).click()
    await page.getByRole('link', { name: 'My accounts' }).click()

    await expect(page).toHaveURL(/\/accounts$/)
    await expect(page.getByRole('link', { name: 'My accounts' })).toBeHidden() // drawer closed again
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
    expect(overflow).toBeLessThanOrEqual(0)
  })
})

test.describe('desktop layout', () => {
  test('the login page shows the photo panel and the sidebar is permanent after login', async ({ page, request }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/login')
    await expect(page.getByText('Banking that moves')).toBeVisible()

    const customer = await createCustomer(request)
    await loginAs(page, customer)
    const sidebar = page.getByRole('navigation', { name: 'Main' })
    await expect(sidebar.getByRole('link', { name: 'My accounts' })).toBeVisible()
    await expect(sidebar.getByRole('link', { name: 'Transfer money' })).toBeVisible()
  })
})
