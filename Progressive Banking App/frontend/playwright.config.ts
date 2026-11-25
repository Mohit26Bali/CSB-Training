import { resolve } from 'node:path'
import { defineConfig } from '@playwright/test'

/**
 * End-to-end browser tests (folder e2e/). `npm run e2e` starts everything itself:
 *   1. an isolated Spring Boot backend on port 8091 using an in-memory H2 database (seeded demo users). The database
 *      is chosen with the DB_URL environment variable below, and e2e/global-setup.ts aborts the whole run if the backend
 *      turns out to hold anything but the fresh seed data - so your real MySQL data cannot be touched by mistake;
 *   2. the Vite dev server on port 5174, proxying /api to that backend (API_TARGET, see vite.config.ts).
 * The ports differ from the normal 8080 / 5173, so the suite can run while the dev servers are up.
 *
 * Browser: uses the Microsoft Edge that is installed on the machine, so nothing needs downloading. To use Playwright's
 * own Chromium instead, run `npx playwright install chromium` once and set PW_CHANNEL=chromium.
 */
const channel = process.env.PW_CHANNEL === 'chromium' ? undefined : (process.env.PW_CHANNEL ?? 'msedge')
const mvnw = resolve(process.cwd(), '..', 'backend', process.platform === 'win32' ? 'mvnw.cmd' : 'mvnw')

export default defineConfig({
  testDir: './e2e',
  globalSetup: './e2e/global-setup.ts',
  timeout: 45_000,
  expect: { timeout: 8_000 },
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  outputDir: 'test-results',
  use: {
    baseURL: 'http://localhost:5174',
    channel,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    colorScheme: 'light',
  },
  webServer: [
    {
      // absolute path: the shell that runs the command does not look in the working directory on Windows
      // useTestClasspath puts the H2 driver (a test-scope dependency) on the classpath.
      command: `"${mvnw}" -q spring-boot:run -Dspring-boot.run.useTestClasspath=true`,
      cwd: '../backend',
      // These override the ${...:default} placeholders in backend application.properties.
      env: {
        SERVER_PORT: '8091',
        DB_URL: 'jdbc:h2:mem:e2e;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=20000',
        DB_USERNAME: 'sa',
        DB_PASSWORD: 'sa',
        JWT_SECRET: 'e2e-only-secret-not-used-anywhere-else-0123456789', // required by the backend (no default)
        SEED_DEMO_DATA: 'true', // the demo users (ravi, priya, admin) that the tests sign in as
        CORS_ORIGINS: 'http://localhost:5174', // the proxied browser sends this Origin header
        APP_SECURITY_MAX_LOGIN_ATTEMPTS: '1000', // the tests log in many times from one address
      },
      url: 'http://localhost:8091/v3/api-docs',
      timeout: 180_000,
      reuseExistingServer: !!process.env.PW_REUSE,
    },
    {
      command: 'npm run dev -- --port 5174 --strictPort',
      env: { API_TARGET: 'http://localhost:8091' },
      url: 'http://localhost:5174',
      timeout: 60_000,
      reuseExistingServer: !!process.env.PW_REUSE,
    },
  ],
})
