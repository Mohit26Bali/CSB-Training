import { resolve } from 'node:path'
import { defineConfig } from '@playwright/test'

/**
 * Browser end-to-end tests (folder e2e/). `npm run e2e` starts everything itself, on ports that differ from the normal
 * dev ports so the suite can run while the dev servers are up:
 *   1. a FAKE TMDB on 8097 (e2e/tmdb-stub.mjs): invented films, deterministic answers, simulated failures;
 *   2. the REAL Spring Boot backend on 8096, pointed at the fake (TMDB_BASE_URL) with a fake token (TMDB_READ_TOKEN)
 *      and an in-memory H2 database (DB_URL), so JSON mapping, caching, accounts and error handling run for real but
 *      nothing ever reaches themoviedb.org or the real MySQL database;
 *   3. the Vite dev server on 5181, proxying /api to that backend (API_TARGET, see vite.config.ts).
 * e2e/global-setup.ts aborts the run if the backend turns out not to be talking to the fake with the fake token.
 *
 * Browser: the Microsoft Edge installed on the machine (nothing to download). For Playwright's own Chromium run
 * `npx playwright install chromium` once and set PW_CHANNEL=chromium. PW_REUSE=1 reuses servers that are already running.
 */
const channel = process.env.PW_CHANNEL === 'chromium' ? undefined : (process.env.PW_CHANNEL ?? 'msedge')
const mvnw = resolve(process.cwd(), '..', 'backend', process.platform === 'win32' ? 'mvnw.cmd' : 'mvnw')
const reuse = !!process.env.PW_REUSE

export default defineConfig({
  testDir: './e2e',
  globalSetup: './e2e/global-setup.ts',
  timeout: 40_000,
  expect: { timeout: 8_000 },
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  outputDir: 'test-results',
  use: {
    baseURL: 'http://localhost:5181',
    channel,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    colorScheme: 'dark',
  },
  webServer: [
    {
      command: 'node e2e/tmdb-stub.mjs',
      url: 'http://localhost:8097/__health',
      timeout: 20_000,
      reuseExistingServer: reuse,
    },
    {
      // absolute path: the shell that runs the command does not look in the working directory on Windows
      // useTestClasspath: the in-memory H2 database is a test-scope dependency of the backend.
      command: `"${mvnw}" -q spring-boot:run -Dspring-boot.run.useTestClasspath=true`,
      cwd: '../backend',
      // Environment variables outrank every properties file, including backend/config/application.properties.
      // DB_*: a throw-away in-memory H2 database, so the suite never touches the real MySQL "cinescope" database.
      env: {
        SERVER_PORT: '8096',
        TMDB_BASE_URL: 'http://localhost:8097/3',
        TMDB_READ_TOKEN: 'e2e-token',
        DB_URL: 'jdbc:h2:mem:cinescope_e2e;DB_CLOSE_DELAY=-1',
        DB_USERNAME: 'sa',
        DB_PASSWORD: '',
        JWT_SECRET: 'e2e-only-secret-not-used-anywhere-else-0123456789', // required by the backend (no default)
        CORS_ORIGINS: 'http://localhost:5181',
        MAX_LOGIN_ATTEMPTS: '1000',
        SEED_DEMO_DATA: 'true', // the demo accounts (asha, ravi ...) that e2e/demo-data.spec.ts signs in as
      },
      url: 'http://localhost:8096/v3/api-docs',
      timeout: 180_000,
      reuseExistingServer: reuse,
    },
    {
      command: 'npm run dev -- --port 5181 --strictPort',
      env: { API_TARGET: 'http://localhost:8096' },
      url: 'http://localhost:5181',
      timeout: 60_000,
      reuseExistingServer: reuse,
    },
  ],
})
