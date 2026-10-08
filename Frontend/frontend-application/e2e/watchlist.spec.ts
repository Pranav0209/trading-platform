import { test, expect } from '@playwright/test';

test.describe('Watchlist E2E Journey', () => {
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
    
    const currentUrl = page.url();
    if (!currentUrl.includes('/dashboard')) {
      throw new Error(`Login failed: Still on ${currentUrl}`);
    }
  }

  test.beforeEach(async ({ context }) => {
    await context.clearCookies();
  });

  test('can navigate to watchlist and create a new list', async ({ page }) => {
    test.skip(!validUsername || !validPassword, 'Test credentials not configured');

    await signInFresh(page);
    await page.goto(`${baseURL}/watchlist`);
    await expect(page).toHaveURL(/\/watchlist/);

    // Wait for the empty state or loaded watchlists
    await page.waitForLoadState('networkidle');

    // Attempt to create a new watchlist
    const createBtn = page.locator('button.create-watchlist, button:has-text("+ Create Watchlist")').first();
    if (await createBtn.isVisible()) {
      await createBtn.click();
      
      const input = page.locator('input[placeholder="New list..."], input.tab-input').first();
      await expect(input).toBeVisible();
      
      await input.fill('My E2E Watchlist');
      await input.press('Enter');

      // Verify the new watchlist tab is visible
      await expect(page.locator('button.watchlist-tab:has-text("My E2E Watchlist")')).toBeVisible();
    }
  });

  test('can search and add instrument to watchlist', async ({ page }) => {
    test.skip(!validUsername || !validPassword, 'Test credentials not configured');

    await signInFresh(page);
    await page.goto(`${baseURL}/watchlist`);
    await expect(page).toHaveURL(/\/watchlist/);
    await page.waitForLoadState('networkidle');

    // Create a new watchlist if none exist to make sure we have a clean slate
    const createBtnEmpty = page.locator('button:has-text("+ Create Watchlist")').first();
    if (await createBtnEmpty.isVisible()) {
      await createBtnEmpty.click();
      const input = page.locator('input.tab-input').first();
      await input.fill('Search Test List');
      await input.press('Enter');
    }

    // Attempt to search and add an instrument
    const searchBtn = page.locator('button:has-text("+ Add Instrument")').first();
    if (await searchBtn.isVisible()) {
      await searchBtn.click();
    }

    const searchInput = page.locator('input[placeholder="Search instruments..."]');
    await searchInput.fill('AAPL');
    
    // Select from dropdown
    const dropdownItem = page.locator('.autocomplete-dropdown li:has-text("AAPL")').first();
    await expect(dropdownItem).toBeVisible();
    await dropdownItem.click();

    // Verify instrument was added (shows up in row)
    await expect(page.locator('.watchlist-row .symbol:has-text("AAPL")')).toBeVisible();
  });
});
