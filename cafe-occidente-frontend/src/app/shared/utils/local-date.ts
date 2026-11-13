/**
 * Hoy en la hora local del equipo, como yyyy-mm-dd. No usar toISOString().slice(0, 10): esa es la fecha en UTC
 * y despues de las 7 p. m. de Colombia ya da el dia siguiente.
 */
export function localToday(now = new Date()): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}
