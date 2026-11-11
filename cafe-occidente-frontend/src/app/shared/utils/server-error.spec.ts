import { HttpErrorResponse } from '@angular/common/http';
import { describe, expect, it } from 'vitest';

import texts from '../../../../public/assets/data/server-errors.json';
import { serverError } from './server-error';

/** Prueba del 11-nov (punto 4): la pantalla muestra el motivo concreto, nunca "Verifique los datos". */
describe('serverError', () => {
  const http = (status: number, error: unknown = null) => new HttpErrorResponse({ status, error });

  it('usa el motivo que manda el servidor', () => {
    expect(serverError(http(400, { message: 'Falta o está mal: Kilos Brutos' }), 'respaldo')).toBe('Falta o está mal: Kilos Brutos');
    expect(serverError(http(409, { message: 'FORMA DE PAGO DESCUADRADA, REVISE' }), null)).toBe('FORMA DE PAGO DESCUADRADA, REVISE');
  });

  it('sin conexión lo dice', () => {
    expect(serverError(http(0), 'respaldo')).toBe(texts.offline);
  });

  it('sin motivo usa el texto de la pantalla o, si no hay, el código', () => {
    expect(serverError(http(500, 'texto plano'), 'No se pudo calcular la compra')).toBe('No se pudo calcular la compra');
    expect(serverError(http(502), undefined)).toBe(texts.status.replace('{status}', '502'));
  });

  it('un error que no es HTTP usa el respaldo', () => {
    expect(serverError(new Error('x'), 'respaldo')).toBe('respaldo');
    expect(serverError(undefined, null)).toBeNull();
  });
});
