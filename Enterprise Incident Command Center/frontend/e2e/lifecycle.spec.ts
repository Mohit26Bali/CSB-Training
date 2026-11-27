import { expect, test, type Page } from '@playwright/test';
import { choose, loginAs, logout, tag } from './helpers';

/**
 * The incident life cycle (D32, D34, D39), told through the real screens:
 *   story 1: report, accept, hold, resume, resolve, reopen by the reporter, resolve again, close, and "Closed is final".
 *   story 2: report, accept, cancel, and "Cancelled is final".
 * The tests run in order and share one incident per story. Everything they create starts with "[e2e-pw]".
 */
test.describe.configure({ mode: 'serial' });

let number = '';
let url = '';

const chip = (page: Page, text: string | RegExp) => page.locator('.MuiChip-root', { hasText: text });

async function report(page: Page, title: string) {
  await loginAs(page, 'ananya@icc.com');
  await page.getByRole('link', { name: 'Report an incident' }).first().click();
  await page.getByLabel('Short description').fill(title);
  await page.getByLabel(/^Description/).fill('Created by the Playwright life-cycle test.');
  await choose(page, 'Category', 'Network');
  await choose(page, 'Subcategory', 'VPN');
  await choose(page, 'Impact', /3 - Low/);
  await choose(page, 'Urgency', /3 - Low/);
  await page.getByRole('button', { name: 'Report incident' }).click();
  await expect(page.getByText('Your incident was reported')).toBeVisible();
  const heading = page.getByRole('heading', { level: 1 });
  await expect(heading).toHaveText(/^INC\d+$/);
  number = (await heading.textContent()) ?? '';
  url = new URL(page.url()).pathname;
  await logout(page);
}

async function open(page: Page) {
  await page.goto(url);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText(number);
}

// Only the text buttons: the success alert also has a small "Close" icon button.
const button = (page: Page, name: string) => page.getByRole('button', { name, exact: true }).and(page.locator('.MuiButton-root'));

// ------------------------------------------------------------------------------------------------ story 1

