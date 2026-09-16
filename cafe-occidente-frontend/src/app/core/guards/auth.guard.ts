import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';

import { AuthService } from '../services/auth.service';

/** Exige sesion valida; si no hay una en memoria pero existe un refresh
 *  token guardado (recarga de pagina), intenta restaurarla antes de negar. */
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.session()) {
    return true;
  }
  if (!auth.hasStoredRefreshToken()) {
    return router.createUrlTree(['/login']);
  }
  return auth.restoreSession().pipe(
    map(() => true),
    catchError(() => of(router.createUrlTree(['/login']))),
  );
};
