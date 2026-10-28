import { QualityPercentages } from '../../core/models/dry-coffee-purchase.model';
import { parseDisplayNumber } from '../../shared/utils/number-format';

export type SampleWeightKey = 'totalStoredWeight' | 'totalHuskWeight';

/** Q2: con Peso Tot Alm se pide Porc Merma; con Peso Pasilla, ademas Alm Sana y Factor (ambos pesos). */
export function qualityRequestArgs(model: Record<string, string>, key: SampleWeightKey): [number?, number?] {
  const total = parseDisplayNumber(model['totalStoredWeight']);
  return key === 'totalStoredWeight' ? [total] : [total, parseDisplayNumber(model['totalHuskWeight'])];
}

/** Escribe en el formulario (y bloquea) los campos que llena la respuesta de /quality-percentages. */
export function applyQualityPercentages(
  model: Record<string, string>,
  locked: Set<string>,
  key: SampleWeightKey,
  q: QualityPercentages,
): void {
  const shown: Record<string, number | null | undefined> =
    key === 'totalStoredWeight'
      ? { wastePercentage: q.wastePercentage }
      : { huskPercentage: q.defectivePercentage, healthyStoredWeight: q.healthyStoredWeight, factor: q.healthyPercentage };
  for (const [field, value] of Object.entries(shown)) {
    model[field] = value != null ? String(value) : '';
    locked.add(field);
  }
}
