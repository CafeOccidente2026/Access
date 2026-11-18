import { FormFieldDefinition } from '../../core/models';

/** Lo que devuelve el servidor al calcular la compra y se muestra en la liquidación. */
export interface SettlementCalc {
  readonly grossValue: number;
  readonly associateContribution: number;
  readonly cooperativeDiscount: number;
  readonly netToPay: number;
}

/** Tipo del vendedor (S o C) tal como quedó en el campo Tipo; vacío si todavía no se buscó la cédula. */
export function growerTypeOf(idType: string | undefined): string {
  return (idType ?? '').trim().toUpperCase();
}

/** Vr. Bruto, Aporte Socio (o Descuento Coop si el vendedor es tipo C) y Neto a Pagar, como en Café Seco. */
export function settlementValues(c: SettlementCalc | null, growerType: string): Record<string, string | number> {
  if (!c) {
    return { grossValue: '', contribution: '', netToPay: '' };
  }
  return {
    grossValue: c.grossValue,
    contribution: growerType === 'C' ? c.cooperativeDiscount : c.associateContribution,
    netToPay: c.netToPay,
  };
}

/** La etiqueta del aporte dice "Descuento Coop" para un vendedor tipo C. */
export function settlementLabel(field: FormFieldDefinition, growerType: string, cooperativeLabel?: string): string {
  return field.key === 'contribution' && growerType === 'C' ? (cooperativeLabel ?? field.label) : field.label;
}
