import { LedgerEntryRequest, PackagingEntryRequest } from '../../core/models/supplies.model';
import { parseDisplayNumber } from '../../shared/utils/number-format';
import { SuppliesScreen } from './supplies-entry.model';

/** yyyy-MM-dd local (toISOString daria el dia UTC, que en Colombia de noche ya es mañana). */
export function isoToday(today = new Date()): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${today.getFullYear()}-${pad(today.getMonth() + 1)}-${pad(today.getDate())}`;
}

/** Valores iniciales: fijos del JSON (Fondo RP, EFECTIVO) y fecha de hoy donde Access tenia Date(). */
export function initialValues(screen: SuppliesScreen, today = isoToday()): Record<string, string> {
  const values: Record<string, string> = {};
  for (const f of screen.fields) {
    values[f.key] = f.value != null ? String(f.value) : f.defaultToday ? today : '';
  }
  return values;
}

/** Primer campo obligatorio vacio (en el orden de la pantalla), o null. */
export function missingField(screen: SuppliesScreen, values: Record<string, string>): string | null {
  return screen.fields.find((f) => f.required && !(values[f.key] ?? '').trim())?.label ?? null;
}

const orNull = (value: string | undefined) => (value ?? '').trim() || null;

/** Arma el cuerpo del POST. Lo fijo (fecha de hoy, RP, EFECTIVO/CHEQUE) igual lo impone el backend. */
export function buildRequest(
  screen: SuppliesScreen,
  values: Record<string, string>,
  agencyId: number | null,
): LedgerEntryRequest | PackagingEntryRequest {
  const common = {
    transactionId: values['transactionId'].trim(),
    agencyId,
    entryDate: orNull(values['entryDate']),
    idNumber: values['idNumber'],
    detail: orNull(values['detail']),
  };
  if (screen.kind === 'packaging') {
    return { ...common, packagingType: values['packagingType'], quantity: parseDisplayNumber(values['quantity']) };
  }
  const check = orNull(values['checkNumber']);
  return {
    ...common,
    fundCode: orNull(values['fundCode']),
    amount: parseDisplayNumber(values['amount']),
    paymentMethod: orNull(values['paymentMethod']),
    checkNumber: check ? parseDisplayNumber(check) : null,
  };
}
