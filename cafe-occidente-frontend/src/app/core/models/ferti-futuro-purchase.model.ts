/** Siguiente factura del rango DIAN (compartido con los otros 4 módulos de compra). */
export interface FertiFuturoNextInvoiceNumber {
  readonly invoiceNumber: number;
  readonly prefix: string;
  readonly warning: string | null;
}

/** Resultado de la cascada completa de cálculo (mismo shape que al guardar, sin persistir). */
export interface FertiFuturoPurchaseCalculation {
  readonly tareKg: number;
  readonly healthyPercentage: number;
  readonly defectivePercentage: number;
  readonly qualityIncrementAmount: number;
  readonly unitPrice: number;
  readonly grossValue: number;
  readonly inventoryValue: number;
  readonly associateContribution: number;
  readonly cooperativeDiscount: number;
  readonly withholding: number;
  readonly netToPay: number;
}

/** Campos de entrada manual de FERTIFUTURO. Sin cellphone (el formulario real no lo captura) y sin
 *  Peso Tot Alm/Pasilla (a diferencia de Seco, Peso Alm Sana/Peso Alm Defec son inputs directos). */
export interface FertiFuturoPurchaseRequest {
  readonly agencyId: number;
  readonly fundId: number;
  readonly invoiceNumber: number;
  readonly specialType: string;
  readonly idNumber: string;
  readonly firstName: string;
  readonly lastName: string;
  readonly growerType: string;
  readonly address: string;
  readonly futurePurchaseId: number | null;
  readonly sacos: number;
  readonly netKg: number;
  readonly grossKg: number;
  readonly healthyStoredWeight: number;
  readonly defectiveStoredWeight: number;
  readonly penalty: number;
  readonly withholdingExempt: boolean;
  readonly freightDiscount: number;
  readonly otherDiscounts: number;
  readonly paymentMethod: string;
  readonly checkNumber: string | null;
}

/** Respuesta del backend: incluye todos los campos calculados de la cascada VBA. */
export interface FertiFuturoPurchaseResponse {
  readonly id: number;
  readonly purchaseDate: string;
  readonly invoiceNumber: number;
  readonly agencyId: number;
  readonly agencyName: string;
  readonly fundId: number;
  readonly fundCode: string;
  readonly specialType: string;
  readonly productCode: string;
  readonly announcementNumber: string;
  readonly announcementDate: string;
  readonly futurePurchaseId: number | null;
  readonly idNumber: string;
  readonly firstName: string;
  readonly lastName: string;
  readonly growerType: string;
  readonly address: string;
  readonly sacos: number;
  readonly netKg: number;
  readonly grossKg: number;
  readonly tareKg: number;
  readonly healthyStoredWeight: number;
  readonly healthyPercentage: number;
  readonly defectiveStoredWeight: number;
  readonly defectivePercentage: number;
  readonly healthyUnitPrice: number;
  readonly defectiveUnitPrice: number;
  readonly bonus: number;
  readonly penalty: number;
  readonly costs: number;
  readonly qualityIncrementRate: number;
  readonly qualityIncrementAmount: number;
  readonly unitPrice: number;
  readonly grossValue: number;
  readonly inventoryValue: number;
  readonly associateContribution: number;
  readonly cooperativeDiscount: number;
  readonly withholdingExempt: boolean;
  readonly withholding: number;
  readonly freightDiscount: number;
  readonly otherDiscounts: number;
  readonly netToPay: number;
  readonly paymentMethod: string;
  readonly checkNumber: string | null;
  readonly purchasePoint: string;
  readonly prefix: string;
  readonly dianResolution: string;
  readonly resolutionDate: string;
  readonly resolutionFrom: number;
  readonly resolutionTo: number;
  readonly validity: number;
}
