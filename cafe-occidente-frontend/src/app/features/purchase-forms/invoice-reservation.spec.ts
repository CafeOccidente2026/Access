import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { reserveInvoice } from './invoice-reservation';

/** Seco, verde y pasilla piden la siguiente factura igual; si falla, el aviso es el motivo del servidor (punto 4). */
describe('reserveInvoice', () => {
  const form = () => ({
    invoiceReservation: signal<{ invoiceNumber: number; warning?: string | null } | null>(null),
    errorMessage: signal<string | null>(null),
    messages: () => ({ saveError: 'No se pudo registrar la compra.' }),
  });

  it('guarda la factura y muestra el aviso de resolución por vencer', () => {
    const f = form();
    const refresh = vi.fn();
    reserveInvoice(f, of({ invoiceNumber: 43907, warning: 'Resolución próxima a vencer' }), refresh);
    expect(f.invoiceReservation()?.invoiceNumber).toBe(43907);
    expect(f.errorMessage()).toBe('Resolución próxima a vencer');
    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it('sin aviso no muestra nada', () => {
    const f = form();
    reserveInvoice(f, of({ invoiceNumber: 1, warning: null }), vi.fn());
    expect(f.errorMessage()).toBeNull();
  });

  it('si falla muestra el motivo del servidor', () => {
    const f = form();
    const refresh = vi.fn();
    reserveInvoice(f, throwError(() => ({ error: { message: 'Numeración vencida' } })), refresh);
    expect(f.errorMessage()).toBe('Numeración vencida');
    expect(refresh).toHaveBeenCalledTimes(1);
  });
});
