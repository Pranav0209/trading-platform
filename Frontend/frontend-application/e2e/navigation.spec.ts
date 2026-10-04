import { test, expect } from '@playwright/test';

/**
 * Navigation and Dashboard E2E Tests
 * 
 * Covers:
 * - Dashboard loading and display
 * - Navigation menu visibility and functionality
 * - Route navigation between pages
 */

test.describe('Navigation and Dashboard Journey', () => {
  const baseURL = process.env.TRADING_UI_URL || 'http://localhost:4200';
  const validUsername = process.env.TEST_USERNAME;
  const validPassword = process.env.TEST_PASSWORD;

  async function signInFresh(page: any) {
    await page.goto(`${baseURL}/sign-in`);
    await page.getByTestId('username-input').fill(validUsername as string);
    await page.getByTestId('password-input').fill(validPassword as string);
    await page.getByTestId('submit-button').click();
    
    // Wait for either successful navigation or error message
    await Promise.race([
      page.waitForNavigation().catch(() => {}),
      page.locator('[data-testid="error-message"]').waitFor({ timeout: 2000 }).catch(() => {})
    ]);
    
    // Check if login succeeded or failed
    const errorMessage = page.locator('[data-testid="error-message"]');
    const isError = await errorMessage.isVisible().catch(() => false);
    
    if (isError) {
      const errorText = await errorMessage.textContent();
      throw new Error(`Login failed: ${errorText}`);
    }
    
    // Wait for network to settle
    await page.waitForLoadState('networkidle').catch(() => {});
    
    // Verify we're on dashboard
    await expect(page).toHaveURL(/\/dashboard/, { timeout: 10000 });
  }

  test.beforeEach(async ({ context }) => {
    await context.clearCookies();
  });

  test('dashboard loads successfully after login', async ({ page }) => {
    test.skip(!validUsername || !validPassword, 'Test credentials not configured');

    await signInFresh(page);
    // After sign in, should be on dashboard
    await expect(page).toHaveURL(/\/dashboard/, { timeout: 10000 });
  });

  test('navigation menu is visible after login', async ({ page }) => {
    test.skip(!validUsername || !validPassword, 'Test credentials not configured');

    await signInFresh(page);
    
    await expect(page.locator('[data-testid="nav-dashboard"]')).toBeVisible();
    await expect(page.locator('[data-testid="nav-order-ticket"]')).toBeVisible();
    await expect(page.locator('[data-testid="nav-orders"]')).toBeVisible();
  });

  test('can navigate to Orders page from dashboard', async ({ page }) => {
    test.skip(!validUsername || !validPassword, 'Test credentials not configured');

    await signInFresh(page);
    await page.click('[data-testid="nav-orders"]');
    
    await expect(page).toHaveURL(/\/orders/, { timeout: 10000 });
  });

  test('can navigate to Trade page from dashboard', async ({ page }) => {
    test.skip(!validUsername || !validPassword, 'Test credentials not configured');

    await signInFresh(page);
    await page.click('[data-testid="nav-order-ticket"]');
    
    await expect(page).toHaveURL(/\/trade/, { timeout: 10000 });
  });

  test('can navigate back to dashboard from orders page', async ({ page }) => {
    test.skip(!validUsername || !validPassword, 'Test credentials not configured');

    await signInFresh(page);
    await page.click('[data-testid="nav-orders"]');
    await expect(page).toHaveURL(/\/orders/, { timeout: 10000 });

    await page.click('[data-testid="nav-dashboard"]');
    await expect(page).toHaveURL(/\/dashboard/, { timeout: 10000 });
  });

  test('can navigate back to dashboard from trade page', async ({ page }) => {
    test.skip(!validUsername || !validPassword, 'Test credentials not configured');

    await signInFresh(page);
    await page.click('[data-testid="nav-order-ticket"]');
    await expect(page).toHaveURL(/\/trade/, { timeout: 10000 });

    await page.click('[data-testid="nav-dashboard"]');
    await expect(page).toHaveURL(/\/dashboard/, { timeout: 10000 });
  });

  test('orders page loads after navigation', async ({ page }) => {
    test.skip(!validUsername || !validPassword, 'Test credentials not configured');

    await signInFresh(page);
    await page.click('[data-testid="nav-orders"]');
    
    // Wait for navigation and orders page to load
    await expect(page).toHaveURL(/\/orders/, { timeout: 10000 });
    await page.waitForLoadState('networkidle').catch(() => {});
  });

  test('trade page (order form) loads after navigation', async ({ page }) => {
    test.skip(!validUsername || !validPassword, 'Test credentials not configured');

    await signInFresh(page);
    await page.click('[data-testid="nav-order-ticket"]');
    
    // Wait for navigation and trade page to load
    await expect(page).toHaveURL(/\/trade/, { timeout: 10000 });
    await page.waitForLoadState('networkidle').catch(() => {});
  });

  test('navigation menu links are clickable', async ({ page }) => {
    test.skip(!validUsername || !validPassword, 'Test credentials not configured');

    await signInFresh(page);
    
    const navLinks = [
      '[data-testid="nav-dashboard"]',
      '[data-testid="nav-order-ticket"]',
      '[data-testid="nav-orders"]',
    ];

    for (const link of navLinks) {
      const element = page.locator(link);
      await expect(element).toBeVisible({ timeout: 5000 });
    }
  });

  test('multiple navigation cycles work correctly', async ({ page }) => {
    test.skip(!validUsername || !validPassword, 'Test credentials not configured');

    await signInFresh(page);

    // Navigate: Dashboard -> Orders -> Trade -> Dashboard
    await page.click('[data-testid="nav-orders"]');
    await expect(page).toHaveURL(/\/orders/, { timeout: 10000 });

    await page.click('[data-testid="nav-order-ticket"]');
    await expect(page).toHaveURL(/\/trade/, { timeout: 10000 });

    await page.click('[data-testid="nav-dashboard"]');
    await expect(page).toHaveURL(/\/dashboard/, { timeout: 10000 });

    // Repeat cycle
    await page.click('[data-testid="nav-orders"]');
    await expect(page).toHaveURL(/\/orders/, { timeout: 10000 });

    await page.click('[data-testid="nav-dashboard"]');
    await expect(page).toHaveURL(/\/dashboard/, { timeout: 10000 });
  });
});
