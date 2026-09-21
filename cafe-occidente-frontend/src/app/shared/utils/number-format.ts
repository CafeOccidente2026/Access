/** Formatea numeros al estilo colombiano (punto miles, coma decimal) para mostrar en pantalla.
 *  Solo afecta lo que se ve: el valor real que se envia al backend nunca pasa por aqui. */
export function formatDisplayNumber(value: string | number, decimals: 'currency' | 'count'): string {
  // parseFloat nativo no entiende la coma decimal y corta en el primer caracter invalido -> "1,8"
  // se convertia en 1 apenas el campo pasaba a solo-lectura (ver parseDisplayNumber para el mismo
  // problema del lado de "lo que se envia").
  const num = typeof value === 'number' ? value : Number.parseFloat(normalizeDecimalComma(value));
  if (value === '' || value === null || value === undefined || Number.isNaN(num)) {
    return typeof value === 'string' ? value : '';
  }
  return decimals === 'currency'
    ? num.toLocaleString('es-CO', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
    : num.toLocaleString('es-CO');
}

function normalizeDecimalComma(value: string): string {
  return value.includes(',') ? value.replace(/\./g, '').replace(',', '.') : value;
}

/** "SDTA-3500" -> "3500": el Anuncio se guarda y numera con prefijo por agencia, pero en
 *  pantalla se muestra solo el numero. */
export function stripAnnouncementPrefix(value: string): string {
  const idx = value.lastIndexOf('-');
  return idx === -1 ? value : value.slice(idx + 1);
}

/** Convierte lo que el usuario digita (0, 122, o el formato colombiano 1,8 / 1.234,56) a number.
 *  `Number()` nativo no entiende la coma decimal -> sin esto, "1,8" da NaN (y termina viajando
 *  como null al backend, que lo rechaza con "must not be null" sin explicarle nada al usuario). */
export function parseDisplayNumber(value: string | undefined | null): number {
  const s = (value ?? '').trim();
  if (s === '') {
    return 0;
  }
  const n = Number(normalizeDecimalComma(s));
  return Number.isNaN(n) ? 0 : n;
}
