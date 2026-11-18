import { FormFieldDefinition } from '../../core/models';
import { isSequentialFieldEnabled } from '../../shared/utils/sequential-gate';
import { SettlementCalc, growerTypeOf, settlementLabel, settlementValues } from './settlement';

/** Lo que el armado de campos lee del formulario de compra (Verde, Pasilla). */
export interface PurchaseFieldForm {
  readonly model: Record<string, string>;
  readonly locked: ReadonlySet<string>;
  /** Se reusa el campo anterior si no cambió, para no repintar el control. */
  readonly fieldCache: Map<string, FormFieldDefinition>;
  messages(): { readonly cooperativeDiscountLabel?: string } | undefined;
}

/**
 * Arma cada campo por su key. Calculado (readonlyKeys): valor del servidor y de solo lectura, con la liquidación
 * (Vr. Kilo, Vr. Bruto, Aporte Socio / Descuento Coop, Retefuente y Neto a Pagar); confirmado: bloqueado con lo
 * digitado; campo al que todavía no le toca su turno (focusOrder): bloqueado.
 */
export function fieldRows(
  bases: ReadonlyMap<string, FormFieldDefinition>,
  readonlyKeys: readonly string[],
  focusOrder: readonly string[],
  form: PurchaseFieldForm,
  values: Record<string, string | number>,
  calc: SettlementCalc | null,
) {
  const growerType = growerTypeOf(form.model['idType']);
  const computed = { ...values, ...settlementValues(calc, growerType) };
  const cooperativeLabel = form.messages()?.cooperativeDiscountLabel;
  const field = (key: string): FormFieldDefinition => {
    const base = bases.get(key)!;
    const patch: { readonly?: boolean; value?: string | number; label: string } = {
      label: settlementLabel(base, growerType, cooperativeLabel),
    };
    if (readonlyKeys.includes(key)) {
      patch.readonly = true;
      patch.value = computed[key] ?? form.model[key] ?? '';
    } else if (form.locked.has(key)) {
      patch.readonly = true;
      patch.value = form.model[key] ?? '';
    } else if (!isSequentialFieldEnabled(key, focusOrder, form.locked)) {
      patch.readonly = true;
    }
    const next: FormFieldDefinition = { ...base, ...patch };
    const prev = form.fieldCache.get(key);
    if (prev && prev.value === next.value && prev.readonly === next.readonly && prev.label === next.label) {
      return prev;
    }
    form.fieldCache.set(key, next);
    return next;
  };
  return { field, row: (fields?: FormFieldDefinition[]) => fields?.map((f) => field(f.key)) };
}
