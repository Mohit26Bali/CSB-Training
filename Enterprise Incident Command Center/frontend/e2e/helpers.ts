import { expect, type Page } from '@playwright/test';

export const PASSWORD = 'Icc@Demo2026';

/** Logs in through the login form, like a person would. */
export async function loginAs(page: Page, username: string) {
  await page.goto('/login');
  await page.getByLabel('Email').fill(username);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: 'Log in' }).click();
  await expect(page.getByRole('heading', { level: 1, name: /^Welcome/ })).toBeVisible();
}

export async function logout(page: Page) {
  await page.getByRole('button', { name: 'Log out' }).click();
  await expect(page.getByRole('heading', { name: 'Incident Command Center' })).toBeVisible();
}

/** Picks an option in a MUI select by its label. */
export async function choose(page: Page, label: string, option: string | RegExp) {
  await page.getByRole('combobox', { name: label }).click();
  // A text must match the whole option name: "Solution provided" is also inside "No resolution provided".
  await page.getByRole('option', { name: option, exact: typeof option === 'string' }).first().click();
}

/** A unique tag, so a run finds only its own records. */
export const tag = 'PW' + Date.now().toString(36).toUpperCase();
