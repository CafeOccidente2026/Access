import { afterEach, describe, expect, it } from 'vitest';

import { installUppercaseInputs } from './uppercase-input';

/** Demo del 23-nov: lo que se digita se ve en mayusculas mientras se escribe (Terceros, Conductores, Detalle...). */
describe('installUppercaseInputs', () => {
  let uninstall: () => void = () => undefined;
  afterEach(() => {
    uninstall();
    document.body.innerHTML = '';
  });

  function type(html: string, text: string): string {
    document.body.innerHTML = html;
    const el = document.body.querySelector<HTMLInputElement | HTMLTextAreaElement>('input, textarea')!;
    let seen = '';
    el.addEventListener('input', () => (seen = el.value));
    el.value = text;
    if (el instanceof HTMLTextAreaElement || el.type === 'text') {
      el.setSelectionRange(2, 2);
    }
    el.dispatchEvent(new Event('input', { bubbles: true }));
    return seen;
  }

  it('upper-cases text inputs and textareas before the screen reads the value, keeping the cursor', () => {
    uninstall = installUppercaseInputs();
    expect(type('<input type="text">', 'trans oriente')).toBe('TRANS ORIENTE');
    expect(type('<input>', 'vereda ñame')).toBe('VEREDA ÑAME');
    expect(type('<textarea></textarea>', 'detalle')).toBe('DETALLE');
    expect(document.querySelector('textarea')!.selectionStart).toBe(2);
  });

  it('leaves alone e-mail, password, search, dates and anything marked data-keep-case (login user)', () => {
    uninstall = installUppercaseInputs();
    expect(type('<input type="email">', 'a@b.co')).toBe('a@b.co');
    expect(type('<input type="password">', 'Clave1')).toBe('Clave1');
    expect(type('<input type="search">', 'busco')).toBe('busco');
    expect(type('<div data-keep-case><input type="text"></div>', 'Usuario.Uno')).toBe('Usuario.Uno');
  });

  it('does nothing once uninstalled', () => {
    installUppercaseInputs()();
    expect(type('<input type="text">', 'abc')).toBe('abc');
  });
});
