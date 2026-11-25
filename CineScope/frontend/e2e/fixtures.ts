import { expect, test as base, type Page } from '@playwright/test'

/**
 * Shared test setup. Every spec imports {@code test} and {@code expect} from here instead of from Playwright.
 *
 * The extended {@code page} fixture intercepts the three outside hosts the app talks to from the browser (TMDB images,
 * YouTube thumbnails, the YouTube player), so tests are fast and offline: images become a 1x1 picture and the player a
 * plain page whose address still contains the video id, which the tests assert on. The backend API is NOT intercepted -
 * it is the real one, talking to the fake TMDB (e2e/tmdb-stub.mjs).
 */
const PIXEL = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==', 'base64')

export const STUB = 'http://localhost:8097'
export const BACKEND = 'http://localhost:8096'
export const PASSWORD = 'E2e-Pass-123!'

let counter = 0
/** A username no earlier test (or earlier run against a reused server) has taken. */
export const uniqueUser = (prefix = 'e2e') => `${prefix}_${Date.now().toString(36)}${counter++}`

/** The account a test is signed in as (see the {@code signedIn} option below). */
export interface Account {
  username: string
  token: string
  /** What the app keeps in sessionStorage (src/lib/session.ts) while someone is signed in. */
  session: string
}

/**
 * Every app page except the sign-in screens needs a login, so by default each test starts SIGNED IN as a brand-new user:
 * the account is created through the API, its token goes into the Authorization header of every API request the test makes
 * directly (page.request), and the browser page starts with the session already in sessionStorage. A spec about signing in
 * or out, or about what a visitor sees, opts out with {@code test.use({ signedIn: false })}.
 *
 * The extended {@code page} fixture also intercepts the three outside hosts the app talks to from the browser (TMDB images,
 * YouTube thumbnails, the YouTube player), so tests are fast and offline: images become a 1x1 picture and the player a
 * plain page whose address still contains the video id, which the tests assert on. The backend API is NOT intercepted -
 * it is the real one, talking to the fake TMDB (e2e/tmdb-stub.mjs).
 */
export const test = base.extend<{ page: Page; signedIn: boolean; account: Account | null }>({
  signedIn: [true, { option: true }],

  account: async ({ playwright, signedIn }, runTest) => {
    if (!signedIn) {
      await runTest(null)
      return
    }
    const username = uniqueUser('viewer')
    const api = await playwright.request.newContext({ baseURL: BACKEND })
    const res = await api.post('/api/auth/register', {
      data: { username, email: `${username}@example.com`, password: PASSWORD, displayName: 'Test Viewer' },
    })
    expect(res.status(), 'creating the default test account').toBe(201)
    const auth = await res.json()
    await api.dispose()
    const session = JSON.stringify({
      token: auth.token,
      username: auth.username,
      displayName: auth.displayName,
      region: auth.region,
      expiresAt: Date.now() + auth.expiresInSeconds * 1000,
    })
    await runTest({ username, token: auth.token, session })
  },

  extraHTTPHeaders: async ({ account }, runTest) => {
    await runTest(account ? { Authorization: `Bearer ${account.token}` } : {})
  },

  page: async ({ page, account }, runTest) => {
    if (account) {
      await page.addInitScript(
        ([key, value]) => {
          try {
            if (!sessionStorage.getItem(key)) sessionStorage.setItem(key, value)
          } catch {
            /* storage blocked: the test will fail visibly */
          }
        },
        ['cinescope.session', account.session],
      )
    }
    await page.route('**/image.tmdb.org/**', (route) => route.fulfill({ status: 200, contentType: 'image/png', body: PIXEL }))
    await page.route('**/img.youtube.com/**', (route) => route.fulfill({ status: 200, contentType: 'image/png', body: PIXEL }))
    await page.route('**/youtube-nocookie.com/**', (route) =>
      route.fulfill({ status: 200, contentType: 'text/html', body: '<!doctype html><title>fake player</title><p>fake video player</p>' }),
    )
    await runTest(page)
  },
})

export { expect }

/** All poster cards (links to a title page) in the main content area. */
export const cards = (page: Page) => page.locator('main a[href^="/title/"]')

/** The <select> inside the filter label with this text on the Browse page. */
export const filter = (page: Page, label: string) => page.locator('label', { hasText: label }).locator('select')



/** Creates an account straight through the backend API (fast; the sign-in itself is then done by the test). */
export async function createAccount(page: Page, username: string, displayName = 'Test Person') {
  const res = await page.request.post(`${BACKEND}/api/auth/register`, {
    data: { username, email: `${username}@example.com`, password: PASSWORD, displayName },
  })
  expect(res.status(), 'account creation').toBe(201)
}

/** Signs in through the real login form, so the app's own session handling is what gets exercised. */
export async function signInViaForm(page: Page, username: string, next = '/') {
  await page.goto(`/login?next=${encodeURIComponent(next)}`)
  await page.getByLabel('Username').fill(username)
  await page.getByLabel('Password').fill(PASSWORD)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('button', { name: /Account menu/ })).toBeVisible()
}

export async function signOut(page: Page) {
  await page.getByRole('button', { name: /Account menu/ }).click()
  await page.getByRole('menuitem', { name: 'Sign out' }).click()
  await expect(page).toHaveURL(/\/login/)
  await expect(page.getByRole('heading', { level: 1, name: 'Welcome back' })).toBeVisible()
}


export async function stubStats(page: Page): Promise<{ counts: Record<string, number>; lastAuth: string | null }> {
  return (await page.request.get(`${STUB}/__stats`)).json()
}