test.describe('story 1: from a report to a closed incident', () => {
  test('the employee reports it: it is New and nobody has it yet', async ({ page }) => {
    await report(page, `[e2e-pw] ${tag} VPN keeps dropping`);
    await loginAs(page, 'ananya@icc.com');
    await open(page);
    await expect(chip(page, 'New')).toBeVisible();
    // An employee cannot work the incident, only talk about it.
    await expect(button(page, 'Accept')).toHaveCount(0);
    await expect(button(page, 'Resolve')).toHaveCount(0);
    await expect(button(page, 'Put on hold')).toHaveCount(0);
  });

  test('the engineer accepts it and puts it on hold, then resumes it', async ({ page }) => {
    await loginAs(page, 'sai@icc.com');
    await open(page);
    await button(page, 'Accept').click();
    await expect(page.getByText(`You accepted ${number}.`)).toBeVisible();
    await expect(chip(page, 'In progress')).toBeVisible();

    await button(page, 'Put on hold').click();
    const dialog = page.getByRole('dialog');
    // The dialog waits for a reason.
    await expect(dialog.getByRole('button', { name: 'Put on hold' })).toBeDisabled();
    await choose(page, 'On hold reason', 'Awaiting vendor');
    await dialog.getByLabel(/Work note/).fill('Waiting for the VPN vendor ticket.');
    await dialog.getByRole('button', { name: 'Put on hold' }).click();
    await expect(page.getByText(`${number} is on hold.`)).toBeVisible();
    await expect(chip(page, /on hold/i)).toBeVisible();
    // A held incident cannot be resolved until it is resumed.
    await expect(button(page, 'Resolve')).toHaveCount(0);

    await button(page, 'Resume').click();
    await expect(page.getByText(`${number} is back in progress.`)).toBeVisible();
    await expect(chip(page, 'In progress')).toBeVisible();
  });

  test('resolving needs a resolution code and notes', async ({ page }) => {
    await loginAs(page, 'sai@icc.com');
    await open(page);
    await button(page, 'Resolve').click();
    const dialog = page.getByRole('dialog');
    await expect(dialog.getByRole('button', { name: 'Resolve' })).toBeDisabled();
    await choose(page, 'Resolution code', 'Solution provided');
    await expect(dialog.getByRole('button', { name: 'Resolve' })).toBeDisabled(); // the notes are still empty
    await dialog.getByLabel('Resolution notes').fill('Replaced the VPN profile.');
    await dialog.getByRole('button', { name: 'Resolve' }).click();
    await expect(page.getByText(`${number} was resolved.`)).toBeVisible();
    await expect(chip(page, 'Resolved')).toBeVisible();
    // Resolved is not final: it can be closed, and the reporter can reopen it.
    await expect(button(page, 'Close')).toBeVisible();
    await expect(button(page, 'Reopen')).toBeVisible();
  });

  test('the reporter reopens it with a reason, and it is back in progress', async ({ page }) => {
    await loginAs(page, 'ananya@icc.com');
    await open(page);
    await button(page, 'Reopen').click();
    const dialog = page.getByRole('dialog');
    await expect(dialog.getByRole('button', { name: 'Reopen' })).toBeDisabled(); // a reason is needed
    await dialog.getByLabel('Why is it reopened?').fill('It dropped again this morning.');
    await dialog.getByRole('button', { name: 'Reopen' }).click();
    await expect(page.getByText(`${number} was reopened.`)).toBeVisible();
    await expect(chip(page, 'In progress')).toBeVisible();
    await expect(page.getByText('Reopened: It dropped again this morning.')).toBeVisible();
  });

  test('the engineer resolves it again and closes it', async ({ page }) => {
    await loginAs(page, 'sai@icc.com');
    await open(page);
    await button(page, 'Resolve').click();
    const dialog = page.getByRole('dialog');
    await choose(page, 'Resolution code', 'Workaround provided');
    await dialog.getByLabel('Resolution notes').fill('Moved the user to the backup gateway.');
    await dialog.getByRole('button', { name: 'Resolve' }).click();
    await expect(chip(page, 'Resolved')).toBeVisible();

    await button(page, 'Close').click();
    await expect(page.getByText(`${number} was closed.`)).toBeVisible();
    await expect(chip(page, 'Closed')).toBeVisible();
  });

  test('Closed is final: no comment form, no Reopen, for the engineer and for the reporter', async ({ page }) => {
    await loginAs(page, 'sai@icc.com');
    await open(page);
    await expect(page.getByText('This incident is closed.')).toBeVisible();
    await expect(button(page, 'Add')).toHaveCount(0);
    await expect(button(page, 'Reopen')).toHaveCount(0);
    await expect(button(page, 'Resolve')).toHaveCount(0);
    await logout(page);

    await loginAs(page, 'ananya@icc.com');
    await open(page);
    await expect(page.getByText('This incident is closed.')).toBeVisible();
    await expect(button(page, 'Add')).toHaveCount(0);
    await expect(button(page, 'Reopen')).toHaveCount(0);
    // The history is kept: the reopen reason and the resolutions are still readable.
    await expect(page.getByText('Reopened: It dropped again this morning.')).toBeVisible();
  });
});

// ------------------------------------------------------------------------------------------------ story 2

test.describe('story 2: an incident that is cancelled', () => {
  test('the engineer cancels an incident, and a cancelled incident is final', async ({ page }) => {
    await report(page, `[e2e-pw] ${tag} duplicate report`);
    await loginAs(page, 'sai@icc.com');
    await open(page);
    await button(page, 'Cancel incident').click();
    await page.getByRole('dialog').getByRole('button', { name: 'Cancel the incident' }).click();
    await expect(page.getByText(`${number} was cancelled.`)).toBeVisible();
    await expect(chip(page, 'Cancelled')).toBeVisible();
    await expect(page.getByText('takes no new comments')).toBeVisible();
    for (const name of ['Accept', 'Resolve', 'Close', 'Reopen', 'Cancel incident', 'Add']) {
      await expect(button(page, name)).toHaveCount(0);
    }
  });
});
