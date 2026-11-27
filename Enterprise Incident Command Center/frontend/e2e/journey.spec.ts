import { expect, test, type Page } from '@playwright/test';
import { choose, loginAs, logout, tag } from './helpers';

/**
 * The demo story from DEMO-SCRIPT.md, told through the real screens, one person after another:
 * an employee reports a network outage, a network engineer takes it, escalates it to a major incident and runs it,
 * then an administrator takes the problem through its six steps (assess, confirm, fix, resolve, complete). At the end the dashboard shows the numbers.
 *
 * The tests run in order and share one incident. Everything they create starts with "[e2e-pw]".
 */
test.describe.configure({ mode: 'serial' });

const title = `[e2e-pw] ${tag} core router down`;
let incidentNumber = '';
let incidentUrl = '';
let majorUrl = '';
let problemUrl = '';

async function openIncident(page: Page) {
  await page.goto(incidentUrl);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText(incidentNumber);
}

test('an employee reports a priority 1 network incident', async ({ page }) => {
  await loginAs(page, 'ananya@icc.com');
  await page.getByRole('link', { name: 'Report an incident' }).first().click();

  // The form explains itself: an empty submit shows the errors and sends nothing.
  await page.getByRole('button', { name: 'Report incident' }).click();
  await expect(page.getByText('Enter a short description.')).toBeVisible();
  await expect(page.getByText('Choose a category.')).toBeVisible();

  await page.getByLabel('Short description').fill(title);
  await page.getByLabel(/^Description/).fill('Building B has no network. Created by the Playwright journey.');
  await choose(page, 'Category', 'Network');
  await choose(page, 'Impact', /1 - High/);
  await choose(page, 'Urgency', /1 - High/);
  await page.getByRole('button', { name: 'Report incident' }).click();

  await expect(page.getByText('Your incident was reported')).toBeVisible();
  const heading = page.getByRole('heading', { level: 1 });
  await expect(heading).toHaveText(/^INC\d+$/);
  incidentNumber = (await heading.textContent()) ?? '';
  incidentUrl = new URL(page.url()).pathname;
  // The server routed it to the Network team and the priority is 1.
  await expect(page.getByText('P1 Critical')).toBeVisible();
  await expect(page.getByText('Team', { exact: true }).locator('xpath=following-sibling::*[1]')).toHaveText('Network');
  await logout(page);
});

