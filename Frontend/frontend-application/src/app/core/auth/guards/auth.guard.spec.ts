import { TestBed } from '@angular/core/testing';
import { Router, UrlTree, provideRouter } from '@angular/router';
import { authGuard } from './auth.guard';
import { Session } from '../session';

describe('authGuard', () => {
  let router: Router;
  let loggedIn = false;

  beforeEach(() => {
    loggedIn = false;

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: Session,
          useValue: {
            get isLoggedIn() {
              return loggedIn;
            },
          },
        },
      ],
    });

    router = TestBed.inject(Router);
  });

  it('allows authenticated navigation', () => {
    loggedIn = true;

    const result = TestBed.runInInjectionContext(() => authGuard({} as never, { url: '/register' } as never));

    expect(result).toBe(true);
  });

  it('blocks unauthenticated navigation and redirects to sign-in with the target path', () => {
    const result = TestBed.runInInjectionContext(() => authGuard({} as never, { url: '/register?step=confirm' } as never));

    expect(result instanceof UrlTree).toBe(true);
    expect(router.serializeUrl(result as UrlTree)).toBe('/sign-in?returnTo=%2Fregister%3Fstep%3Dconfirm');
  });
});