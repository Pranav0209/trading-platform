import { defineConfig, devices } from '@playwright/test';
import dotenv from 'dotenv';

dotenv.config();

/**
 * Playwright configuration for e2e tests against the real stack.
 * 
 * Environment variables required:
 * - TRADING_UI_URL: Base URL of the Angular trading UI (e.g., http://localhost:4200)
 * - AUTH_SERVICE_URL: Base URL of the auth service (used by the UI)
 * - TRADE_SERVICE_URL: Base URL of the trade service (used by the UI)
 * - TEST_USERNAME: Valid username for test account
 * - TEST_PASSWORD: Valid password for test account
 * - TEST_ACCOUNT_ID: Numeric account ID associated with test user
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: 1,

  reporter: 'html',

  use: {
    baseURL: process.env.TRADING_UI_URL || 'http://localhost:4200',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],

  webServer: undefined,
});
