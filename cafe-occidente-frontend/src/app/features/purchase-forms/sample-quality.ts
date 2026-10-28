import { QualityPercentages } from '../../core/models/dry-coffee-purchase.model';
import { DryCoffeePurchaseService } from '../../core/services/dry-coffee-purchase.service';
import { parseDisplayNumber } from '../../shared/utils/number-format';

export type SampleWeightKey = 'totalStoredWeight' | 'totalHuskWeight';

/**
 * Q2: Peso Tot Alm da Porc Merma; Peso Pasilla da Porc Kg Pas y, con el total, Peso Alm Sana y Factor,
 * que calcula el servidor. Escribe esos campos en el formulario, los bloquea y avisa con `done`.
 */
export function loadSampleQuality(
  service: DryCoffeePurchaseService,
  model: Record<string, string>,
  locked: Set<string>,
  key: SampleWeightKey,
  done: () => void,
): void {
  const total = parseDisplayNumber(model['totalStoredWeight']);
  const args: [number?, number?] = key === 'totalStoredWeight' ? [total] : [total, parseDisplayNumber(model['totalHuskWeight'])];
  service.qualityPercentages(...args).subscribe((q) => {
    applyQualityPercentages(model, locked, key, q);
    done();
  });
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
