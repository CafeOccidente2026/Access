import { DryCoffeePurchaseResponse } from './dry-coffee-purchase.model';
import { GreenCoffeePurchaseResponse } from './green-coffee-purchase.model';
import { HuskPurchaseResponse } from './husk-purchase.model';
import { PurchasePayment } from './payment-method.model';
import { AnnulmentModule } from './purchase-annulment.model';

/** Una compra en "Consultar Compras" (PurchaseQueryDetail del backend, RF-26). */
export type PurchaseQueryDetail =
  | QueryDetail<'DRY', DryCoffeePurchaseResponse>
  | QueryDetail<'GREEN', GreenCoffeePurchaseResponse>
  | QueryDetail<'HUSK', HuskPurchaseResponse>;

interface QueryDetail<M extends AnnulmentModule, P> {
  readonly module: M;
  /** La respuesta de su modulo, con todos los campos de la compra. */
  readonly purchase: P;
  readonly status: 'VALIDA' | 'ANULADA';
  /** Formas de pago guardadas; null en compras sin desglose. */
  readonly payment: PurchasePayment | null;
  readonly remissions: readonly string[];
}
