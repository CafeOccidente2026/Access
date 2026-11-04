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

  it('by default a failed save logs the detail, shows the screen saveError and repaints, without resetting', () => {
    const f = form();
    const refresh = vi.fn();
    const log = vi.spyOn(console, 'error').mockImplementation(() => undefined);

    saveAndPrint(f, {
      name: 'Pasilla',
      save: () => throwError(() => ({ error: { message: '400' } })),
      invoice: () => ({ content: [] }),
      messages: () => ({ saveError: 'No se pudo registrar la compra. Verifique los datos.' }),
      refresh,
    });

    expect(log).toHaveBeenCalledWith('Error al registrar compra Pasilla:', { message: '400' });
    expect(f.errorMessage()).toBe('No se pudo registrar la compra. Verifique los datos.');
    expect(refresh).toHaveBeenCalledTimes(1);
    expect(f.reset).not.toHaveBeenCalled();
    expect(f.saving()).toBe(false);
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
