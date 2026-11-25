import { expect, test } from '@playwright/test'
import { login, PASSWORD, registrationBody, signOut } from './helpers'

/**
 * Sign-in, registration and route protection as a person experiences them in the browser.
 * (Backend rules for the same features are tested in backend/src/test; here we check the screens and redirects.)
 */
test.describe('authentication', () => {
  test('an anonymous visitor is sent to the login page, branded Crestline Bank', async ({ page }) => {
    await page.goto('/dashboard')
    await expect(page).toHaveURL(/\/login/)
    await expect(page).toHaveTitle('Crestline Bank')
    await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible()
    await expect(page.getByText('Crestline Bank').first()).toBeVisible()
  })

  test('a wrong password shows a generic error and keeps the user on the login page', async ({ page }) => {
    await page.goto('/login')
    await page.getByLabel('Username').fill('ravi')
    await page.getByLabel('Password', { exact: true }).fill('definitely-wrong')
    await page.getByRole('button', { name: 'Sign in' }).click()

    await expect(page.getByRole('alert')).toContainText('Invalid username or password')
    await expect(page).toHaveURL(/\/login/)
  })

  test('the demo-account buttons fill the form and a seeded customer can sign in', async ({ page }) => {
    await page.goto('/login')
    await page.getByRole('button', { name: 'Customer · Ravi' }).click()
    await expect(page.getByLabel('Username')).toHaveValue('ravi')
    await page.getByRole('button', { name: 'Sign in' }).click()

    await expect(page).toHaveURL(/\/dashboard/)
    await expect(page.getByText(/Good (morning|afternoon|evening), Ravi/)).toBeVisible()
  })

  test('the password can be shown and hidden', async ({ page }) => {
    await page.goto('/login')
    const password = page.getByLabel('Password', { exact: true })
    await password.fill('secret')
    await expect(password).toHaveAttribute('type', 'password')
    await page.getByRole('button', { name: 'Show password' }).click()
    await expect(password).toHaveAttribute('type', 'text')
  })

  test('registration validates the form before sending it', async ({ page }) => {
    await page.goto('/register')
    await page.getByRole('button', { name: 'Create account' }).click()

    await expect(page.getByText('Enter your full name')).toBeVisible()
    await expect(page.getByText('Enter a valid email address')).toBeVisible()
    await expect(page.getByText('Mobile number must be 10 digits starting with 6-9')).toBeVisible()
    await expect(page.getByText('Password must be at least 8 characters')).toBeVisible()
    await expect(page).toHaveURL(/\/register/)
  })

  test('mismatching passwords are caught', async ({ page }) => {
    const body = registrationBody()
    await page.goto('/register')
    await page.getByLabel('Full name').fill(body.fullName)
    await page.getByLabel('Email').fill(body.email)
    await page.getByLabel('Mobile number').fill(body.mobile)
    await page.getByLabel('Address').fill(body.address)
    await page.getByLabel('Choose a username').fill(body.username)
    await page.getByLabel('Password', { exact: true }).fill(PASSWORD)
    await page.getByLabel('Confirm password').fill('something-else-1')
    await page.getByRole('button', { name: 'Create account' }).click()

    await expect(page.getByText('Passwords do not match')).toBeVisible()
  })

  test('a new customer can register, lands on the dashboard, and signs out again', async ({ page }) => {
    const body = registrationBody()
    await page.goto('/register')
    await page.getByLabel('Full name').fill(body.fullName)
    await page.getByLabel('Email').fill(body.email)
    await page.getByLabel('Mobile number').fill(body.mobile)
    await page.getByLabel('Address').fill(body.address)
    await page.getByLabel('Choose a username').fill(body.username)
    await page.getByLabel('Password', { exact: true }).fill(PASSWORD)
    await page.getByLabel('Confirm password').fill(PASSWORD)
    await page.getByRole('button', { name: 'Create account' }).click()

    await expect(page).toHaveURL(/\/dashboard/)
    await expect(page.getByText(`Eve Tester`).first()).toBeVisible()
    await expect(page.getByText('You don’t have an account yet')).toBeVisible()

    await signOut(page)
    // the session is gone: a protected URL bounces back to login
    await page.goto('/dashboard')
    await expect(page).toHaveURL(/\/login/)
  })

  test('registering a taken username is reported by the server', async ({ page }) => {
    const body = registrationBody()
    await page.goto('/register')
    await page.getByLabel('Full name').fill(body.fullName)
    await page.getByLabel('Email').fill(body.email)
    await page.getByLabel('Mobile number').fill(body.mobile)
    await page.getByLabel('Address').fill(body.address)
    await page.getByLabel('Choose a username').fill('ravi') // seeded
    await page.getByLabel('Password', { exact: true }).fill(PASSWORD)
    await page.getByLabel('Confirm password').fill(PASSWORD)
    await page.getByRole('button', { name: 'Create account' }).click()

    await expect(page.getByRole('alert')).toContainText("Username 'ravi' is already taken")
  })

  test('each role stays inside its own area', async ({ page }) => {
    await login(page, 'ravi', 'customer123')
    await page.goto('/admin/accounts')
    await expect(page).toHaveURL(/\/dashboard/) // customer bounced from admin pages
    await signOut(page)

    await login(page, 'admin', 'admin12345')
    await page.goto('/transfer')
    await expect(page).toHaveURL(/\/admin/) // admin bounced from customer pages
  })

  test('unknown URLs show the not-found page', async ({ page }) => {
    await page.goto('/this-does-not-exist')
    await expect(page.getByText('404')).toBeVisible()
  })

  // ---- password strength ----

  const rule = (page: import('@playwright/test').Page, id: string) => page.locator(`[data-rule="${id}"]`)
  const mistake = (page: import('@playwright/test').Page, id: string) => page.locator(`[data-mistake="${id}"]`)
  const strength = (page: import('@playwright/test').Page) => page.getByTestId('password-strength')

  async function fillSignUp(page: import('@playwright/test').Page, body: ReturnType<typeof registrationBody>, password: string) {
    await page.getByLabel('Full name').fill(body.fullName)
    await page.getByLabel('Email').fill(body.email)
    await page.getByLabel('Mobile number').fill(body.mobile)
    await page.getByLabel('Address').fill(body.address)
    await page.getByLabel('Choose a username').fill(body.username)
    await page.getByLabel('Password', { exact: true }).fill(password)
    await page.getByLabel('Confirm password').fill(password)
  }

  test('before you type, one calm line says what is needed and no list of rules is shown', async ({ page }) => {
    await page.goto('/register')

    await expect(page.getByTestId('password-hint')).toHaveText('Use 8 to 20 characters with upper and lower case letters, a digit and a symbol.')
    await expect(page.locator('[data-rule]')).toHaveCount(0)
    await expect(page.locator('[data-mistake]')).toHaveCount(0)
  })

  test('only what is still missing is shown, and each requirement disappears the moment it is met', async ({ page }) => {
    await page.goto('/register')
    const password = page.getByLabel('Password', { exact: true })

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
    const password = page.getByLabel('Password', { exact: true })

    await password.fill('Password1!')
    await expect(mistake(page, 'common')).toContainText('That password is too common. Choose something harder to guess.')
    await expect(page.getByTestId('password-ok')).toHaveCount(0)

    await password.fill('Gooood-Day-1')
    await expect(mistake(page, 'repeat')).toContainText('Avoid repeating the same character more than 3 times in a row.')
    await expect(mistake(page, 'common')).toHaveCount(0) // fixed: the old message goes away

    await page.getByLabel('Choose a username').fill('asha')
    await password.fill('Asha-pass-1!')
    await expect(mistake(page, 'identity')).toContainText('Your password contains your username or email. Choose something different.')

    await password.fill('Sunset-Rain-42!')
    await expect(page.locator('[data-mistake]')).toHaveCount(0)
    await expect(page.getByTestId('password-ok')).toBeVisible()
  })

  test('the strength bar follows the password', async ({ page }) => {
    await page.goto('/register')
    const password = page.getByLabel('Password', { exact: true })

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
    const password = page.getByLabel('Password', { exact: true })

    await password.pressSequentially('Sunset-Rain-42!abcdefghij') // 25 characters typed
    expect(await password.inputValue()).toHaveLength(20)
    await expect(password).toHaveAttribute('maxlength', '20')
  })

  test('a weak password is stopped under the field, naming what is missing, and nothing is created', async ({ page }) => {
    await page.goto('/register')
    await fillSignUp(page, registrationBody(), 'password1')
    await page.getByRole('button', { name: 'Create account' }).click()

    await expect(page.getByText('Password must include an uppercase letter and a symbol such as ! @ # or -')).toBeVisible()
    await expect(page).toHaveURL(/\/register/)
  })

  test('common passwords and passwords containing your username are refused', async ({ page }) => {
    const body = registrationBody()
    await page.goto('/register')
    await fillSignUp(page, body, 'Password1!')
    await page.getByRole('button', { name: 'Create account' }).click()
    await expect(page.getByText('Password is too common; choose something harder to guess')).toBeVisible()

    const withName = `${body.username}A1!`
    await page.getByLabel('Password', { exact: true }).fill(withName)
    await page.getByLabel('Confirm password').fill(withName)
    await page.getByRole('button', { name: 'Create account' }).click()
    await expect(page.getByText('Password must not contain your username')).toBeVisible()
  })

  test('the server refuses weak and over-long passwords too, so the form is not the only guard', async ({ page }) => {
    const body = registrationBody()
    const weak = await page.request.post('/api/auth/register', { data: { ...body, password: 'password1' } })
    expect(weak.status()).toBe(400)
    expect((await weak.json()).fieldErrors.password).toBe('password must include an uppercase letter and a symbol such as ! @ # or -')
    expect((await page.request.post('/api/auth/register', { data: { ...body, password: 'Welcome@2024' } })).status()).toBe(400)

    const identity = await page.request.post('/api/auth/register', { data: { ...body, password: `${body.username}A1!` } })
    expect(identity.status()).toBe(400)
    expect((await identity.json()).message).toBe('password must not contain your username')

    const tooLong = await page.request.post('/api/auth/register', { data: { ...body, password: 'Sunset-Rain-42!abcdef' } }) // 21 characters
    expect(tooLong.status()).toBe(400)
    expect((await tooLong.json()).fieldErrors.password).toBe('password must be 8 to 20 characters')
  })
})
