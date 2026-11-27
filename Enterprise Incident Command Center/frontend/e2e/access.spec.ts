import { expect, test } from '@playwright/test';
import { PASSWORD, loginAs } from './helpers';

/** Keyboard and label basics: a person without a mouse, or with a screen reader, can use the main screens. */

test('the login form works with the keyboard only', async ({ page }) => {
  await page.goto('/login');
  await expect(page.getByLabel('Email')).toBeFocused(); // autofocus
  await page.keyboard.type('ananya@icc.com');
  await page.keyboard.press('Tab');
  await page.keyboard.type(PASSWORD);
  await page.keyboard.press('Enter');
  await expect(page.getByTestId('who')).toHaveText('Ananya Iyer');
});

test('a wrong password shows a message and no page', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel('Email').fill('ananya@icc.com');
  await page.getByLabel('Password').fill('not-the-password');
  await page.getByRole('button', { name: 'Log in' }).click();
  await expect(page.getByRole('alert')).toBeVisible();
  await expect(page).toHaveURL(/\/login$/);
});

test('every menu page of the administrator has one main heading and no unnamed controls', async ({ page }) => {
  await loginAs(page, 'mohit@icc.com');
  const menu = page.getByRole('navigation', { name: 'Main menu' });
  const entries = await menu.getByRole('link').allTextContents();
  expect(entries.length).toBeGreaterThanOrEqual(7);

  for (const name of entries) {
    await menu.getByRole('link', { name: name.trim(), exact: true }).click();
    await expect(page.getByRole('heading', { level: 1 })).toHaveCount(1);
    // Buttons and form fields need a name a screen reader can read.
    const unnamed = await page.evaluate(() =>
      [...document.querySelectorAll('button, input, [role=combobox], [role=tab]')]
        .filter((el) => !(el as HTMLElement).hidden && (el as HTMLInputElement).type !== 'hidden' && el.getAttribute('aria-hidden') !== 'true')
        .filter((el) => {
          const label = el.getAttribute('aria-label') || el.getAttribute('aria-labelledby') || (el as HTMLElement).innerText?.trim() || (el as HTMLInputElement).labels?.length || el.getAttribute('placeholder');
          return !label;
        })
        .map((el) => el.outerHTML.slice(0, 120)),
    );
    expect(unnamed, `unnamed controls on ${name}`).toEqual([]);
  }
});

test('the menu can be reached and used with the keyboard', async ({ page }) => {
  await loginAs(page, 'ananya@icc.com');
  await page.getByRole('link', { name: 'My incidents' }).focus();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('heading', { level: 1, name: 'My incidents' })).toBeVisible();
});

test('a role cannot open a page of another role', async ({ page }) => {
  await loginAs(page, 'sai@icc.com');
  await page.goto('/admin/audit');
  await expect(page.getByText('Not available for your role')).toBeVisible();
});

test('the layout holds on a phone: no sideways scrolling and the menu opens', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 800 });
  await loginAs(page, 'mohit@icc.com');
  await page.getByRole('button', { name: 'Open the menu' }).click();
  await page.getByRole('link', { name: 'All incidents' }).click();
  await expect(page.getByRole('heading', { level: 1, name: 'All incidents' })).toBeVisible();
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  expect(overflow).toBeLessThanOrEqual(1);
});
