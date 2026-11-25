import { defineConfig } from '@playwright/test'

/**
 * Browser tests against the REAL backend and the seeded demo databases (run `npm run seed -- --yes` in ../backend first and
 * keep ../backend/.env). The servers start on their own ports (4101 and 5301), so the normal dev servers can stay running.
 * Browser: the Microsoft Edge on the machine; for Playwright's Chromium run `npx playwright install chromium` and set PW_CHANNEL=chromium.
 */
const channel = process.env.PW_CHANNEL === 'chromium' ? undefined : (process.env.PW_CHANNEL ?? 'msedge')

export default defineConfig({
  testDir: './e2e',
  timeout: 40_000,
  expect: { timeout: 8_000 },
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  use: { baseURL: 'http://localhost:5301', channel, trace: 'retain-on-failure', screenshot: 'only-on-failure' },
  webServer: [
    {
      command: 'node --env-file=../backend/.env ../backend/src/server.js',
      env: { PORT: '4101', RATE_LIMIT_PER_MINUTE: '20000' }, // the browser tests make many requests in a burst
      url: 'http://127.0.0.1:4101/api/health',
      reuseExistingServer: !!process.env.PW_REUSE,
      timeout: 30_000,
    },
    {
      command: 'npm run dev -- --port 5301 --strictPort',
      env: { API_TARGET: 'http://127.0.0.1:4101' },
      url: 'http://localhost:5301',
      reuseExistingServer: !!process.env.PW_REUSE,
      timeout: 60_000,
    },
  ],
})
