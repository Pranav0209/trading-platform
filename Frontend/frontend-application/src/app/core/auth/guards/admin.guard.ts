import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Session } from '../session';
import { createSignInRedirect } from './return-to';

export const adminGuard: CanActivateFn = (_route, state) => {
  const session = inject(Session);
  const router = inject(Router);

  if (!session.isLoggedIn) {
    return createSignInRedirect(router, state.url);
  }

  const token = session.token();
  if (token) {
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      if (payload.roles && payload.roles.includes('ADMIN')) {
        return true;
      }
    } catch (e) {
      console.error('Failed to parse token in adminGuard', e);
    }
  }

  return router.parseUrl('/sign-in');
};