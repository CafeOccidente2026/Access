import { signal } from '@angular/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { askCellphoneOr } from './seller-cellphone';

describe('askCellphoneOr (prueba del 10-nov, fallas 2 y 3)', () => {
  let host: HTMLElement;

  beforeEach(() => {
    vi.useFakeTimers();
    host = document.body.appendChild(document.createElement('div'));
    host.innerHTML = '<input data-field-key="cellphone" /><input data-field-key="special" />';
  });

  afterEach(() => {
    host.remove();
    vi.useRealTimers();
  });

  function form(cellphone: string) {
    return {
      model: { cellphone } as Record<string, string>,
      errorMessage: signal<string | null>(null),
      messages: () => ({ cellphoneMissing: 'Este vendedor no tiene celular registrado.' }),
    };
  }

  it('without a cellphone it warns, puts the focus on Celular and does not go on', () => {
    const next = vi.fn();
    const f = form('  ');
    askCellphoneOr(host, f, next);
    vi.runAllTimers();

    expect(f.errorMessage()).toBe('Este vendedor no tiene celular registrado.');
    expect(document.activeElement).toBe(host.querySelector('[data-field-key="cellphone"]'));
    expect(next).not.toHaveBeenCalled();
  });

  it('with a cellphone it goes on without a warning', () => {
    const next = vi.fn();
    const f = form('3001112233');
    askCellphoneOr(host, f, next);

    expect(next).toHaveBeenCalledOnce();
    expect(f.errorMessage()).toBeNull();
  });
});
