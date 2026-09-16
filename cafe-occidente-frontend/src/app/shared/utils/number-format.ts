/** Formatea numeros al estilo colombiano (punto miles, coma decimal) para mostrar en pantalla.
 *  Solo afecta lo que se ve: el valor real que se envia al backend nunca pasa por aqui. */
export function formatDisplayNumber(value: string | number, decimals: 'currency' | 'count'): string {
  const num = typeof value === 'number' ? value : parseFloat(value);
  if (value === '' || value === null || value === undefined || Number.isNaN(num)) {
    return typeof value === 'string' ? value : '';
  }
  return decimals === 'currency'
    ? num.toLocaleString('es-CO', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
    : num.toLocaleString('es-CO');
}
