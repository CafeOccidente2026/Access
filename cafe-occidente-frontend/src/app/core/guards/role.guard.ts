import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { UserRole } from '../models';
import { AuthService } from '../services/auth.service';

/** Restringe una ruta a los roles indicados; requiere que authGuard ya haya
 *  corrido antes en la misma ruta para que la sesion este resuelta. */
export function roleGuard(...allowedRoles: UserRole[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);

    const session = auth.session();
    if (session && allowedRoles.includes(session.role)) {
      return true;
    }
    return router.createUrlTree(['/acceso-principal']);
  };
}
