import { expect, type APIRequestContext, type Page } from '@playwright/test'

/**
 * Shared helpers of the browser tests. Two ways to prepare a test:
 *   - the UI helpers (login, signOut) do what a person does, for the flow under test;
 *   - the API helpers (createCustomer, openAccount, ...) set up data quickly through the same /api the app uses
 *     (the Vite dev server proxies it to the isolated e2e backend), so a test only spends browser time on what it
 *     actually checks.
 * Every test creates its own customer with a unique name, so tests never depend on each other or on leftover data.
 */

export interface Customer {
  username: string
  password: string
  fullName: string
  token: string
}

export const PASSWORD = 'E2e-Password-1!'

let counter = 0
/** Unique, readable suffix: time + counter, so parallel or repeated runs never collide. */
export function unique(): string {
  counter += 1
  return `${Date.now().toString(36)}${counter}${Math.random().toString(36).slice(2, 5)}`
}

export function registrationBody(suffix = unique()) {
  return {
    username: `e2e_${suffix}`,
    password: PASSWORD,
    fullName: `Eve Tester ${suffix}`,
    email: `e2e_${suffix}@example.com`,
    mobile: '9000000001',
    address: '1 Test Street',
  }
}

/** Registers a customer through the API. */
export async function createCustomer(request: APIRequestContext): Promise<Customer> {
  const body = registrationBody()
  const response = await request.post('/api/auth/register', { data: body })
  expect(response.status(), 'register').toBe(201)
  const { token } = await response.json()
  return { username: body.username, password: body.password, fullName: body.fullName, token }
}

const auth = (customer: Customer) => ({ Authorization: `Bearer ${customer.token}` })

export async function openAccount(
  request: APIRequestContext,
  customer: Customer,
  accountType: 'SAVINGS' | 'CURRENT' = 'SAVINGS',
  initialDeposit = 0,
): Promise<{ accountNumber: string }> {
  const response = await request.post('/api/accounts', { headers: auth(customer), data: { accountType, initialDeposit } })
  expect(response.status(), 'open account').toBe(201)
  return response.json()
}

export async function deposit(request: APIRequestContext, customer: Customer, accountNumber: string, amount: number) {
  const response = await request.post(`/api/accounts/${accountNumber}/deposit`, { headers: auth(customer), data: { amount } })
  expect(response.status(), 'deposit').toBe(200)
}

/** Returns the HTTP status instead of asserting, so tests can create deliberate failures (e.g. an overdraw). */
export async function withdraw(request: APIRequestContext, customer: Customer, accountNumber: string, amount: number) {
  const response = await request.post(`/api/accounts/${accountNumber}/withdraw`, { headers: auth(customer), data: { amount } })
  return response.status()
}

export async function transfer(request: APIRequestContext, customer: Customer, from: string, to: string, amount: number) {
  const response = await request.post('/api/transfers', { headers: auth(customer), data: { fromAccount: from, toAccount: to, amount } })
  expect(response.status(), 'transfer').toBe(201)
}

/** Signs in through the login form and waits for the landing page. */
export async function login(page: Page, username: string, password: string, landing: RegExp = /\/(dashboard|admin)/) {
  await page.goto('/login')
  await page.getByLabel('Username').fill(username)
  await page.getByLabel('Password', { exact: true }).fill(password)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page).toHaveURL(landing)
}

export async function loginAs(page: Page, customer: Customer) {
  await login(page, customer.username, customer.password, /\/dashboard/)
}

export async function signOut(page: Page) {
  // The sidebar is always in the DOM on desktop; on mobile it lives in the drawer.
  await page.getByRole('button', { name: 'Sign out' }).first().click()
  await expect(page).toHaveURL(/\/login/)
}

/** Formats like the app does (Indian grouping), for assertions on displayed money. */
export function inr(value: number): string {
  return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2 }).format(value)
}

/** Groups an account number like the UI: 1071103934 -> "1071 1039 34". */
export function grouped(accountNumber: string): string {
  return accountNumber.replace(/(\d{4})(?=\d)/g, '$1 ')
}
