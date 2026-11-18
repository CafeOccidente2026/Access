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
    ? truncateToCents(num).toLocaleString('es-CO', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
    : num.toLocaleString('es-CO');
}

/**
 * Q3: en pantalla los calculados van con 2 decimales SIN redondear, cortando hacia cero
 * (92,118 -> 92,11; -235,569 -> -235,56). Se corta sobre el texto, no con Math.trunc(x * 100), para
 * que errores de coma flotante (1,005 * 100 = 100,49999...) no quiten un centavo.
 */
function truncateToCents(num: number): number {
  const [whole, fraction = ''] = Math.abs(num).toFixed(12).split('.');
  const cut = Number(`${whole}.${fraction.slice(0, 2)}`);
  return num < 0 && cut !== 0 ? -cut : cut;
}

function normalizeDecimalComma(value: string): string {
  return value.includes(',') ? value.replace(/\./g, '').replace(',', '.') : value;
}

/** Separador de miles con punto, estilo colombiano (ej. "2500000" -> "2.500.000"), para inputs que
 *  formatean en vivo mientras el usuario digita (solo dígitos, sin decimales). */
export function formatThousands(digits: string): string {
  // Sin regex (S8786): un punto antes de cada grupo de 3 digitos que cierra un tramo de digitos, si lo
  // precede una letra, digito o "_" (lo mismo que hacia /\B(?=(\d{3})+(?!\d))/g).
  let out = '';
  let run = 0; // digitos seguidos desde la posicion actual hasta el primer no digito
  const runs = new Array<number>(digits.length + 1).fill(0);
  for (let i = digits.length - 1; i >= 0; i--) {
    run = isDigit(digits[i]) ? run + 1 : 0;
    runs[i] = run;
  }
  for (let i = 0; i < digits.length; i++) {
    if (i > 0 && runs[i] >= 3 && runs[i] % 3 === 0 && isWordChar(digits[i - 1])) {
      out += '.';
    }
    out += digits[i];
  }
  return out;
}

const isDigit = (c: string) => c >= '0' && c <= '9';
const isWordChar = (c: string) => isDigit(c) || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c === '_';

/** "SDTA-3500" -> "3500": el Anuncio se guarda y numera con prefijo por agencia, pero en
 *  pantalla se muestra solo el numero. */
export function stripAnnouncementPrefix(value: string): string {
  const idx = value.lastIndexOf('-');
  return idx === -1 ? value : value.slice(idx + 1);
}

/**
 * R4 (17-nov): el anuncio se muestra con el identificador del punto, "SDTA-3738" -> "TA-3738". "SD" es de los
 * documentos soporte (lo da la resolucion DIAN), no del anuncio. Se guarda igual; solo cambia lo que se ve.
 */
export function announcementDisplay(value: string | null | undefined): string {
  const text = value ?? '';
  return /^SD[^-]*-/.test(text) ? text.slice(2) : text;
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

/**
 * Castigo confirmado (Q1, RN-14.7): solo cero o negativo. "23" queda "-23", "-23" se respeta y vacio o
 * cero quedan "0". El servidor aplica la misma regla, esto es solo lo que ve el cajero.
 */
export function asPenalty(value: string): string {
  const digits = value.trim().replace(/^[+-]/, '');
  return parseDisplayNumber(digits) === 0 ? '0' : `-${digits}`;
}

type Shown = string | number | null | undefined;

/** El número de un valor de pantalla o del servidor; null si viene vacío o no es número. */
function numeric(value: Shown): number | null {
  if (value === '' || value === null || value === undefined) {
    return null;
  }
  const num = typeof value === 'number' ? value : Number.parseFloat(normalizeDecimalComma(value));
  return Number.isNaN(num) ? null : num;
}

const esCO = (num: number, decimals: number, fixed: boolean) =>
  num.toLocaleString('es-CO', { minimumFractionDigits: fixed ? decimals : 0, maximumFractionDigits: decimals });

/** Hallazgo #1 (prueba del 16-nov): dinero sin decimales, en pesos enteros redondeados como se guardan (Q3). */
export function formatMoney(value: Shown): string {
  const num = numeric(value);
  return num === null ? String(value ?? '') : esCO(Math.sign(num) * Math.round(Math.abs(num)), 0, false);
}

/** Kilos: sin ",00" cuando son enteros; con decimales reales, dos y cortando hacia cero (101,50). */
export function formatKilos(value: Shown): string {
  const num = numeric(value);
  if (num === null) {
    return String(value ?? '');
  }
  const cut = truncateToCents(num);
  return esCO(cut, 2, !Number.isInteger(cut));
}

/** Porcentajes y pesos de la muestra: como se digitan (7,1), hasta dos decimales cortando hacia cero. */
export function formatTyped(value: Shown): string {
  const num = numeric(value);
  return num === null ? String(value ?? '') : esCO(truncateToCents(num), 2, false);
}

/** Fechas dd/mm/aaaa, de yyyy-mm-dd (o de un yyyy-mm-ddThh:mm del servidor). */
export function formatDate(value: string | null | undefined): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(value ?? '');
  return match ? `${match[3]}/${match[2]}/${match[1]}` : (value ?? '');
}
