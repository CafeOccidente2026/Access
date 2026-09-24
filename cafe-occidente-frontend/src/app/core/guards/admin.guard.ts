import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from '../services/auth.service';

/** Bloquea rutas de administracion (crear/editar anuncios, etc.) a quien no sea ADMIN - el backend
 *  ya lo rechaza con @PreAuthorize, esto evita que un cajero llegue a ver el formulario primero. */
export const adminGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);
  return authService.isAdmin() ? true : router.parseUrl('/menu-principal');
};
