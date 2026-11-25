import { BACKEND, createAccount, expect, PASSWORD, signInViaForm, signOut, test, uniqueUser } from './fixtures'

/**
 * Accounts in the browser: creating one, signing in and out, the messages for every kind of failure, the redirect that
 * brings a visitor back to the page they wanted, and that the session survives a reload. Runs against the real backend
 * with a throw-away in-memory database (see playwright.config.ts and global-setup.ts).
 */
// these tests are about signing in and out and about what a visitor sees, so they start signed out
test.use({ signedIn: false })

test.describe('accounts', () => {
  test('opening the site shows the sign-in screen and nothing of the app', async ({ page }) => {
    await page.goto('/')

    await expect(page).toHaveURL('/login')
    await expect(page.getByRole('heading', { level: 1, name: 'Welcome back' })).toBeVisible()
    await expect(page.getByRole('navigation', { name: 'Main' })).toHaveCount(0) // no app header
    await expect(page.getByRole('button', { name: /Account menu/ })).toHaveCount(0)
    await expect(page.getByRole('region', { name: 'Featured title' })).toHaveCount(0)
  })

  test('every page of the app sends a visitor to sign in and remembers where they were going', async ({ page }) => {
    for (const path of ['/browse?type=movie', '/search?q=mara', '/tonight', '/watchlist', '/title/movie/1001', '/person/3001', '/list/popular/movie', '/nowhere']) {
      await page.goto(path)
      await expect(page, path).toHaveURL(`/login?next=${encodeURIComponent(path)}`)
    }
  })

  test('after signing in from a deep link the visitor lands on that page, not the home page', async ({ page }) => {
    const username = uniqueUser()
    await createAccount(page, username)
    await page.goto('/title/movie/1002')
    await expect(page).toHaveURL(/\/login\?next=%2Ftitle%2Fmovie%2F1002/)

    await page.getByLabel('Username').fill(username)
    await page.getByLabel('Password').fill(PASSWORD)
    await page.getByRole('button', { name: 'Sign in' }).click()

    await expect(page).toHaveURL('/title/movie/1002')
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
  })

  test('the server refuses a visitor too: only the sign-in screens’ own data is open', async ({ page }) => {
    for (const path of ['/api/home', '/api/titles/movie/1001', '/api/titles/movie/1001/providers', '/api/people/3001', '/api/tonight', '/api/search?query=a', '/api/genres?type=movie', '/api/discover?type=movie']) {
      const res = await page.request.get(`${BACKEND}${path}`)
      expect(res.status(), path).toBe(401)
      expect((await res.json()).message).toBe('Please log in to continue')
    }
    const showcase = await page.request.get(`${BACKEND}/api/showcase`)
    expect(showcase.status()).toBe(200)
  })

  test('signing out returns to the sign-in screen, and the app cannot be reopened without signing in again', async ({ page }) => {
    const username = uniqueUser()
    await createAccount(page, username)
    await signInViaForm(page, username)
    await signOut(page)

    await page.goto('/browse?type=movie')
    await expect(page).toHaveURL(/\/login\?next=/)
    await page.goBack()
    await expect(page.getByRole('navigation', { name: 'Main' })).toHaveCount(0)
  })

  test('registering signs the new user in; the session survives a reload; signing out ends it', async ({ page }) => {
    const username = uniqueUser()
    await page.goto('/register')
    await page.getByLabel('Name', { exact: true }).fill('Asha Rao')
    await page.getByLabel('Username').fill(username)
    await page.getByLabel('Email').fill(`${username}@example.com`)
    await page.getByLabel('Password').fill(PASSWORD)
    await page.getByRole('button', { name: 'Create account' }).click()

    await expect(page).toHaveURL('/')
    const menu = page.getByRole('button', { name: 'Account menu for Asha Rao' })
    await expect(menu).toBeVisible()
    await expect(menu).toHaveText('AR')

    await page.reload()
    await expect(menu).toBeVisible()

    await menu.click()
    await expect(page.getByText(`@${username}`)).toBeVisible()
    await page.getByRole('menuitem', { name: 'Sign out' }).click()
    await expect(page).toHaveURL('/login')
    await page.reload()
    await expect(page.getByRole('heading', { level: 1, name: 'Welcome back' })).toBeVisible()
  })

  test('the sign-up form explains each problem before asking the server', async ({ page }) => {
    await page.goto('/register')
    const submit = page.getByRole('button', { name: 'Create account' })
    const alert = page.getByRole('alert')

    await submit.click()
    await expect(alert).toHaveText('Enter your name')

    await page.getByLabel('Name', { exact: true }).fill('Asha')
    await page.getByLabel('Username').fill('a b')
    await submit.click()
    await expect(alert).toContainText('Username must be')

    await page.getByLabel('Username').fill('asha_ok')
    await page.getByLabel('Email').fill('not-an-email')
    await submit.click()
    await expect(alert).toHaveText('Enter a valid email address')

    await page.getByLabel('Email').fill('asha@example.com')
    await page.getByLabel('Password').fill('short')
    await submit.click()
    await expect(alert).toHaveText('Password must be at least 8 characters')
    await expect(page.getByRole('button', { name: /Account menu/ })).toHaveCount(0)
  })

  test('a taken username is refused with the server’s message', async ({ page }) => {
    const username = uniqueUser()
    await createAccount(page, username)
    await page.goto('/register')
    await page.getByLabel('Name', { exact: true }).fill('Someone Else')
    await page.getByLabel('Username').fill(username.toUpperCase())
    await page.getByLabel('Email').fill(`other_${username}@example.com`)
    await page.getByLabel('Password').fill(PASSWORD)
    await page.getByRole('button', { name: 'Create account' }).click()
    await expect(page.getByRole('alert')).toContainText('is already taken')
  })

  test('signing in works with the right password and gives one message for every failure', async ({ page }) => {
    const username = uniqueUser()
    await createAccount(page, username, 'Ravi Kumar')

    await page.goto('/login')
    await page.getByLabel('Username').fill(username)
    await page.getByLabel('Password').fill('wrong-password')
    await page.getByRole('button', { name: 'Sign in' }).click()
    await expect(page.getByRole('alert')).toHaveText('Invalid username or password')

    await page.getByLabel('Username').fill('nobody_here')
    await page.getByRole('button', { name: 'Sign in' }).click()
    await expect(page.getByRole('alert')).toHaveText('Invalid username or password')

    await page.getByLabel('Username').fill(username)
    await page.getByLabel('Password').fill(PASSWORD)
    await page.getByRole('button', { name: 'Sign in' }).click()
    await expect(page.getByRole('button', { name: 'Account menu for Ravi Kumar' })).toBeVisible()
    await signOut(page)
  })

  test('the watchlist asks anonymous visitors to sign in, then returns them to it', async ({ page }) => {
    const username = uniqueUser()
    await createAccount(page, username)

    await page.goto('/watchlist?status=WATCHED')
    await expect(page).toHaveURL(/\/login\?next=%2Fwatchlist%3Fstatus%3DWATCHED/)

    await page.getByLabel('Username').fill(username)
    await page.getByLabel('Password').fill(PASSWORD)
    await page.getByRole('button', { name: 'Sign in' }).click()
    await expect(page).toHaveURL('/watchlist?status=WATCHED')
    await expect(page.getByRole('heading', { name: 'My watchlist' })).toBeVisible()
  })

  test('a ?next= pointing to another site is ignored', async ({ page }) => {
    const username = uniqueUser()
    await createAccount(page, username)
    await signInViaForm(page, username, '//evil.example/steal')
    await expect(page).toHaveURL('/')
  })

  test('visiting the login page while signed in goes straight on', async ({ page }) => {
    const username = uniqueUser()
    await createAccount(page, username)
    await signInViaForm(page, username)
    await page.goto('/login?next=%2Fbrowse%3Ftype%3Dmovie')
    await expect(page).toHaveURL('/browse?type=movie')
  })

  test('an expired or rejected token signs the user out on the next request', async ({ page }) => {
    const username = uniqueUser()
    await createAccount(page, username)
    await signInViaForm(page, username)
    await page.evaluate(() => {
      const session = JSON.parse(sessionStorage.getItem('cinescope.session')!)
      session.token = session.token.slice(0, -4) + 'AAAA' // a forged signature
      sessionStorage.setItem('cinescope.session', JSON.stringify(session))
    })
    await page.goto('/watchlist')
    await expect(page).toHaveURL(/\/login/)
    await expect(page.getByRole('heading', { level: 1, name: 'Welcome back' })).toBeVisible()
  })

  // ---- password strength ----

  const rule = (page: import('@playwright/test').Page, id: string) => page.locator(`[data-rule="${id}"]`)
  const mistake = (page: import('@playwright/test').Page, id: string) => page.locator(`[data-mistake="${id}"]`)
  const strength = (page: import('@playwright/test').Page) => page.getByTestId('password-strength')

  async function fillSignUp(page: import('@playwright/test').Page, username: string, password: string) {
    await page.getByLabel('Name', { exact: true }).fill('Asha Rao')
    await page.getByLabel('Username').fill(username)
    await page.getByLabel('Email').fill(`${username}@example.com`)
    await page.getByLabel('Password').fill(password)
  }

  test('before you type, one calm line says what is needed and no list of rules is shown', async ({ page }) => {
    await page.goto('/register')

    await expect(page.getByTestId('password-hint')).toHaveText('Use 8 to 20 characters with upper and lower case letters, a digit and a symbol.')
    await expect(page.locator('[data-rule]')).toHaveCount(0)
    await expect(page.locator('[data-mistake]')).toHaveCount(0)
  })

  test('only what is still missing is shown, and each requirement disappears the moment it is met', async ({ page }) => {
    await page.goto('/register')
    const password = page.getByLabel('Password')

    await password.fill('abc')
    for (const id of ['length', 'upper', 'digit', 'symbol']) await expect(rule(page, id)).toBeVisible()
    await expect(rule(page, 'lower')).toHaveCount(0) // already satisfied: not shown

    await password.fill('abcD')
    await expect(rule(page, 'upper')).toHaveCount(0)
    await password.fill('abcD1')
    await expect(rule(page, 'digit')).toHaveCount(0)
    await password.fill('abcD1!')
    await expect(rule(page, 'symbol')).toHaveCount(0)
    await expect(rule(page, 'length')).toHaveText('8 to 20 characters') // 6 characters so far: the last thing missing

    await password.fill('abcD1!xy')
    await expect(page.locator('[data-rule]')).toHaveCount(0)
    await expect(page.getByTestId('password-ok')).toHaveText('✓ All requirements met')
  })

  test('a mistake is called out in plain words as soon as it happens', async ({ page }) => {
    await page.goto('/register')
    const password = page.getByLabel('Password')

    await password.fill('Password1!')
    await expect(mistake(page, 'common')).toContainText('That password is too common. Choose something harder to guess.')
    await expect(page.getByTestId('password-ok')).toHaveCount(0)

    await password.fill('Gooood-Day-1')
    await expect(mistake(page, 'repeat')).toContainText('Avoid repeating the same character more than 3 times in a row.')
    await expect(mistake(page, 'common')).toHaveCount(0) // fixed: the old message goes away

    await page.getByLabel('Username').fill('asha')
    await password.fill('Asha-pass-1!')
    await expect(mistake(page, 'identity')).toContainText('Your password contains your username or email. Choose something different.')

    await password.fill('Sunset-Rain-42!')
    await expect(page.locator('[data-mistake]')).toHaveCount(0)
    await expect(page.getByTestId('password-ok')).toBeVisible()
  })

  test('the strength bar follows the password', async ({ page }) => {
    await page.goto('/register')
    const password = page.getByLabel('Password')

    await password.fill('abc')
    await expect(strength(page)).toHaveText('Weak')
    await password.fill('Password1!')
    await expect(strength(page)).toHaveText('Fair')
    await password.fill('Sunset9!')
    await expect(strength(page)).toHaveText('Good')
    await password.fill('Sunset-Rain-42!')
    await expect(strength(page)).toHaveText('Strong')
  })

  test('a password cannot be longer than 20 characters', async ({ page }) => {
    await page.goto('/register')
    const password = page.getByLabel('Password')

    await password.pressSequentially('Sunset-Rain-42!abcdefghij') // 25 characters typed
    expect(await password.inputValue()).toHaveLength(20)
    await expect(password).toHaveAttribute('maxlength', '20')
  })

  test('a weak password is stopped with a message naming what is missing, and nothing is created', async ({ page }) => {
    const username = uniqueUser()
    await page.goto('/register')
    await fillSignUp(page, username, 'password1')
    await page.getByRole('button', { name: 'Create account' }).click()

    await expect(page.getByRole('alert')).toHaveText('Password must include an uppercase letter and a symbol such as ! @ # or -')
    await expect(page).toHaveURL(/\/register/)
    await expect(page.getByRole('button', { name: /Account menu/ })).toHaveCount(0)
  })

  test('common passwords and passwords containing your username are refused', async ({ page }) => {
    const username = uniqueUser()
    await page.goto('/register')
    await fillSignUp(page, username, 'Password1!')
    await page.getByRole('button', { name: 'Create account' }).click()
    await expect(page.getByRole('alert')).toHaveText('Password is too common; choose something harder to guess')

    await page.getByLabel('Password').fill(`${username}-A1!`)
    await page.getByRole('button', { name: 'Create account' }).click()
    await expect(page.getByRole('alert')).toHaveText('Password must not contain your username')
  })

  test('a strong password is accepted', async ({ page }) => {
    const username = uniqueUser()
    await page.goto('/register')
    await fillSignUp(page, username, 'Sunset-Rain-42!')
    await page.getByRole('button', { name: 'Create account' }).click()
    await expect(page.getByRole('button', { name: /Account menu/ })).toBeVisible()
  })

  test('the server refuses weak and over-long passwords too, so the form is not the only guard', async ({ page }) => {
    const username = uniqueUser()
    const post = (password: string) =>
      page.request.post(`${BACKEND}/api/auth/register`, { data: { username, email: `${username}@example.com`, password, displayName: 'Sneaky' } })

    const weak = await post('password1')
    expect(weak.status()).toBe(400)
    expect((await weak.json()).fieldErrors.password).toBe('password must include an uppercase letter and a symbol such as ! @ # or -')
    expect((await post('Welcome@2024')).status()).toBe(400)

    const tooLong = await post('Sunset-Rain-42!abcdef') // 21 characters
    expect(tooLong.status()).toBe(400)
    expect((await tooLong.json()).fieldErrors.password).toBe('password must be 8 to 20 characters')
  })
})
