import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withInMemoryScrolling } from '@angular/router';
import { provideHttpClient, withInterceptorsFromDi, HTTP_INTERCEPTORS } from '@angular/common/http';

import { routes } from './app.routes';
import { BASE_PATH as AUTH_BASE_PATH } from '../generated/auth/variables';
import { Configuration as AuthConfiguration } from '../generated/auth/configuration';
import { BASE_PATH as TRADE_BASE_PATH } from '../generated/trade/variables';
import { BearerTokenInterceptor } from './core/auth/bearer-token.interceptor';
import { environment } from '../environments/environment';

// Get URLs from environment configuration (injected from .env during build)
const authApiUrl = environment.authApiUrl;
const tradeApiUrl = environment.tradeApiUrl;

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withInMemoryScrolling({ anchorScrolling: 'enabled' })),
    provideHttpClient(withInterceptorsFromDi()),
    {
      provide: HTTP_INTERCEPTORS,
      useClass: BearerTokenInterceptor,
      multi: true,
    },
    { 
      provide: AuthConfiguration, 
      useFactory: () => new AuthConfiguration({ 
        basePath: authApiUrl,
        withCredentials: true 
      }) 
    },
    { provide: TRADE_BASE_PATH, useValue: tradeApiUrl }
  ]
};
