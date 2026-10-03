import { test, expect } from '@playwright/test';

/**
 * Sign-in journey e2e tests
 * 
 * Covers:
 * - Guard redirect: attempting to access protected route without auth
 * - Refused sign-in: invalid credentials
 * - Successful sign-in: valid credentials leading to dashboard
 * 
 * Each test is isolated: no shared state, tokens, or side effects.
 */

test.describe('Sign-in Journey', () => {
  const baseURL = process.env.TRADING_UI_URL || 'http://localhost:4200';
  const validUsername = process.env.TEST_USERNAME;
  const validPassword = process.env.TEST_PASSWORD;

  test.beforeEach(async ({ context }) => {
    // Clear all cookies and storage to ensure isolation
    await context.clearCookies();
  });

  test('guard redirects unauthenticated user to sign-in when accessing dashboard', async ({
    page,
  }) => {
    // Attempt to access protected route directly
    await page.goto(`${baseURL}/dashboard`);

    // Should be redirected to sign-in
    await expect(page).toHaveURL(/\/sign-in/);
    await expect(page.locator('[data-testid="sign-in-form"]')).toBeVisible();
  });

  test('refused sign-in shows error for invalid credentials', async ({ page }) => {
    await page.goto(`${baseURL}/sign-in`);

    // Enter invalid credentials
    await page.fill('input[name="username"]', 'nonexistent-user');
    await page.fill('input[name="password"]', 'wrong-password');
    await page.click('[data-testid="submit-button"]');

    // Should show error message
    const errorMessage = page.locator('[data-testid="error-message"]');
    await expect(errorMessage).toBeVisible();
    await expect(errorMessage).toContainText(/expired|failed|sign in/i);
  });

  test('successful sign-in with valid credentials redirects to dashboard', async ({
    page,
  }) => {
    // Skip if test credentials not provided
    test.skip(!validUsername || !validPassword, 'Test credentials not configured');

    await page.goto(`${baseURL}/sign-in`);

    // Enter valid credentials
    await page.fill('input[name="username"]', validUsername);
    await page.fill('input[name="password"]', validPassword);
    await page.click('[data-testid="submit-button"]');

    // Should navigate to dashboard
    await expect(page).toHaveURL(/\/dashboard/);

    // Dashboard should be visible
    await expect(page.locator('[data-testid="dashboard"]')).toBeVisible();
  });

  test('successful sign-in returns to requested return-to parameter', async ({ page }) => {
    test.skip(!validUsername || !validPassword, 'Test credentials not configured');

    // Navigate with returnTo parameter
    await page.goto(`${baseURL}/sign-in?returnTo=/dashboard`);

    // Enter valid credentials
    await page.fill('input[name="username"]', validUsername);
    await page.fill('input[name="password"]', validPassword);
    await page.click('[data-testid="submit-button"]');

    // Should navigate to the requested path
    await expect(page).toHaveURL(/\/dashboard/);
    await expect(page.locator('[data-testid="dashboard"]')).toBeVisible();
  });
});
