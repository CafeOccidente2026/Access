import { signal } from '@angular/core';
import { throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { PrintablePurchaseForm, saveAndPrint } from './purchase-print';

describe('saveAndPrint', () => {
  function form(): PrintablePurchaseForm {
    return {
      saving: signal(false),
      payment: signal(null),
      savedNoticeOpen: signal(false),
      errorMessage: signal<string | null>(null),
      reset: vi.fn(),
    };
  }

  it('by default a failed save shows the reason of the server (prueba del 11-nov), logs it and repaints, without resetting', () => {
    const f = form();
    const refresh = vi.fn();
    const log = vi.spyOn(console, 'error').mockImplementation(() => undefined);

    saveAndPrint(f, {
      name: 'Pasilla',
      save: () => throwError(() => ({ error: { message: 'Falta o está mal: Kilos Brutos' } })),
      invoice: () => ({ content: [] }),
      messages: () => ({ saveError: 'No se pudo registrar la compra.' }),
      refresh,
    });

    expect(log).toHaveBeenCalledWith('Error al registrar compra Pasilla:', { message: 'Falta o está mal: Kilos Brutos' });
    expect(f.errorMessage()).toBe('Falta o está mal: Kilos Brutos');
    expect(refresh).toHaveBeenCalledTimes(1);
    expect(f.reset).not.toHaveBeenCalled();
    expect(f.saving()).toBe(false);
    log.mockRestore();
  });

  it('without a reason from the server it shows the text of the screen', () => {
    const f = form();
    const log = vi.spyOn(console, 'error').mockImplementation(() => undefined);

    saveAndPrint(f, {
      name: 'Pasilla',
      save: () => throwError(() => new Error('sin respuesta')),
      invoice: () => ({ content: [] }),
      messages: () => ({ saveError: 'No se pudo registrar la compra.' }),
      refresh: vi.fn(),
    });

    expect(f.errorMessage()).toBe('No se pudo registrar la compra.');
    log.mockRestore();
  });

  it('uses the screen own saveError when it passes one (Cafe Seco, threshold)', () => {
    const f = form();
    const saveError = vi.fn();
    const refresh = vi.fn();

    saveAndPrint(f, {
      name: 'Cafe Seco',
      save: () => throwError(() => new Error('umbral')),
      invoice: () => ({ content: [] }),
      messages: () => undefined,
      refresh,
      saveError,
    });

    expect(saveError).toHaveBeenCalledTimes(1);
    expect(refresh).not.toHaveBeenCalled();
  });
});
