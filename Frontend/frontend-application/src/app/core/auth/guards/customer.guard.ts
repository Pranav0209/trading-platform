import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Session } from '../session';

/**
 * Guard that prevents admins from accessing customer-only routes.
 * If an admin tries to access a customer route, redirect them to the admin dashboard.
 */
export const customerGuard: CanActivateFn = () => {
  const session = inject(Session);
  const router = inject(Router);

  if (session.isAdmin()) {
    return router.parseUrl('/admin');
  }

  return true;
};
