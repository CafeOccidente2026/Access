import { WritableSignal } from '@angular/core';

import { parseDisplayNumber } from '../../shared/utils/number-format';

/** Los avisos de los pesos que se revisan al confirmarlos (texto en el JSON de cada pantalla). */
export interface WeightMessages {
  readonly grossNotPositive?: string;
  readonly tareNotBelowGross?: string;
  readonly almondNotPositive?: string;
  readonly almondOverSample?: string;
}

/** Lo que un formulario de compra expone para avisar y borrar un valor. */
export interface RetryHost {
  model: Record<string, string>;
  readonly errorMessage: WritableSignal<string | null>;
}

/**
 * Hallazgos #5 y #10 (prueba del 16-nov): Kilos Brutos, Destare y Peso Almendra se revisan al dar Enter o salir del
 * campo, no al final. Mismas reglas que el servidor (NetWeightRule y HuskPurchaseCalculator).
 */
export function weightProblem(key: string, model: Record<string, string>, sampleSize?: number | null): keyof WeightMessages | null {
  const value = parseDisplayNumber(model[key]);
  if (key === 'grossKg') {
    return value > 0 ? null : 'grossNotPositive';
  }
  if (key === 'tare') {
    return value < parseDisplayNumber(model['grossKg']) ? null : 'tareNotBelowGross';
  }
  if (key === 'almondWeight') {
    if (!(value > 0)) {
      return 'almondNotPositive';
    }
    return sampleSize != null && value > sampleSize ? 'almondOverSample' : null;
  }
  return null;
}

/** El valor rechazado se borra y se desbloquea, y se avisa. Devuelve el campo al que debe volver el foco. */
export function retryField(host: RetryHost, locked: Set<string>, key: string, message: string | null | undefined): string {
  host.model[key] = '';
  locked.delete(key);
  host.errorMessage.set(message ?? null);
  return key;
}

/** Revisa el peso recién confirmado; si está mal, lo borra y avisa. Devuelve el campo a reintentar o null. */
export function checkWeight(
  host: RetryHost,
  locked: Set<string>,
  key: string,
  messages: WeightMessages | undefined,
  sampleSize?: number | null,
): string | null {
  const problem = weightProblem(key, host.model, sampleSize);
  if (!problem) {
    return null;
  }
  const message = messages?.[problem]?.replace('{sample}', String(sampleSize ?? ''));
  return retryField(host, locked, key, message);
}
