import { PaymentKey, PurchasePayment } from '../../../core/models';

export const PAYMENT_ORDER: readonly PaymentKey[] = ['cash', 'check', 'transfer', 'card'];

export interface PaymentState {
  readonly amounts: Readonly<Record<PaymentKey, number>>;
  readonly checkNumber: string;
}

export interface CascadeStep {
  readonly state: PaymentState;
  /** Campo al que pasa el foco: otra forma, el numero de cheque, o null si quedo cuadrado. */
  readonly next: PaymentKey | 'checkNumber' | null;
  /** "FORMA DE PAGO DESCUADRADA, REVISE": todo vuelve a 0 y el foco a Efectivo. */
  readonly unbalanced: boolean;
}

const total = (amounts: Readonly<Record<PaymentKey, number>>) => PAYMENT_ORDER.reduce((sum, k) => sum + amounts[k], 0);

/** OtrosDescuentos_LostFocus: Texto197 (FPef) = Neto_a_Pagar, el resto en 0. */
export function initialPayment(netToPay: number): PaymentState {
  return { amounts: { cash: netToPay, check: 0, transfer: 0, card: 0 }, checkNumber: '' };
}

/**
 * Texto197/Texto199/NumCheque/Texto201/Texto203_LostFocus de Form_COMPRAS.bas: al confirmar una forma,
 * las que siguen se recalculan (quedan en 0); si la suma cuadra con el Neto termina; si se pasa (o ya no
 * quedan formas) queda descuadrada; si falta, la siguiente forma se llena con lo que falta. Despues de
 * Cheque se pide el numero de cheque, y la Transferencia ya queda con lo que falta.
 */
export function commitPayment(netToPay: number, state: PaymentState, field: PaymentKey | 'checkNumber'): CascadeStep {
  const index = PAYMENT_ORDER.indexOf(field === 'checkNumber' ? 'check' : field);
  const amounts = { ...state.amounts };
  PAYMENT_ORDER.slice(index + 1).forEach((k) => (amounts[k] = 0));
  const paid = total(amounts);
  const following = PAYMENT_ORDER[index + 1];
  if (paid > netToPay || (paid < netToPay && !following)) {
    return { state: { amounts: { cash: 0, check: 0, transfer: 0, card: 0 }, checkNumber: '' }, next: 'cash', unbalanced: true };
  }
  if (paid < netToPay) {
    amounts[following] = netToPay - paid;
  }
  let next: CascadeStep['next'] = paid === netToPay ? null : following;
  if (field === 'check' && amounts.check > 0) {
    next = 'checkNumber';
  }
  return { state: { ...state, amounts }, next, unbalanced: false };
}

/** Formas que se ven (.Visible de FPch/FPtx/FPdat): Efectivo siempre, y hasta la ultima con monto. */
export function visibleMethods(state: PaymentState): PaymentKey[] {
  const last = Math.max(0, ...PAYMENT_ORDER.map((k, i) => (state.amounts[k] > 0 ? i : 0)));
  return PAYMENT_ORDER.slice(0, last + 1);
}

/** El pago que se puede enviar: cuadrado con el Neto y, si hay cheque, con su numero. */
export function readyPayment(netToPay: number, state: PaymentState): PurchasePayment | null {
  const { amounts, checkNumber } = state;
  if (total(amounts) !== netToPay || PAYMENT_ORDER.some((k) => amounts[k] < 0)) {
    return null;
  }
  // Mismo criterio que el backend: 1 a 9 digitos y distinto de 0 (Caja.Cheque es numerico).
  if (amounts.check > 0 && !(/^\d{1,9}$/.test(checkNumber.trim()) && Number(checkNumber) > 0)) {
    return null;
  }
  return {
    cashAmount: amounts.cash,
    checkAmount: amounts.check,
    transferAmount: amounts.transfer,
    cardAmount: amounts.card,
    checkNumber: amounts.check > 0 ? checkNumber.trim() : null,
  };
}
