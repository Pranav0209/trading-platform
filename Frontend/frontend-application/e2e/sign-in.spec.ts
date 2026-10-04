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
  const adminUsername = process.env.TEST_ADMIN_USERNAME;
  const validPassword = process.env.TEST_PASSWORD;

  test.beforeEach(async ({ context }) => {
    // Clear all cookies and storage to ensure isolation
    await context.clearCookies();
  });

  test('guard redirects unauthenticated user to sign-in when accessing dashboard', async ({
    page,
  }) => {
    // Attempt to access protected route directly
    await page.goto(${baseURL}/dashboard);

    // Should be redirected to sign-in
    await expect(page).toHaveURL(/\/sign-in/);
    await expect(page.locator('[data-testid="sign-in-form"]')).toBeVisible();
  });

  test('refused sign-in shows error for invalid credentials', async ({ page }) => {
    await page.goto(${baseURL}/sign-in);

    // Enter invalid credentials
    await page.getByTestId('username-input').fill('nonexistent-user');
    await page.getByTestId('password-input').fill('wrong-password');
    await page.getByTestId('submit-button').click();

    // Should show error message
    const errorMessage = page.locator('[data-testid="error-message"]');
    await expect(errorMessage).toBeVisible();
    await expect(errorMessage).toContainText(/Invalid username or password/i);
  });

  test('successful sign-in with valid credentials redirects to dashboard', async ({
    page,
  }) => {
    // Skip if test credentials not provided
    test.skip(!validUsername || !validPassword, 'Test credentials not configured');

    await page.goto(${baseURL}/sign-in);

    // Enter valid credentials
    await page.getByTestId('username-input').fill(validUsername as string);
    await page.getByTestId('password-input').fill(validPassword as string);
    await page.getByTestId('submit-button').click();

    // Should navigate to dashboard
    await expect(page).toHaveURL(/\/dashboard/);
  });

  test('successful sign-in returns to requested return-to parameter', async ({ page }) => {
    test.skip(!validUsername || !validPassword, 'Test credentials not configured');

    // Navigate with returnTo parameter
    await page.goto(${baseURL}/sign-in?returnTo=/dashboard);

    // Enter valid credentials
    await page.getByTestId('username-input').fill(validUsername as string);
    await page.getByTestId('password-input').fill(validPassword as string);
    await page.getByTestId('submit-button').click();

    // Should navigate to the requested path
    await expect(page).toHaveURL(/\/dashboard/);
  });

  test('successful admin sign-in redirects to admin dashboard', async ({ page }) => {
    test.skip(!adminUsername || !validPassword, 'Admin test credentials not configured');

    await page.goto(${baseURL}/sign-in);

    // Enter admin credentials
    await page.getByTestId('username-input').fill(adminUsername as string);
    await page.getByTestId('password-input').fill(validPassword as string);
    await page.getByTestId('submit-button').click();

    // Should navigate to admin
    await expect(page).toHaveURL(/\/admin/);
  });
});