test('a network engineer accepts it, comments and escalates it to a major incident', async ({ page }) => {
  await loginAs(page, 'sai@icc.com');
  await page.getByRole('link', { name: 'Incident queue' }).click();
  await page.getByLabel('Search number or text').fill(incidentNumber);
  await page.getByRole('link', { name: incidentNumber }).click();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText(incidentNumber);

  await page.getByRole('button', { name: 'Accept' }).click();
  await expect(page.getByText(`You accepted ${incidentNumber}.`)).toBeVisible();
  await expect(page.locator('.MuiChip-root', { hasText: 'In progress' })).toBeVisible();

  await choose(page, 'Type', /Work note/);
  await page.getByLabel('Add a comment').fill('Checking the core switch uplinks.');
  await page.getByRole('button', { name: 'Add', exact: true }).click();
  await expect(page.getByText('Checking the core switch uplinks.')).toBeVisible();
  await expect(page.locator('.MuiChip-root', { hasText: 'Work note (internal)' })).toBeVisible();

  await page.getByRole('button', { name: 'Escalate to major incident' }).click();
  const dialog = page.getByRole('dialog');
  // Declare waits for the impact text.
  await expect(dialog.getByRole('button', { name: 'Promote to major incident' })).toBeDisabled();
  await dialog.getByLabel('Business impact').fill('80 people in building B have no network.');
  await choose(page, 'Commander', 'Sachin Suryavanshi');
  await dialog.getByRole('button', { name: 'Promote to major incident' }).click();

  await expect(page.getByRole('heading', { level: 1 })).toHaveText(/^Major incident #\d+$/);
  majorUrl = new URL(page.url()).pathname;
  await expect(page.locator('.MuiChip-root', { hasText: 'Declared' })).toBeVisible();
  await expect(page.getByText(`Major incident declared for ${incidentNumber}`)).toBeVisible();
  await logout(page);
});

test('the major incident is run through its timeline and status steps', async ({ page }) => {
  await loginAs(page, 'sai@icc.com');
  await page.goto(majorUrl);
  await page.getByLabel('Add an update').fill('Spare router on site.');
  await page.getByRole('button', { name: 'Post update' }).click();
  await expect(page.getByText('Spare router on site.')).toBeVisible();

  // Only the next step is offered.
  await expect(page.getByRole('button', { name: 'Move to Resolved' })).toHaveCount(0);
  await page.getByRole('button', { name: 'Move to Mitigating' }).click();
  await expect(page.getByText('Status changed from DECLARED to MITIGATING')).toBeVisible();

  // The incident itself is resolved, then the major incident.
  await openIncident(page);
  await page.getByRole('button', { name: 'Resolve', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await choose(page, 'Resolution code', 'Solution provided');
  await dialog.getByLabel('Resolution notes').fill('Router replaced.');
  await dialog.getByRole('button', { name: 'Resolve', exact: true }).click();
  await expect(page.getByText('Resolution: Solution provided')).toBeVisible();

  await page.goto(majorUrl);
  await page.getByRole('button', { name: 'Move to Resolved' }).click();
  await expect(page.getByText('Status changed from MITIGATING to RESOLVED')).toBeVisible();

  // From the resolved major incident, straight to a problem with everything chosen.
  await page.getByRole('link', { name: 'Create a problem for this major incident' }).click();
  await page.getByLabel('Title').fill(`[e2e-pw] ${tag} router failure`);
  await page.getByLabel(/^Description/).fill('Why did the core router fail?');
  await page.getByRole('button', { name: 'Create problem' }).click();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText(/^PRB\d+$/);
  problemUrl = new URL(page.url()).pathname;
  await expect(page.getByText('The problem was created.')).toBeVisible();
  await logout(page);
});

test('the administrator takes the problem through assess, confirm, fix, resolve and complete', async ({ page }) => {
  await loginAs(page, 'mohit@icc.com');
  await page.goto(problemUrl);

  // New: only Assess. It needs an assignee.
  await expect(page.getByRole('button', { name: 'Confirm' })).toHaveCount(0);
  await page.getByRole('button', { name: 'Assess', exact: true }).click();
  const assess = page.getByRole('dialog');
  await expect(assess.getByRole('button', { name: 'Assess', exact: true })).toBeDisabled();
  await choose(page, 'Assigned to', 'Sai Vivek');
  await assess.getByRole('button', { name: 'Assess', exact: true }).click();
  await expect(page.getByText('is being assessed.')).toBeVisible();

  await page.getByRole('button', { name: 'Confirm', exact: true }).click();
  await expect(page.getByText('The problem is now in root cause analysis.')).toBeVisible();

  // A corrective action does not block anything: it is closed with the problem.
  await page.getByRole('tab', { name: /Corrective actions/ }).click();
  await page.getByLabel('What must be done').fill('Replace the fans in all core routers.');
  await choose(page, 'Owner', 'Sai Vivek');
  await page.getByLabel('Due date').fill('2030-01-31');
  await page.getByRole('button', { name: 'Add action' }).click();
  await expect(page.getByText('The corrective action was added.')).toBeVisible();

  // Fix needs both notes.
  await page.getByRole('button', { name: 'Fix', exact: true }).click();
  const fix = page.getByRole('dialog');
  await expect(fix.getByRole('button', { name: 'Fix', exact: true })).toBeDisabled();
  await fix.getByLabel('Cause notes').fill('The line card overheated because a fan failed.');
  await fix.getByLabel('Fix notes').fill('Replace the fans.');
  await fix.getByRole('button', { name: 'Fix', exact: true }).click();
  await expect(page.getByText('is in progress.')).toBeVisible();

  await page.getByRole('button', { name: 'Resolve', exact: true }).click();
  await expect(page.getByText('The problem was resolved.')).toBeVisible();
  await page.getByRole('button', { name: 'Complete', exact: true }).click();
  await expect(page.getByText('The problem was closed.')).toBeVisible();
  await page.getByRole('tab', { name: /Corrective actions/ }).click();
  await expect(page.getByText('Done', { exact: true })).toBeVisible();
});

test('the dashboard and the audit trail show what just happened', async ({ page }) => {
  await loginAs(page, 'mohit@icc.com');
  await page.getByRole('link', { name: 'Dashboard' }).click();
  await expect(page.getByRole('heading', { level: 1, name: 'Dashboard' })).toBeVisible();
  for (const card of ['Incidents', 'Major incidents', 'Problems and RCA']) {
    await expect(page.getByRole('heading', { level: 2, name: card, exact: true })).toBeVisible();
  }
  await expect(page.getByText('Unavailable:')).toHaveCount(0);
  await expect(page.getByRole('img', { name: /per day over the last 7 days/ })).toBeVisible();
  await page.getByRole('button', { name: '30 days' }).click();
  await expect(page.getByRole('img', { name: /last 30 days/ })).toBeVisible();

  await page.getByRole('link', { name: 'Audit trails' }).click();
  await expect(page.getByRole('heading', { level: 1, name: 'Audit trails' })).toBeVisible();
  await expect(page.getByRole('row', { name: new RegExp('INCIDENT_CREATED') }).first()).toBeVisible();
  await page.getByRole('tab', { name: 'Problems' }).click();
  await expect(page.getByRole('row', { name: /PROBLEM_/ }).first()).toBeVisible();
});
