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
    await page.goto(`${baseURL}/dashboard`);

    // Should be redirected to sign-in
    await expect(page).toHaveURL(/\/sign-in/);
    await expect(page.locator('[data-testid="sign-in-form"]')).toBeVisible();
  });

  test('refused sign-in shows error for invalid credentials', async ({ page }) => {
    await page.goto(`${baseURL}/sign-in`);

    // Enter invalid credentials
    await page.getByTestId('username-input').fill('nonexistent-user');
    await page.getByTestId('password-input').fill('wrong-password');
    await page.getByTestId('submit-button').click();

    // Should show error message (either invalid credentials or account locked)
    const errorMessage = page.locator('[data-testid="error-message"]');
    await expect(errorMessage).toBeVisible();
    await expect(errorMessage).toContainText(/Invalid username or password|Too many login attempts|temporarily locked/i);
  });

  test('successful sign-in with valid credentials redirects to dashboard', async ({
    page,
  }) => {
    // Skip if test credentials not provided
    test.skip(!validUsername || !validPassword, 'Test credentials not configured');

    await page.goto(`${baseURL}/sign-in`);

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
    await page.goto(`${baseURL}/sign-in?returnTo=/dashboard`);

    // Enter valid credentials
    await page.getByTestId('username-input').fill(validUsername as string);
    await page.getByTestId('password-input').fill(validPassword as string);
    await page.getByTestId('submit-button').click();

    // Should navigate to the requested path
    await expect(page).toHaveURL(/\/dashboard/);
  });

  test('successful admin sign-in redirects to admin dashboard', async ({ page }) => {
    test.skip(!adminUsername || !validPassword, 'Admin test credentials not configured');

    await page.goto(`${baseURL}/sign-in`);

    // Enter admin credentials
    await page.getByTestId('username-input').fill(adminUsername as string);
    await page.getByTestId('password-input').fill(validPassword as string);
    await page.getByTestId('submit-button').click();

    // Should navigate to admin
    await expect(page).toHaveURL(/\/admin/);
  });

  test('account lockout after 5 failed login attempts within 15 minutes', async ({ page, context }) => {
    // Use a unique test username to avoid interference with other tests
    const testUsername = `lockout-test-${Date.now()}`;
    const wrongPassword = 'wrong-password-123';

    // Attempt 5 failed logins
    for (let i = 1; i <= 5; i++) {
      await page.goto(`${baseURL}/sign-in`);
      await page.getByTestId('username-input').fill(testUsername);
      await page.getByTestId('password-input').fill(wrongPassword);
      await page.getByTestId('submit-button').click();

      // Each attempt should show an error
      const errorMessage = page.locator('[data-testid="error-message"]');
      await expect(errorMessage).toBeVisible({ timeout: 5000 });

      // After 5 attempts, should show "Invalid username or password"
      if (i < 5) {
        await expect(errorMessage).toContainText(/Invalid username or password/i);
      }

      // Clear cookies for next attempt to simulate fresh session
      await context.clearCookies();
    }

    // 6th attempt should be locked out
    await page.goto(`${baseURL}/sign-in`);
    await page.getByTestId('username-input').fill(testUsername);
    await page.getByTestId('password-input').fill(wrongPassword);
    await page.getByTestId('submit-button').click();

    // Should show account locked message
    const errorMessage = page.locator('[data-testid="error-message"]');
    await expect(errorMessage).toBeVisible({ timeout: 5000 });
    await expect(errorMessage).toContainText(/Too many login attempts|temporarily locked/i);
  });

  test('sign-in form displays all required fields', async ({ page }) => {
    await page.goto(`${baseURL}/sign-in`);
    
    await expect(page.getByTestId('username-input')).toBeVisible();
    await expect(page.getByTestId('password-input')).toBeVisible();
    await expect(page.getByTestId('submit-button')).toBeVisible();
    await expect(page.locator('[data-testid="sign-in-form"]')).toBeVisible();
  });

  test('password field is masked (type="password")', async ({ page }) => {
    await page.goto(`${baseURL}/sign-in`);
    
    const passwordInput = page.getByTestId('password-input');
    await expect(passwordInput).toHaveAttribute('type', 'password');
  });

  test('invalid username alone shows error', async ({ page }) => {
    await page.goto(`${baseURL}/sign-in`);
    
    await page.getByTestId('username-input').fill('nonexistent-user-xyz');
    await page.getByTestId('password-input').fill('password123');
    await page.getByTestId('submit-button').click();
    
    const errorMessage = page.locator('[data-testid="error-message"]');
    await expect(errorMessage).toBeVisible({ timeout: 5000 });
    await expect(errorMessage).toContainText(/Invalid|error|failed|Too many/i);
  });

  test('invalid password alone shows error', async ({ page }) => {
    await page.goto(`${baseURL}/sign-in`);
    
    await page.getByTestId('username-input').fill(validUsername as string);
    await page.getByTestId('password-input').fill('wrong-password-12345');
    await page.getByTestId('submit-button').click();
    
    const errorMessage = page.locator('[data-testid="error-message"]');
    await expect(errorMessage).toBeVisible({ timeout: 5000 });
    await expect(errorMessage).toContainText(/Invalid|error|failed/i);
  });
});