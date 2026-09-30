import {
  HttpInterceptor,
  HttpRequest,
  HttpHandler,
  HttpEvent,
} from '@angular/common/http';
import { Injectable, inject, Injector } from '@angular/core';
import { Observable, catchError, throwError, switchMap, from, lastValueFrom } from 'rxjs';
import { Router } from '@angular/router';
import { Session } from './session';
import { AuthService } from '../../../generated/auth/api/auth.service';

/**
 * HTTP Interceptor that attaches bearer tokens to requests destined for platform APIs.
 *
 * Uses an allow list of origins to determine which requests should include authorization.
 * This fails closed: new third-party integrations won't silently receive session tokens.
 */
@Injectable({
  providedIn: 'root',
})
export class BearerTokenInterceptor implements HttpInterceptor {
  private session = inject(Session);
  private router = inject(Router);
  private injector = inject(Injector);

  /** Allow list of origins that should receive the bearer token */
  private allowedOrigins = new Set([
    'http://localhost:3001', // Auth service - protected routes only
    'http://localhost:8081', // Trade API - all routes
  ]);

  /** Path segments that indicate unauthenticated routes in the Auth service */
  private unauthenticatedAuthPaths = new Set(['/auth/login', '/auth/register', '/auth/refresh']);

  intercept(
    req: HttpRequest<any>,
    next: HttpHandler
  ): Observable<HttpEvent<any>> {
    const token = this.session.token();

    // Only attach token if:
    // 1. A token exists in the session
    // 2. The request origin is in the allow list
    // 3. The request is not to an unauthenticated auth endpoint
    const isUnauthRoute = this.isUnauthenticatedAuthRoute(req.url);

    if (
      token &&
      this.isAllowedOrigin(req.url) &&
      !isUnauthRoute
    ) {
      req = req.clone({
        setHeaders: {
          Authorization: `Bearer ${token}`,
        },
      });
    }

    return next.handle(req).pipe(
      catchError((error) => {
        // Only attempt refresh if it's a 401 AND the request wasn't already for login/refresh
        if (error.status === 401 && !isUnauthRoute) {
          // Token is expired. Attempt silent refresh using HttpOnly cookie!
          return from(this.attemptRefresh()).pipe(
            switchMap((newAccessToken) => {
              if (newAccessToken) {
                // Refresh succeeded! Update session and retry original request
                this.session.setToken(newAccessToken);
                const retryReq = req.clone({
                  setHeaders: { Authorization: `Bearer ${newAccessToken}` },
                });
                return next.handle(retryReq);
              } else {
                // Refresh failed. Auto-logout!
                this.session.clearToken();
                this.router.navigate(['/sign-in']);
                return throwError(() => error);
              }
            })
          );
        }
        return throwError(() => error);
      })
    );
  }

  /**
   * Silently requests a new access token using the generated AuthService contract.
   * Uses Injector to lazily load AuthService and avoid circular DI errors with HttpClient.
   */
  private async attemptRefresh(): Promise<string | null> {
    try {
      const authService = this.injector.get(AuthService);
      // We pass an empty object because the backend reads the refresh token from the HttpOnly cookie
      const res = await lastValueFrom(authService.refresh({ refreshToken: 'cookie' }));
      return res.accessToken;
    } catch {
      return null;
    }
  }

  /**
   * Determine if the request URL's origin is in the allow list.
   */
  private isAllowedOrigin(url: string): boolean {
    try {
      const urlObj = new URL(url, window.location.href);
      return this.allowedOrigins.has(`${urlObj.protocol}//${urlObj.host}`);
    } catch {
      // If URL parsing fails, assume it's not allowed
      return false;
    }
  }

  /**
   * Determine if the request is to an unauthenticated auth endpoint.
   */
  private isUnauthenticatedAuthRoute(url: string): boolean {
    return Array.from(this.unauthenticatedAuthPaths).some(path => url.includes(path));
  }
}
