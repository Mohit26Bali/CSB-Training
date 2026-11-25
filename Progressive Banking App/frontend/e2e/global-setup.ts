/**
 * Runs once before the browser tests (playwright.config.ts#globalSetup) and REFUSES to continue unless the backend
 * under test is the fresh, in-memory e2e database.
 *
 * Why this exists: the tests register customers, move money and block accounts. If the e2e backend were ever pointed
 * at the real MySQL "bank" database by mistake (a missing setting, a typo), those tests would silently fill it with
 * garbage. A freshly started in-memory backend contains exactly the 3 accounts created by the seed data
 * (backend config/DataInitializer: ravi 1, priya 2); anything else means this is not the isolated database, and the
 * run is aborted before a single test touches it.
 */
const BACKEND = 'http://localhost:8091'
const SEEDED_ACCOUNTS = 3

export default async function globalSetup() {
  const login = await fetch(`${BACKEND}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: 'admin12345' }),
  })
  if (!login.ok) {
    throw new Error(`E2E guard: could not log in to the test backend at ${BACKEND} (HTTP ${login.status}).`)
  }
  const { token } = (await login.json()) as { token: string }

  const accounts = await fetch(`${BACKEND}/api/admin/accounts`, { headers: { Authorization: `Bearer ${token}` } })
  const list = (await accounts.json()) as unknown[]
  if (list.length !== SEEDED_ACCOUNTS) {
    throw new Error(
      `E2E guard: the backend at ${BACKEND} has ${list.length} accounts, but a fresh in-memory test database has exactly ` +
        `${SEEDED_ACCOUNTS}. It is probably connected to a real database, so the run was aborted to protect it. ` +
        'Check the DB_URL setting in playwright.config.ts.',
    )
  }
}
