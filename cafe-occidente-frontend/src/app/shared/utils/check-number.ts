/**
 * Numero de cheque (V32, 24-nov): de 4 a 15 digitos, solo numeros, con sus ceros y no todos ceros (0548 vale; 0000
 * no). Mismo criterio que el backend; lo usan el panel de pago de las compras y Suministros.
 */
export function validCheckNumber(value: string): boolean {
  const check = value.trim();
  return /^\d{4,15}$/.test(check) && !/^0+$/.test(check);
}
