/**
 * Runs once before the browser tests and REFUSES to continue unless the backend under test is talking to the FAKE TMDB
 * with the FAKE token AND is running on the throw-away in-memory H2 database (never the real MySQL "cinescope" one,
 * where the accounts the tests create would otherwise pile up).
 *
 * Why: the tests must never depend on, or send traffic to, the real themoviedb.org, and the real TMDB token (kept in
 * backend/config/application.properties) must never be used by a test run. playwright.config.ts passes TMDB_BASE_URL and
 * TMDB_READ_TOKEN as environment variables, which Spring Boot ranks above that file - but if that ever stopped being
 * true, this check notices: it makes the backend fetch something and then asks the fake TMDB which token arrived.
 */
const BACKEND = 'http://localhost:8096'
const STUB = 'http://localhost:8097'

export default async function globalSetup() {
  await fetch(`${STUB}/__reset`)
  const meta = (await (await fetch(`${BACKEND}/api/meta`)).json()) as { database?: string }
  if (meta.database !== 'H2') {
    throw new Error(`E2E guard: the test backend reports database "${meta.database}", not the in-memory H2. Check DB_URL in playwright.config.ts. Run aborted so that no test account is created in a real database.`)
  }
  // The whole API needs a login now, so the guard signs up a throw-away user first (in the H2 database checked above).
  const guard = `guard_${Date.now().toString(36)}`
  const signUp = await fetch(`${BACKEND}/api/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: guard, email: `${guard}@example.com`, password: 'Guard-Setup-42!', displayName: 'Guard' }),
  })
  if (!signUp.ok) throw new Error(`E2E guard: could not create the guard user (HTTP ${signUp.status}).`)
  const { token } = (await signUp.json()) as { token: string }
  const answer = await fetch(`${BACKEND}/api/genres?type=movie`, { headers: { Authorization: `Bearer ${token}` } })
  if (!answer.ok) {
    throw new Error(`E2E guard: the test backend at ${BACKEND} answered HTTP ${answer.status} to /api/genres. Is it using the fake TMDB?`)
  }
  const stats = (await (await fetch(`${STUB}/__stats`)).json()) as { counts: Record<string, number>; lastAuth: string | null }
  if (!stats.counts['/3/genre/movie/list']) {
    throw new Error(
      `E2E guard: the backend answered, but the fake TMDB at ${STUB} never received its request, so the backend is talking ` +
        'to something else (the real TMDB?). Check TMDB_BASE_URL in playwright.config.ts. Run aborted.',
    )
  }
  if (stats.lastAuth !== 'Bearer e2e-token') {
    throw new Error('E2E guard: the backend sent a token other than the fake e2e token. The run was aborted so that the real token is never used by tests.')
  }
  await fetch(`${STUB}/__reset`)
}
