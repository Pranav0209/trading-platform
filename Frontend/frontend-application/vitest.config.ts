import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['src/test-setup.ts'],
    exclude: [
      'node_modules',
      'dist',
      '.idea',
      '.git',
      '.cache',
      'e2e/**/*.spec.ts', // Exclude e2e tests - they use Playwright not Vitest
    ],
    include: ['src/**/*.spec.ts'], // Only include unit tests in src
    // Suppress component resolution warnings in jsdom
    testTimeout: 5000,
    hookTimeout: 5000,
  },
  resolve: {
    alias: {},
  },
});
