import { TestBed } from '@angular/core/testing';
import { Router, UrlTree, provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { adminGuard } from './admin.guard';
import { Session } from '../session';

describe('adminGuard', () => {
  let router: Router;
  let loggedIn = false;
  let token: string | null = null;

  const adminToken = [
    'header',
    btoa(JSON.stringify({ roles: ['ADMIN'] })),
    'signature',
  ].join('.');

  beforeEach(() => {
    loggedIn = false;
    token = null;

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: Session,
          useValue: {
            get isLoggedIn() {
              return loggedIn;
            },
            token: () => token,
          },
        },
      ],
    });

    router = TestBed.inject(Router);
  });

  it('redirects signed-out visitors instead of leaving navigation unresolved', () => {
    const result = TestBed.runInInjectionContext(() => adminGuard({} as never, { url: '/admin' } as never));

    expect(result instanceof UrlTree).toBeTrue();
    expect(router.serializeUrl(result as UrlTree)).toBe('/sign-in?returnTo=%2Fadmin');
  });

  it('allows authenticated administrators', () => {
    loggedIn = true;
    token = adminToken;

    const result = TestBed.runInInjectionContext(() => adminGuard({} as never, { url: '/admin' } as never));

    expect(result).toBeTrue();
  });
});