import { test, expect } from '@playwright/test';

test.describe('Smart Rules E2E Journey', () => {
  const baseURL = process.env.TRADING_UI_URL || 'http://localhost:4200';
  const validUsername = process.env.TEST_USERNAME;
  const validPassword = process.env.TEST_PASSWORD;

  async function signInFresh(page: any) {
    await page.goto(`${baseURL}/sign-in`);
    await page.getByTestId('username-input').fill(validUsername as string);
    await page.getByTestId('password-input').fill(validPassword as string);
    await page.getByTestId('submit-button').click();
    
    await Promise.race([
      page.waitForURL(/\/dashboard/),
      page.waitForTimeout(5000)
    ]).catch(() => {});
  }

  test.beforeEach(async ({ context }) => {
    await context.clearCookies();
  });

  test('can navigate to smart rules and create a new rule', async ({ page }) => {
    test.skip(!validUsername || !validPassword, 'Test credentials not configured');

    await signInFresh(page);
    await page.goto(`${baseURL}/smart-rules`);
    await expect(page).toHaveURL(/\/smart-rules/);
    await page.waitForLoadState('networkidle');

    // Ensure we are in the Smart Rules page
    await expect(page.locator('h1:has-text("Smart Rules")')).toBeVisible();

    // The default draft rule should be visible in the builder panel
    await expect(page.locator('.rule-badge:has-text("Outer rule")')).toBeVisible();

    // Fill in the rule details
    await page.fill('input[placeholder="INFY.NS"]', 'INFY.NS');
    
    // Select option from autocomplete if present
    const dropdown = page.locator('.autocomplete-dropdown li:has-text("INFY.NS")').first();
    if (await dropdown.isVisible({ timeout: 2000 }).catch(() => false)) {
      await dropdown.click();
    }

    // Wait a brief moment
    await page.waitForTimeout(500);

    // Fill target value so validation passes
    await page.locator('label:has-text("Target value") input').fill('100');

    // Save the rule
    await page.click('button:has-text("Save rule")');

    // Verify it appears in the saved rules list
    await expect(page.locator('.saved-rule-card')).toBeVisible();
    await expect(page.locator('.saved-rule-card h3')).toContainText('INFY.NS');
  });
});
