/** Motivo de una anulacion (26-nov): obligatorio, de al menos 10 caracteres sin los espacios de los extremos. */
export const MIN_REASON_LENGTH = 10;

export function validReason(reason: string): boolean {
  return reason.trim().length >= MIN_REASON_LENGTH;
}
