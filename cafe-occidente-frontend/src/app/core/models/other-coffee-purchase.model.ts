/** Autocompletado al confirmar "Especial" (paso 5): Cod Prod + datos del anuncio vigente. */
export interface OtherCoffeeSpecialInfo {
  readonly productCode: string;
  readonly announcementNumber: string;
  readonly announcementDate: string;
  readonly basePriceLoad: number;
  readonly defectiveUnitPrice: number;
  readonly healthyUnitPrice: number;
  readonly bonus: number;
  readonly costs: number;
}

/** Siguiente factura a reservar al confirmar "Fondo" (paso 3) - rango compartido con los otros 4 módulos. */
export interface OtherCoffeeNextInvoiceNumber {
  readonly invoiceNumber: number;
  readonly prefix: string;
  readonly warning: string | null;
}

/** Porcentajes calculados en los pasos "Peso Tot Alm"/"Peso Tot Pasilla"/"Peso Alm Sana". */
export interface OtherCoffeeQualityPercentages {
  readonly wastePercentage: number | null;
  readonly defectivePercentage: number | null;
  readonly healthyPercentage: number | null;
}

/** Resultado de la cascada completa de cálculo (mismo shape que al guardar, sin persistir). */
export interface OtherCoffeePurchaseCalculation {
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

/** Campos de entrada manual del formulario Cafés Otros (COMPRASESP). Sin Pr_AlmDefec: a diferencia
 *  de Café Seco, este módulo no tiene el término de la almendra defectuosa en la fórmula. */
export interface OtherCoffeePurchaseRequest {
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
export interface OtherCoffeePurchaseResponse {
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
  readonly address: string;
  readonly cellphone: string;
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
