import { inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivateChildFn, Router } from '@angular/router';

import { UserRole } from '../models';
import { AuthService } from '../services/auth.service';

/** Roles de una pantalla de operacion cuando la ruta no dice otra cosa (RN-01.2: Operaciones no entra). */
export const DEFAULT_ROUTE_ROLES: readonly UserRole[] = ['ADMINISTRADOR', 'USUARIO'];

/**
 * Toda pantalla salvo el login exige sesion, y cada ruta solo deja entrar a sus roles (data.roles).
 * Sin sesion va al login; con un rol que no corresponde, a su menu principal. El backend igual lo
 * rechaza con @PreAuthorize: esto evita que el usuario vea la pantalla antes del 403.
 */
export const authGuard: CanActivateChildFn = (route: ActivatedRouteSnapshot) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.isAuthenticated()) {
    return router.parseUrl('/login');
  }
  const roles = (route.data['roles'] as readonly UserRole[] | undefined) ?? DEFAULT_ROUTE_ROLES;
  return auth.hasRole(roles) ? true : router.parseUrl('/menu-principal');
};
