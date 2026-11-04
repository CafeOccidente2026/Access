/** Las 4 formas de la cascada de Access, en su orden: FPef, FPch, FPtx, FPdat. */
export type PaymentKey = 'cash' | 'check' | 'transfer' | 'card';

export interface PaymentMethodEntry {
  readonly key: PaymentKey;
  readonly label: string;
}

/** Panel "FORMAS DE PAGO" de los formularios de compra (textos del JSON de cada formulario). */
export interface PaymentPanelDefinition {
  readonly heading: string;
  readonly methods: PaymentMethodEntry[];
  readonly checkNumberLabel: string;
  readonly totalLabel: string;
  readonly unbalancedMessage: string;
  readonly checkNumberMessage: string;
}

/** Lo que viaja al backend (PurchasePaymentRequest): montos en pesos enteros, como el Neto a Pagar. */
export interface PurchasePayment {
  readonly cashAmount: number;
  readonly checkAmount: number;
  readonly transferAmount: number;
  readonly cardAmount: number;
  readonly checkNumber: string | null;
}
