import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Session } from '../session';
import { createSignInRedirect } from './return-to';

export const authGuard: CanActivateFn = (_route, state) => {
  const session = inject(Session);

  if (session.isLoggedIn) {
    return true;
  }

  return createSignInRedirect(inject(Router), state.url);
};