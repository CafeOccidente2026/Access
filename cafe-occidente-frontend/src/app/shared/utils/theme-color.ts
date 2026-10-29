/**
 * Color de un token de styles.css (@theme) para los generadores de PDF, que no leen clases de
 * Tailwind. undefined si el token no esta cargado (por ejemplo en las pruebas): pdfmake usa su color.
 */
export function themeColor(token: string): string | undefined {
  return getComputedStyle(document.documentElement).getPropertyValue(token).trim() || undefined;
}
