import { defineConfig } from '@playwright/test';

/**
 * Browser tests (D26). They drive the real screens against the real backend, so the whole stack must be running
 * (see README). They use the Microsoft Edge that Windows already has, so no browser is downloaded.
 * Settings: APP_URL (default http://localhost:5173), BROWSER_CHANNEL (default msedge; use "chrome" or leave empty for Playwright's own).
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 90_000,
  expect: { timeout: 10_000 },
  workers: 1, // the journey is one story told in order
  retries: 0,
  reporter: [['list']],
  use: {
    baseURL: process.env.APP_URL || 'http://localhost:5173',
    channel: process.env.BROWSER_CHANNEL ?? 'msedge',
    viewport: { width: 1280, height: 860 },
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
  },
  webServer: { command: 'npm run dev', url: process.env.APP_URL || 'http://localhost:5173', reuseExistingServer: true, timeout: 60_000 },
});
