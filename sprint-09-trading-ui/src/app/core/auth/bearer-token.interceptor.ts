import {
  HttpInterceptor,
  HttpRequest,
  HttpHandler,
  HttpEvent,
} from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Session } from './session';

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
    if (
      token &&
      this.isAllowedOrigin(req.url) &&
      !this.isUnauthenticatedAuthRoute(req.url)
    ) {
      req = req.clone({
        setHeaders: {
          Authorization: `Bearer ${token}`,
        },
      });
    }

    return next.handle(req);
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
