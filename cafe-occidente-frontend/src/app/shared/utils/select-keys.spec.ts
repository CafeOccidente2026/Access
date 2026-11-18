import { describe, expect, it } from 'vitest';

import { changedByKeyboard } from './select-keys';

/** Decision del usuario (10-nov): en una lista nativa la flecha solo cambia la opcion; se pasa con Enter o con el mouse. */
describe('changedByKeyboard', () => {
  const key = (el: Element, k: string) => el.dispatchEvent(new KeyboardEvent('keydown', { key: k, bubbles: true }));
  const mouse = (el: Element) => el.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));

  it('an arrow or a letter on the list counts as keyboard; the mouse or Enter clears it', () => {
    const select = document.createElement('select');
    document.body.appendChild(select);

    expect(changedByKeyboard(select)).toBe(false);
    key(select, 'ArrowDown');
    expect(changedByKeyboard(select)).toBe(true);
    mouse(select);
    expect(changedByKeyboard(select)).toBe(false);
    key(select, 'r');
    expect(changedByKeyboard(select)).toBe(true);
    key(select, 'Enter');
    expect(changedByKeyboard(select)).toBe(false);
    select.remove();
  });
});
