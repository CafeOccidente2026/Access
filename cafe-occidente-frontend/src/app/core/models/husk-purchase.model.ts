/** Autocompletado al iniciar el formulario (Especial "PASILLA" / Fondo "RP" fijos en la practica). */
export interface HuskAnnouncementInfo {
  readonly fundId: number;
  readonly productCode: string;
  readonly announcementNumber: string;
  readonly announcementDate: string;
  readonly basePriceDryLoad: number;
  readonly pointPrice: number;
  readonly costs: number;
}

/** Factura: "Para asignar # factura pasilla" (siguiente consecutivo propio de PASILLA). */
export interface HuskNextInvoiceNumber {
  readonly invoiceNumber: number;
  readonly prefix: string;
  readonly warning: string | null;
}

/** Resultado de la cascada completa de calculo (mismo shape que al guardar, sin persistir). */
export interface HuskPurchaseCalculation {
  readonly netKg: number;
  readonly almondPercentage: number;
  readonly unitPrice: number;
  readonly grossValue: number;
  readonly inventoryValue: number;
  readonly associateContribution: number;
  readonly cooperativeDiscount: number;
  readonly withholding: number;
  readonly netToPay: number;
}

/** Campos de entrada manual del formulario Compra Pasilla (PASILLA). */
export interface HuskPurchaseRequest {
  readonly agencyId: number;
  readonly fundId: number;
  readonly invoiceNumber: number;
  readonly idNumber: string;
  readonly firstName: string;
  readonly lastName: string;
  readonly growerType: string;
  readonly address: string;
  readonly cellphone: string;
  readonly almondWeight: number;
  readonly bagsCount: number;
  readonly grossKg: number;
  readonly tareKg: number;
  readonly pointPrice: number;
  readonly costs: number;
  readonly withholdingExempt: boolean;
  readonly shrinkageDiscount: number;
  readonly otherDiscounts: number;
  readonly paymentMethod: string;
  readonly checkNumber: string | null;
}

/** Respuesta del backend: incluye todos los campos calculados de la cascada VBA. */
export interface HuskPurchaseResponse {
  readonly id: number;
  readonly purchaseDate: string;
  readonly invoiceNumber: number;
  readonly agencyName: string;
  readonly fundCode: string;
  readonly specialType: string;
  readonly productCode: string;
  readonly announcementNumber: string;
  readonly announcementDate: string;
  readonly basePriceDryLoad: number;
  readonly idNumber: string;
  readonly firstName: string;
  readonly lastName: string;
  readonly growerType: string;
  readonly pointPrice: number;
  readonly costs: number;
  readonly almondWeight: number;
  readonly almondPercentage: number;
  readonly bagsCount: number;
  readonly grossKg: number;
  readonly tareKg: number;
  readonly netKg: number;
  readonly unitPrice: number;
  readonly grossValue: number;
  readonly inventoryValue: number;
  readonly associateContribution: number;
  readonly cooperativeDiscount: number;
  readonly withholding: number;
  readonly shrinkageDiscount: number;
  readonly otherDiscounts: number;
  readonly netToPay: number;
}
