import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { advanceFocus, focusField } from './form-focus';

describe('form-focus: el foco salta solo al siguiente campo', () => {
  let host: HTMLElement;

  beforeEach(() => {
    vi.useFakeTimers();
    host = document.createElement('div');
    host.innerHTML = ['fund', 'idPart1', 'bags'].map((k) => `<input data-field-key="${k}" />`).join('');
    document.body.appendChild(host);
  });

  afterEach(() => {
    host.remove();
    vi.useRealTimers();
  });

  const field = (key: string) => host.querySelector<HTMLElement>(`[data-field-key="${key}"]`)!;

  it('focuses the field after the view updates and flashes it for one second', () => {
    focusField(host, 'idPart1');
    expect(document.activeElement).not.toBe(field('idPart1'));

    vi.advanceTimersByTime(0);
    expect(document.activeElement).toBe(field('idPart1'));
    expect(field('idPart1').classList.contains('field-flash')).toBe(true);

    vi.advanceTimersByTime(1000);
    expect(field('idPart1').classList.contains('field-flash')).toBe(false);
  });

  it('does nothing when the field is not on the screen', () => {
    expect(() => {
      focusField(host, 'missing');
      vi.runAllTimers();
    }).not.toThrow();
  });

  it('moves to the next field of the order; not after the last one or an unknown one', () => {
    const order = ['fund', 'idPart1', 'bags'];
    advanceFocus(host, order, 'fund');
    vi.runAllTimers();
    expect(document.activeElement).toBe(field('idPart1'));

    advanceFocus(host, order, 'bags');
    advanceFocus(host, order, 'other');
    vi.runAllTimers();
    expect(document.activeElement).toBe(field('idPart1'));
  });
});
