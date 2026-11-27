import base from './playwright.config';
import { defineConfig } from '@playwright/test';

/**
 * Same tests as playwright.config.ts, but you can WATCH them: a real Edge window opens and every click and
 * keystroke is slowed down by half a second. Run:   npx playwright test -c playwright.watch.config.ts
 */
export default defineConfig({
  ...base,
  timeout: 180_000,
  use: {
    ...base.use,
    headless: false,
    launchOptions: { slowMo: 500 },
    viewport: { width: 1280, height: 860 },
  },
});
