const { defineConfig } = require('@playwright/test');

// The dashboard is a plain static page, so the tests open index.html straight from disk (no server).
// They use the Chrome installed on your computer; remove `channel` to use Playwright's own browser instead.
module.exports = defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  retries: 0,
  reporter: 'list',
  use: { channel: 'chrome', viewport: { width: 1400, height: 900 } },
});
