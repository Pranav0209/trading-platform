import { getTestBed } from '@angular/core/testing';
import { BrowserDynamicTestingModule, platformBrowserDynamicTesting } from '@angular/platform-browser-dynamic/testing';

// Initialize the Angular testing environment
getTestBed().initTestEnvironment(
  BrowserDynamicTestingModule,
  platformBrowserDynamicTesting(),
);

// Mock fetch for external template loading in jsdom
const originalFetch = globalThis.fetch;
globalThis.fetch = ((url: string | Request, options?: RequestInit) => {
  const urlString = typeof url === 'string' ? url : url.url;
  
  // Intercept .html and .css file requests and return empty content
  if (urlString.endsWith('.html') || urlString.endsWith('.css')) {
    return Promise.resolve(
      new Response('', {
        status: 200,
        statusText: 'OK',
        headers: new Headers({
          'Content-Type': urlString.endsWith('.css') ? 'text/css' : 'text/html',
        }),
      })
    );
  }
  
  // Pass through other requests to original fetch
  return originalFetch.call(globalThis, url as any, options);
}) as any;

// Mock localStorage for tests
const localStorageMock: Storage = {
  getItem: (key: string) => null,
  setItem: (key: string, value: string) => {},
  removeItem: (key: string) => {},
  clear: () => {},
  length: 0,
  key: (index: number) => null,
};

Object.defineProperty(window, 'localStorage', {
  value: localStorageMock,
  writable: true,
});
