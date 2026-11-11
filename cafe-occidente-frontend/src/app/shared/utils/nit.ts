import company from '../../../../public/assets/data/company.json';
import { formatThousands } from './number-format';

/** NIT con puntos de miles y guion antes del digito de verificacion (6C): 8912009868 -> 891.200.986-8. */
export function formatNit(nit: string): string {
  const digits = nit.replace(/\D/g, '');
  return digits.length < 2 ? nit : `${formatThousands(digits.slice(0, -1))}-${digits.slice(-1)}`;
}

/** NIT de la cooperativa: se guarda sin separadores en company.json y se formatea solo al mostrarlo. */
export function cooperativeNit(): string {
  return formatNit(company.nit);
}
