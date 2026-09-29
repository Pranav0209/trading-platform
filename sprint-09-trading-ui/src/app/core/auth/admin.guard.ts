import { inject } from '@angular/core';
import { Router, CanActivateFn } from '@angular/router';
import { Session } from './session';

export const adminGuard: CanActivateFn = () => {
  const session = inject(Session);
  const router = inject(Router);
  
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
