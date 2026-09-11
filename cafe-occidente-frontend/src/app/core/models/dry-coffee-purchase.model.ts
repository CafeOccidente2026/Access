export interface Fund {
  readonly id: number;
  readonly code: string;
  readonly name: string;
}

export interface Announcement {
  readonly id: number;
  readonly announcementNumber: string;
  readonly announcementDate: string;
  readonly basePriceLoad: number;
  readonly defectiveUnitPrice: number;
  readonly healthyUnitPrice: number;
  readonly bonus: number;
  readonly costs: number;
  readonly agencyId: number;
  readonly fundId: number;
}

/** Autocompletado al confirmar "Especial" (paso 5): Cod Prod + datos del anuncio vigente. */
export interface SpecialInfo {
  readonly productCode: string;
  readonly announcementNumber: string;
  readonly announcementDate: string;
  readonly basePriceLoad: number;
  readonly defectiveUnitPrice: number;
  readonly healthyUnitPrice: number;
  readonly bonus: number;
  readonly costs: number;
}

/** Siguiente factura a reservar al confirmar "Fondo" (paso 3). */
export interface NextInvoiceNumber {
  readonly invoiceNumber: number;
  readonly prefix: string;
  readonly warning: string | null;
}

/** Porcentajes calculados en los pasos "Peso Tot Alm"/"Peso Tot Pasilla"/"Peso Alm Sana". */
export interface QualityPercentages {
  readonly wastePercentage: number | null;
  readonly defectivePercentage: number | null;
  readonly healthyPercentage: number | null;
}

/** Resultado de la cascada completa de cálculo (mismo shape que al guardar, sin persistir). */
export interface DryCoffeePurchaseCalculation {
  readonly basePriceLoad: number;
  readonly netKg: number;
  readonly wastePercentage: number;
  readonly defectivePercentage: number;
  readonly healthyPercentage: number;
  readonly unitPrice: number;
  readonly grossValue: number;
  readonly inventoryValue: number;
  readonly associateContribution: number;
  readonly cooperativeDiscount: number;
  readonly withholding: number;
  readonly netToPay: number;
}

/** Campos de entrada manual del formulario Compras Café Seco (Pr_AlmDefec no va: lo trae el anuncio). */
export interface DryCoffeePurchaseRequest {
  readonly agencyId: number;
  readonly fundId: number;
  readonly invoiceNumber: number;
  readonly specialType: string;
  readonly idNumber: string;
  readonly firstName: string;
  readonly lastName: string;
  readonly growerType: string;
  readonly address: string;
  readonly cellphone: string;
  readonly bagsCount: number;
  readonly grossKg: number;
  readonly tareKg: number;
  readonly totalStoredWeight: number;
  readonly defectiveStoredWeight: number;
  readonly healthyStoredWeight: number;
  readonly healthyUnitPrice: number;
  readonly bonus: number;
  readonly penalty: number;
  readonly costs: number;
  readonly withholdingExempt: boolean;
  readonly freightDiscount: number;
  readonly otherDiscounts: number;
  readonly paymentMethod: string;
  readonly checkNumber: string | null;
}

/** Respuesta del backend: incluye todos los campos calculados de la cascada VBA. */
export interface DryCoffeePurchaseResponse {
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
  readonly netKg: number;
  readonly totalStoredWeight: number;
  readonly wastePercentage: number;
  readonly defectiveStoredWeight: number;
  readonly defectivePercentage: number;
  readonly healthyStoredWeight: number;
  readonly healthyPercentage: number;
  readonly healthyUnitPrice: number;
  readonly defectiveUnitPrice: number;
  readonly bonus: number;
  readonly penalty: number;
  readonly costs: number;
  readonly unitPrice: number;
  readonly grossValue: number;
  readonly inventoryValue: number;
  readonly associateContribution: number;
  readonly cooperativeDiscount: number;
  readonly withholding: number;
  readonly freightDiscount: number;
  readonly otherDiscounts: number;
  readonly netToPay: number;
}
