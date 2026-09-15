/** Autocompletado al iniciar el formulario (Especial "CV" / Fondo "RP" fijos): Cod Prod + anuncio vigente. */
export interface GreenAnnouncementInfo {
  readonly fundId: number;
  readonly productCode: string;
  readonly announcementNumber: string;
  readonly announcementDate: string;
  readonly basePriceLoad: number;
  readonly defectiveUnitPrice: number;
  readonly healthyUnitPrice: number;
  readonly bonus: number;
  readonly costs: number;
}

/** Factura: "Para asignar # factura verdes" (siguiente consecutivo propio de VERDES). */
export interface GreenNextInvoiceNumber {
  readonly invoiceNumber: number;
  readonly prefix: string;
  readonly warning: string | null;
}

/** Resultado de la cascada completa de calculo (mismo shape que al guardar, sin persistir). */
export interface GreenCoffeePurchaseCalculation {
  readonly basePriceLoad: number;
  readonly greenKg: number;
  readonly netKg: number;
  readonly unitPrice: number;
  readonly grossValue: number;
  readonly inventoryValue: number;
  readonly associateContribution: number;
  readonly cooperativeDiscount: number;
  readonly withholding: number;
  readonly netToPay: number;
}

/** Campos de entrada manual del formulario Compras Cafe Verde (VERDES). */
export interface GreenCoffeePurchaseRequest {
  readonly agencyId: number;
  readonly fundId: number;
  readonly invoiceNumber: number;
  readonly idNumber: string;
  readonly firstName: string;
  readonly lastName: string;
  readonly growerType: string;
  readonly address: string;
  readonly cellphone: string;
  readonly bagsCount: number;
  readonly grossKg: number;
  readonly tareKg: number;
  readonly healthyUnitPrice: number;
  readonly bonus: number;
  readonly costs: number;
  readonly penalty: number;
  readonly compKgPrice: number;
  readonly withholdingExempt: boolean;
  readonly shrinkageDiscount: number;
  readonly otherDiscounts: number;
  readonly paymentMethod: string;
  readonly checkNumber: string | null;
}

/** Respuesta del backend: incluye todos los campos calculados de la cascada VBA. */
export interface GreenCoffeePurchaseResponse {
  readonly id: number;
  readonly purchaseDate: string;
  readonly invoiceNumber: number;
  readonly agencyName: string;
  readonly fundCode: string;
  readonly specialType: string;
  readonly productCode: string;
  readonly announcementNumber: string;
  readonly announcementDate: string;
  readonly basePriceLoad: number;
  readonly idNumber: string;
  readonly firstName: string;
  readonly lastName: string;
  readonly growerType: string;
  readonly bagsCount: number;
  readonly grossKg: number;
  readonly tareKg: number;
  readonly greenKg: number;
  readonly netKg: number;
  readonly healthyUnitPrice: number;
  readonly defectiveUnitPrice: number;
  readonly bonus: number;
  readonly costs: number;
  readonly penalty: number;
  readonly compKgPrice: number;
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
