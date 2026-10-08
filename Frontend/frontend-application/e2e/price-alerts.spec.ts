import { test, expect } from '@playwright/test';

test.describe('Price Alerts E2E Journey', () => {
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

  test('can navigate to alerts and create a new alert', async ({ page }) => {
    test.skip(!validUsername || !validPassword, 'Test credentials not configured');

    await signInFresh(page);
    await page.goto(`${baseURL}/alerts`);
    await expect(page).toHaveURL(/\/alerts/);
    await page.waitForLoadState('networkidle');

    // Ensure we are in the alerts page
    await expect(page.locator('h2:has-text("Price Alerts")')).toBeVisible();

    // Fill in the new alert form
    await page.fill('input[placeholder="e.g., AAPL, MSFT"]', 'AAPL');
    await page.selectOption('select', 'BELOW');
    await page.fill('input[placeholder="e.g., 150.00"]', '100.00');

    // Submit alert
    await page.click('button:has-text("Create Alert")');

    // Verify the alert shows up in the list
    // Verify the alert shows up in the list
    await expect(page.locator('.alert-card h5:has-text("AAPL")').first()).toBeVisible();
    await expect(page.locator('.alert-card .alert-description:has-text("100.00")').first()).toBeVisible();
  });
});
