import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';

import { AuthService } from '../services/auth.service';

/** Agrega el access token a cada request y reintenta una vez tras refrescar
 *  la sesion si el backend responde 401. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const isAuthEndpoint = req.url.includes('/auth/login') || req.url.includes('/auth/refresh');

  const token = auth.getAccessToken();
  const authorizedReq =
    token && !isAuthEndpoint ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(authorizedReq).pipe(
    catchError((error: HttpErrorResponse) => {
      if (isAuthEndpoint || error.status !== 401 || !auth.hasStoredRefreshToken()) {
        return throwError(() => error);
      }
      return auth.restoreSession().pipe(
        switchMap(() =>
          next(req.clone({ setHeaders: { Authorization: `Bearer ${auth.getAccessToken()}` } })),
        ),
        catchError((refreshError) => {
          auth.logout();
          return throwError(() => refreshError);
        }),
      );
    }),
  );
};
