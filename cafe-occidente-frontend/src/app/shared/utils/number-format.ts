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

/** Separador de miles con punto, estilo colombiano (ej. "2500000" -> "2.500.000"), para inputs que
 *  formatean en vivo mientras el usuario digita (solo dígitos, sin decimales). */
export function formatThousands(digits: string): string {
  return digits.replace(/\B(?=(\d{3})+(?!\d))/g, '.');
}

/** "SDTA-3500" -> "3500": el Anuncio se guarda y numera con prefijo por agencia, pero en
 *  pantalla se muestra solo el numero. */
export function stripAnnouncementPrefix(value: string): string {
  const idx = value.lastIndexOf('-');
  return idx === -1 ? value : value.slice(idx + 1);
}

/** Valida un campo numerico entero sin decimales (precios de anuncio: Pr Base Carga, SobrePr,
 *  Precio Pasilla, Pr Por Punto) mientras el usuario escribe. Devuelve el mensaje de error a
 *  mostrar cerca del campo, o null si el valor (posiblemente vacio, mientras se sigue escribiendo)
 *  es valido hasta ahora. Vacio no es error: recien se exige un valor al confirmar el formulario. */
export function validateWholeNumberField(rawValue: string): string | null {
  const trimmed = rawValue.trim();
  if (trimmed === '') {
    return null;
  }
  // Un punto de miles ya insertado por formatThousands es valido; cualquier otro caracter no.
  const withoutThousands = trimmed.replace(/\./g, '');
  if (!/^-?\d+$/.test(withoutThousands)) {
    return 'No se aceptan letras, solo números';
  }
  if (withoutThousands.startsWith('-')) {
    return 'No se aceptan valores negativos en este campo';
  }
  return null;
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
