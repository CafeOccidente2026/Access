import { afterEach, describe, expect, it } from 'vitest';

import { applyLiveThousands, formatMoneyInput, moneyDigits, skipThousandsSeparator } from './live-thousands';

function typed(value: string, caret = value.length): HTMLInputElement {
  const input = document.createElement('input');
  document.body.appendChild(input);
  input.value = value;
  input.setSelectionRange(caret, caret);
  return input;
}

describe('live thousands', () => {
  afterEach(() => document.body.querySelectorAll('input').forEach((i) => i.remove()));

  it('shows the thousands while typing and gives back the clean number', () => {
    const input = typed('45690');
    expect(applyLiveThousands(input)).toBe('45690');
    expect(input.value).toBe('45.690');
  });

  it('keeps the cursor next to the same digit when typing in the middle', () => {
    // "4.5|90" + "6" -> "45.6|90" (el cursor queda despues del 6)
    const input = typed('4.5690', 4);
    applyLiveThousands(input);
    expect(input.value).toBe('45.690');
    expect(input.selectionStart).toBe(4);
  });

  it('deleting a digit keeps the cursor in place', () => {
    // "1.234|.567" y Backspace borra el 4: "1.23|.567" -> "123|.567" (el cursor sigue despues del 3)
    const input = typed('1.23.567', 4);
    expect(applyLiveThousands(input)).toBe('123567');
    expect(input.value).toBe('123.567');
    expect(input.selectionStart).toBe(3);
  });

  it('a pasted amount with dots, decimals or letters keeps only the whole pesos', () => {
    expect(applyLiveThousands(typed('$ 45.690,00'))).toBe('45690');
    expect(applyLiveThousands(typed('8379840'))).toBe('8379840');
    expect(applyLiveThousands(typed('00'))).toBe('0');
    expect(applyLiveThousands(typed(''))).toBe('');
  });

  it('only a field that allows it keeps the minus sign (Castigo)', () => {
    expect(applyLiveThousands(typed('-2300'), true)).toBe('-2300');
    const input = typed('-2300');
    expect(applyLiveThousands(input)).toBe('2300');
    expect(input.value).toBe('2.300');
  });

  it('Backspace right after a dot jumps over it and Delete right before a dot too', () => {
    const back = typed('45.690', 3);
    skipThousandsSeparator(new KeyboardEvent('keydown', { key: 'Backspace' }), back);
    expect(back.selectionStart).toBe(2);
    const del = typed('45.690', 2);
    skipThousandsSeparator(new KeyboardEvent('keydown', { key: 'Delete' }), del);
    expect(del.selectionStart).toBe(3);
    const other = typed('45.690', 3);
    skipThousandsSeparator(new KeyboardEvent('keydown', { key: 'a' }), other);
    expect(other.selectionStart).toBe(3);
  });

  it('a value of the server or the model shows as whole pesos with thousands', () => {
    expect(moneyDigits(8379840)).toBe('8379840');
    expect(moneyDigits('8379840.00')).toBe('8379840');
    expect(moneyDigits('8379840,50')).toBe('8379840');
    expect(moneyDigits('45.690')).toBe('45690');
    expect(moneyDigits(-2300.4, true)).toBe('-2300');
    expect(moneyDigits(-2300)).toBe('2300');
    expect(moneyDigits(null)).toBe('');
    expect(formatMoneyInput('-2300')).toBe('-2.300');
    expect(formatMoneyInput('')).toBe('');
  });
});
