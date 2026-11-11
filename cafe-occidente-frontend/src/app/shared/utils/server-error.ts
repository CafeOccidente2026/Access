import { HttpErrorResponse } from '@angular/common/http';

import texts from '../../../../public/assets/data/server-errors.json';

/**
 * Prueba del 11-nov (punto 4): el texto de un error para la pantalla. Primero el motivo que manda el servidor (regla
 * de negocio, campo que falta, restriccion de la base); sin conexion, que no hay conexion; si no viene motivo, el
 * texto de respaldo de la pantalla (qué se intentó) o el código HTTP.
 */
export function serverError(err: unknown, fallback: string | null | undefined): string | null {
  if (!(err instanceof HttpErrorResponse)) {
    return fallback ?? null;
  }
  if (err.status === 0) {
    return texts.offline;
  }
  const message = (err.error as { message?: unknown } | null)?.message;
  if (typeof message === 'string' && message.trim() !== '') {
    return message;
  }
  return fallback ?? texts.status.replace('{status}', String(err.status));
}
