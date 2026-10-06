import { Router, UrlTree } from '@angular/router';

export function sanitizeReturnTo(returnTo: string | null | undefined): string | null {
  if (!returnTo || !returnTo.startsWith('/')) {
    return null;
  }

  try {
    const parsed = new URL(returnTo, window.location.origin);

    if (parsed.origin !== window.location.origin) {
      return null;
    }

    return `${parsed.pathname}${parsed.search}${parsed.hash}`;
  } catch {
    return null;
  }
}

export function createSignInRedirect(router: Router, returnTo: string): UrlTree {
  const sanitizedReturnTo = sanitizeReturnTo(returnTo);

  return router.createUrlTree(['/sign-in'], {
    queryParams: sanitizedReturnTo ? { returnTo: sanitizedReturnTo } : undefined,
  });
}