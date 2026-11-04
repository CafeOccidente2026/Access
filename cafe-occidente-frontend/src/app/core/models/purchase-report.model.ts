/**
 * Una compra en los reportes de Compras (PurchaseReportRow del backend): seco, verde y pasilla. Lo que
 * no aplica viene en null: greenKg solo en verde; factor es la columna Porc (Factor, o % de almendra
 * en pasilla). Una anulada trae sus valores en 0 y paymentMethod "ANULADA".
 */
export interface PurchaseReportRow {
  readonly module: 'DRY' | 'GREEN' | 'HUSK';
  readonly agencyName: string;
  readonly announcementNumber: string;
  readonly specialType: string;
  readonly productCode: string;
  readonly productName: string | null;
  readonly purchaseDate: string;
  readonly fundCode: string;
  readonly prefix: string | null;
  readonly invoiceNumber: number;
  readonly idNumber: string;
  readonly firstName: string;
  readonly lastName: string | null;
  readonly greenKg: number | null;
  readonly netKg: number;
  readonly factor: number | null;
  readonly unitPrice: number;
  readonly grossValue: number;
  readonly inventoryValue: number;
  readonly associateContribution: number;
  readonly cooperativeDiscount: number;
  readonly freightDiscount: number;
  readonly withholding: number;
  readonly otherDiscounts: number;
  readonly netToPay: number;
  readonly paymentMethod: string;
  readonly status: 'VALIDA' | 'ANULADA';
}
