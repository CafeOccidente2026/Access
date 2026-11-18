import { FormFieldDefinition } from '../../core/models';
import { isSequentialFieldEnabled } from '../../shared/utils/sequential-gate';
import { SettlementCalc, growerTypeOf, settlementLabel, settlementValues } from './settlement';

/** Lo que necesita un formulario de compra (Verde, Pasilla) para armar sus campos. */
export interface FieldStateContext {
  readonly readonlyKeys: readonly string[];
  readonly focusOrder: readonly string[];
  readonly locked: ReadonlySet<string>;
  readonly model: Record<string, string>;
  readonly computed: Record<string, string | number>;
  /** Se reusa el campo anterior si no cambió, para no repintar el control. */
  readonly cache: Map<string, FormFieldDefinition>;
  /** Cálculo del servidor: llena Vr. Bruto, Aporte Socio / Descuento Coop y Neto a Pagar. */
  readonly calc: SettlementCalc | null;
  readonly cooperativeLabel?: string;
}

/**
 * Arma cada campo por su key. Calculado: valor del servidor y de solo lectura; confirmado: bloqueado con lo digitado;
 * campo al que todavía no le toca su turno (focusOrder): bloqueado.
 */
export function fieldRows(bases: ReadonlyMap<string, FormFieldDefinition>, ctx: FieldStateContext) {
  const growerType = growerTypeOf(ctx.model['idType']);
  const computed = { ...ctx.computed, ...settlementValues(ctx.calc, growerType) };
  const field = (key: string): FormFieldDefinition => {
    const base = bases.get(key)!;
    const patch: { readonly?: boolean; value?: string | number; label: string } = {
      label: settlementLabel(base, growerType, ctx.cooperativeLabel),
    };
    if (ctx.readonlyKeys.includes(key)) {
      patch.readonly = true;
      patch.value = computed[key] ?? ctx.model[key] ?? '';
    } else if (ctx.locked.has(key)) {
      patch.readonly = true;
      patch.value = ctx.model[key] ?? '';
    } else if (!isSequentialFieldEnabled(key, ctx.focusOrder, ctx.locked)) {
      patch.readonly = true;
    }
    const next: FormFieldDefinition = { ...base, ...patch };
    const prev = ctx.cache.get(key);
    if (prev && prev.value === next.value && prev.readonly === next.readonly && prev.label === next.label) {
      return prev;
    }
    ctx.cache.set(key, next);
    return next;
  };
  return { field, row: (fields?: FormFieldDefinition[]) => fields?.map((f) => field(f.key)) };
}
