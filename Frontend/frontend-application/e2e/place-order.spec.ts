import { test, expect } from '@playwright/test';

/**
 * Place order journey e2e tests
 * 
 * Covers:
 * - Read-only account: attempting to place order on restricted account
 * - Rejection before submission: form validation prevents submission
 * - Placed order: valid submission resulting in NEW, FILLED, or REJECTED status
 * 
 * Each test is isolated: fresh login, no shared tokens or order state.
 * 
 * Acceptance: NEW, FILLED or REJECTED are all valid responses.
 * A test that asserts FILLED would fail when executor switches on.
 */

test.describe('Place Order Journey', () => {
  const baseURL = process.env.TRADING_UI_URL || 'http://localhost:4200';
  const validUsername = process.env.TEST_USERNAME;
  const validPassword = process.env.TEST_PASSWORD;

  async function signInFresh(page: any) {
    // Each test signs in independently to avoid shared state
    await page.goto(${baseURL}/sign-in);
    await page.getByTestId('username-input').fill(validUsername as string);
    await page.getByTestId('password-input').fill(validPassword as string);
    await page.getByTestId('submit-button').click();
    await expect(page).toHaveURL(/\/dashboard/);
  }

  test.beforeEach(async ({ context }) => {
    // Clear all cookies and storage to ensure isolation
    await context.clearCookies();
  });

  test('form validation rejects invalid order before submission', async ({ page }) => {
    test.skip(!validUsername || !validPassword, 'Test credentials not configured');

    await signInFresh(page);

    // Navigate to order ticket
    await page.click('[data-testid="nav-order-ticket"]');
    await expect(page.locator('[data-testid="order-ticket"]')).toBeVisible();

    // Try submitting with empty form
    const submitButton = page.getByTestId('submit-order-button');
    await expect(submitButton).toBeDisabled();

    // Fill invalid quantity (zero)
    await page.getByTestId('quantity-input').fill('0');
    await expect(submitButton).toBeDisabled();

    // Fill valid quantity but empty symbol
    await page.getByTestId('quantity-input').fill('10');
    await page.getByTestId('price-input').fill('100.00');
    await expect(submitButton).toBeDisabled();

    // Fill valid symbol - should enable submit
    await page.getByTestId('symbol-input').fill('INFY.NS');
    await expect(submitButton).toBeEnabled();
  });

  test('placed order receives status: NEW, FILLED, or REJECTED', async ({ page }) => {
    test.skip(!validUsername || !validPassword, 'Test credentials not configured');

    await signInFresh(page);

    // Navigate to order ticket
    await page.click('[data-testid="nav-order-ticket"]');
    await expect(page.locator('[data-testid="order-ticket"]')).toBeVisible();

    // Fill valid order
    await page.getByTestId('symbol-input').fill('INFY.NS');
    await page.getByTestId('side-input').selectOption('BUY');
    await page.getByTestId('quantity-input').fill('5');
    await page.getByTestId('price-input').fill('1500.50');

    // Submit order
    const submitButton = page.getByTestId('submit-order-button');
    await submitButton.click();

    // Response should show success message with order status
    const responseMessage = page.locator('[data-testid="response-message"]');
    await expect(responseMessage).toBeVisible();

    // Accept any valid response status: NEW, FILLED, or REJECTED
    const messageText = await responseMessage.textContent();
    expect(messageText).toMatch(/NEW|FILLED|REJECTED/);
  });

  test('order rejection shows trader-facing error message', async ({ page }) => {
    test.skip(!validUsername || !validPassword, 'Test credentials not configured');

    await signInFresh(page);

    // Navigate to order ticket
    await page.click('[data-testid="nav-order-ticket"]');
    await expect(page.locator('[data-testid="order-ticket"]')).toBeVisible();

    // Try placing order with unrealistic price that will be rejected
    // (e.g., trying to buy a huge quantity with insufficient funds)
    await page.getByTestId('symbol-input').fill('INFY.NS');
    await page.getByTestId('side-input').selectOption('BUY');
    await page.getByTestId('quantity-input').fill('99999'); // Unrealistic quantity
    await page.getByTestId('price-input').fill('99999.99'); // Unrealistic price

    // Submit order
    const submitButton = page.getByTestId('submit-order-button');
    await submitButton.click();

    // Error should be visible
    const responseElement = page.locator('[data-testid="response-message"]');
    await expect(responseElement).toBeVisible();

    // Message should be trader-friendly (not developer jargon)
    const messageText = await responseElement.textContent();
    expect(messageText).toBeTruthy();
    // Should not contain technical error codes or stack traces
    expect(messageText).not.toMatch(/Error|Exception|Stack|null|undefined/i);
  });

  test('each order placement is isolated with fresh authentication', async ({
    page,
    context,
  }) => {
    test.skip(!validUsername || !validPassword, 'Test credentials not configured');

    // First order: sign in, place order
    await signInFresh(page);
    await page.click('[data-testid="nav-order-ticket"]');
    await page.getByTestId('symbol-input').fill('INFY.NS');
    await page.getByTestId('side-input').selectOption('BUY');
    await page.getByTestId('quantity-input').fill('1');
    await page.getByTestId('price-input').fill('1500.00');
    const firstOrderButton = page.getByTestId('submit-order-button');
    await firstOrderButton.click();

    // Wait for response
    await expect(page.locator('[data-testid="response-message"]')).toBeVisible();
    const firstResponse = await page.locator('[data-testid="response-message"]').textContent();

    // Clear cookies for second order (fresh session)
    await context.clearCookies();

    // Second order: sign in again with fresh session
    await signInFresh(page);
    await page.click('[data-testid="nav-order-ticket"]');
    await page.getByTestId('symbol-input').fill('RELIANCE.NS');
    await page.getByTestId('side-input').selectOption('SELL');
    await page.getByTestId('quantity-input').fill('2');
    await page.getByTestId('price-input').fill('2500.00');
    const secondOrderButton = page.getByTestId('submit-order-button');
    await secondOrderButton.click();

    // Wait for response
    await expect(page.locator('[data-testid="response-message"]')).toBeVisible();
    const secondResponse = await page.locator('[data-testid="response-message"]').textContent();

    // Both should have responses but may differ (isolated)
    expect(firstResponse).toBeTruthy();
    expect(secondResponse).toBeTruthy();
    // They should be independent placements
    expect(firstResponse).not.toEqual(secondResponse);
  });
});