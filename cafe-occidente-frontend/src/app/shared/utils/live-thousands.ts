import { formatThousands } from './number-format';

/**
 * Puntos de miles mientras se digita plata (pesos enteros): 45690 -> 45.690. Lo comparten `form-field`
 * (`liveThousands`) y la directiva `appLiveThousands`; al servidor siempre va el número limpio.
 */

/** Lo digitado o pegado, limpio: solo pesos enteros (lo de después de la coma no cuenta) y, si se permite, el menos. */
function typedDigits(text: string, negative: boolean): string {
  const whole = text.split(',')[0];
  const digits = whole.replace(/\D/g, '').replace(/^0+(?=\d)/, '');
  return negative && digits !== '' && whole.trim().startsWith('-') ? `-${digits}` : digits;
}

/** El número limpio con sus puntos de miles ("-2300" -> "-2.300"). */
export function formatMoneyInput(clean: string): string {
  return clean.startsWith('-') ? `-${formatThousands(clean.slice(1))}` : formatThousands(clean);
}

/** Un valor del servidor o del modelo (8379840, "8379840.00", "45.690") como número limpio. */
export function moneyDigits(value: string | number | null | undefined, negative = false): string {
  if (value === null || value === undefined || value === '') {
    return '';
  }
  const text = String(value).trim();
  if (typeof value === 'number' || /^-?\d+(\.\d{1,2})?$/.test(text)) {
    const whole = Math.trunc(Number(text));
    return String(negative ? whole : Math.abs(whole));
  }
  return typedDigits(text, negative);
}

/** Pone los puntos en el campo sin mover el cursor del dígito donde estaba; devuelve el número limpio. */
export function applyLiveThousands(input: HTMLInputElement, negative = false): string {
  const raw = input.value;
  const caret = input.selectionStart ?? raw.length;
  const clean = typedDigits(raw, negative);
  const leadingZeros = (raw.split(',')[0].replace(/\D/g, '').match(/^0+(?=\d)/) ?? [''])[0].length;
  const digitsBefore = Math.max(0, raw.slice(0, caret).split(',')[0].replace(/\D/g, '').length - leadingZeros);
  const shown = formatMoneyInput(clean);
  input.value = shown;
  let position = shown.startsWith('-') && caret > 0 ? 1 : 0;
  for (let seen = 0; seen < digitsBefore && position < shown.length; position++) {
    if (/\d/.test(shown[position])) {
      seen++;
    }
  }
  input.setSelectionRange(position, position);
  return clean;
}

/** Backspace justo después de un punto (o Supr justo antes) salta el punto y borra el dígito. */
export function skipThousandsSeparator(event: KeyboardEvent, input: HTMLInputElement): void {
  const at = input.selectionStart;
  if (at === null || at !== input.selectionEnd) {
    return;
  }
  if (event.key === 'Backspace' && input.value[at - 1] === '.') {
    input.setSelectionRange(at - 1, at - 1);
  } else if (event.key === 'Delete' && input.value[at] === '.') {
    input.setSelectionRange(at + 1, at + 1);
  }
}
