/** Tokens de styles.css (@theme) que usan los generadores de PDF. */
export const PDF_COLOR_TOKENS = [
  '--color-annulled', '--color-print-placeholder', '--color-remission-number',
  '--color-report-amber', '--color-report-muted', '--color-report-teal',
] as const;

/** R3: en el Web Worker no hay DOM; los colores llegan resueltos desde la pantalla. */
let palette: Readonly<Record<string, string>> | null = null;

export function setThemePalette(colors: Readonly<Record<string, string>> | null): void {
  palette = colors;
}

/**
 * Color de un token de styles.css (@theme) para los generadores de PDF, que no leen clases de
 * Tailwind. undefined si el token no esta cargado (por ejemplo en las pruebas): pdfmake usa su color.
 */
export function themeColor(token: string): string | undefined {
  if (palette) {
    return palette[token] || undefined;
  }
  return getComputedStyle(document.documentElement).getPropertyValue(token).trim() || undefined;
}

/** Los colores de los tokens de PDF tal como estan en la pantalla, para mandarlos al Web Worker. */
export function pdfPalette(): Record<string, string> {
  return Object.fromEntries(PDF_COLOR_TOKENS.map((token) => [token, themeColor(token) ?? '']));
}
