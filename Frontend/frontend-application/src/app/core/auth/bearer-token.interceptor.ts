import {
  HttpInterceptor,
  HttpRequest,
  HttpHandler,
  HttpEvent,
  HttpClient,
} from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, throwError, switchMap, from, lastValueFrom } from 'rxjs';
import { Router } from '@angular/router';
import { Session } from './session';
import { environment } from '../../../environments/environment';

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
  private httpClient = inject(HttpClient);

  // Get URLs from environment configuration (injected from .env during build)
  private authApiUrl = environment.authApiUrl;
  private tradeApiUrl = environment.tradeApiUrl;

  /** Allow list of origins that should receive the bearer token */
  private allowedOrigins = new Set([
    this.authApiUrl, // Auth service - protected routes only
    this.tradeApiUrl, // Trade API - all routes
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
   * Silently requests a new access token using the refresh token in the HttpOnly cookie.
   * 
   * CRITICAL: Must use withCredentials: true so the browser sends the HttpOnly cookie.
   * Backend reads refresh token from cookie, not from request body.
   */
  private async attemptRefresh(): Promise<string | null> {
    try {
      // Make direct HTTP call with credentials enabled
      // Backend reads refreshToken from HttpOnly cookie automatically
      const refreshUrl = `${this.authApiUrl}/auth/refresh`;
      const response = await lastValueFrom(
        this.httpClient.post<{ accessToken: string }>(
          refreshUrl,
          {}, // Empty body - token comes from cookie
          { withCredentials: true } // CRITICAL: Enables cookie transmission
        )
      );
      return response.accessToken;
    } catch (error) {
      console.error('Token refresh failed:', error);
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
