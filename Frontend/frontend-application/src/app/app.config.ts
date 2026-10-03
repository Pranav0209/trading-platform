import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptorsFromDi, HTTP_INTERCEPTORS } from '@angular/common/http';

import { routes } from './app.routes';
import { BASE_PATH as AUTH_BASE_PATH } from '../generated/auth/variables';
import { Configuration as AuthConfiguration } from '../generated/auth/configuration';
import { BASE_PATH as TRADE_BASE_PATH } from '../generated/trade/variables';
import { BearerTokenInterceptor } from './core/auth/bearer-token.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptorsFromDi()),
    {
      provide: HTTP_INTERCEPTORS,
      useClass: BearerTokenInterceptor,
      multi: true,
    },
    { 
      provide: AuthConfiguration, 
      useFactory: () => new AuthConfiguration({ 
        basePath: 'http://localhost:3000',
        withCredentials: true 
      }) 
    },
    { provide: TRADE_BASE_PATH, useValue: 'http://localhost:8081' }
  ]
};
