import { LedgerEntryRequest, PackagingEntryRequest } from '../../core/models/supplies.model';
import { parseDisplayNumber } from '../../shared/utils/number-format';
import { SuppliesEntryContent, SuppliesScreen } from './supplies-entry.model';

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

/** Campo que decide el codigo del Id (RF §2.1), o null si el Id no depende de lo que se elija. */
export function idSource(screen: SuppliesScreen): 'fundCode' | 'packagingType' | null {
  return screen.idFrom ?? null;
}

const FOUR_DIGITS = /^\d{4}$/;

/** Primer problema antes de guardar (en el orden de la pantalla): un obligatorio vacio o un cheque que no es de 4 digitos. */
export function invalidField(
  screen: SuppliesScreen,
  values: Record<string, string>,
  messages: SuppliesEntryContent['messages'],
): string | null {
  const missing = screen.fields.find((f) => f.required && !(values[f.key] ?? '').trim());
  if (missing) {
    return messages.required.replace('{label}', missing.label);
  }
  const check = (values['checkNumber'] ?? '').trim();
  return check && !FOUR_DIGITS.test(check) ? messages.checkDigits : null;
}

const orNull = (value: string | undefined) => (value ?? '').trim() || null;

/**
 * Arma el cuerpo del POST sin Id (lo genera el servidor). La fecha solo se manda donde se puede editar (Ingresar
 * Suministros); lo fijo (RP, EFECTIVO, Fiel de Caja Menor) igual lo impone el backend.
 */
export function buildRequest(
  screen: SuppliesScreen,
  values: Record<string, string>,
): LedgerEntryRequest | PackagingEntryRequest {
  const common = { idNumber: orNull(values['idNumber']), detail: orNull(values['detail']) };
  if (screen.kind === 'packaging') {
    return { ...common, packagingType: values['packagingType'], quantity: parseDisplayNumber(values['quantity']) };
  }
  const editableDate = screen.fields.some((f) => f.key === 'entryDate' && !f.readonly);
  return {
    ...common,
    entryDate: editableDate ? orNull(values['entryDate']) : null,
    fundCode: orNull(values['fundCode']),
    amount: parseDisplayNumber(values['amount']),
    paymentMethod: orNull(values['paymentMethod']),
    checkNumber: orNull(values['checkNumber']),
  };
}
