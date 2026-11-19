import { FormFieldDefinition, PurchaseFormContent } from '../../core/models';

/** Lo que devuelve el servidor al calcular la compra y se muestra en la liquidación. */
export interface SettlementCalc {
  readonly unitPrice: number;
  readonly withholding: number;
  readonly grossValue: number;
  readonly associateContribution: number;
  readonly cooperativeDiscount: number;
  readonly netToPay: number;
}

/** Tipo del vendedor (S o C) tal como quedó en el campo Tipo; vacío si todavía no se buscó la cédula. */
export function growerTypeOf(idType: string | undefined): string {
  return (idType ?? '').trim().toUpperCase();
}

/** Vr. Kilo, Vr. Bruto, Aporte Socio (o Descuento Coop si el vendedor es tipo C), Retefuente y Neto a Pagar,
 *  como en Café Seco. */
export function settlementValues(c: SettlementCalc | null, growerType: string): Record<string, string | number> {
  if (!c) {
    return { kgPrice: '', grossValue: '', contribution: '', withholding: '', netToPay: '' };
  }
  return {
    kgPrice: c.unitPrice,
    grossValue: c.grossValue,
    contribution: growerType === 'C' ? c.cooperativeDiscount : c.associateContribution,
    withholding: c.withholding,
    netToPay: c.netToPay,
  };
}

/** La etiqueta del aporte dice "Descuento Coop" para un vendedor tipo C. */
export function settlementLabel(field: FormFieldDefinition, growerType: string, cooperativeLabel?: string): string {
  return field.key === 'contribution' && growerType === 'C' ? (cooperativeLabel ?? field.label) : field.label;
}

/**
 * Bloque de liquidación compartido por Seco, Verde, Pasilla, Anunciadas y Cupos (prueba del 19-nov): fila 1 Vr. Kilo,
 * Vr. Bruto y Neto a Pagar; fila 2 Aporte Socio (o Descuento Coop) y Retefuente; Otros Desctos a la derecha.
 * Las casillas salen del JSON de cada pantalla; `settlement-layout.spec.ts` exige que sean las mismas de Seco.
 */
export function settlementRows(
  base: Pick<PurchaseFormContent, 'settlementFields' | 'additionalDiscountFields' | 'netToPayField'>,
  field: (key: string) => FormFieldDefinition,
): Pick<PurchaseFormContent, 'settlementFields' | 'additionalDiscountFields' | 'netToPayField'> {
  return {
    settlementFields: base.settlementFields?.map((f) => field(f.key)),
    additionalDiscountFields: base.additionalDiscountFields?.map((f) => field(f.key)),
    netToPayField: base.netToPayField && field(base.netToPayField.key),
  };
}

/** Las casillas de la liquidación, para el mapa de campos de cada pantalla. */
export function settlementBases(base: Pick<PurchaseFormContent, 'settlementFields' | 'additionalDiscountFields' | 'netToPayField'>) {
  return [...(base.settlementFields ?? []), ...(base.additionalDiscountFields ?? []), ...(base.netToPayField ? [base.netToPayField] : [])];
}
