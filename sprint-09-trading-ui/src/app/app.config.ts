import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';

import { routes } from './app.routes';
import { BASE_PATH as AUTH_BASE_PATH } from '../generated/auth/variables';
import { BASE_PATH as TRADE_BASE_PATH } from '../generated/trade/variables';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(),
    { provide: AUTH_BASE_PATH, useValue: 'http://localhost:3001' },
    { provide: TRADE_BASE_PATH, useValue: 'http://localhost:8081' }
  ]
};
