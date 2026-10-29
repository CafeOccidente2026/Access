import { PurchasePayment } from './payment-method.model';

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
  /** HU-23 CA-2: muestra fija del Registro de Control; null mientras falten los pesos de verde. */
  readonly sample: GreenCoffeeSample | null;
  readonly sampleReady: boolean;
}

/** Muestra fija de cafe verde (Registro de Control) y sus porcentajes, calculados por el servidor. */
export interface GreenCoffeeSample {
  readonly totalStoredWeight: number;
  readonly wastePercentage: number;
  readonly huskWeight: number;
  readonly huskPercentage: number;
  readonly healthyStoredWeight: number;
  readonly factor: number;
}

/** Proxima factura del punto (Q7: consecutivo compartido con seco y pasilla); la asigna el servidor al guardar. */
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
/** Solo lo que digita el cajero: precios, costos, factura y vendedor los pone el servidor (6b). */
export interface GreenCoffeePurchaseRequest {
  readonly fundId: number;
  readonly idNumber: string;
  readonly address: string;
  readonly cellphone: string;
  readonly bagsCount: number;
  readonly grossKg: number;
  readonly tareKg: number;
  readonly penalty: number;
  readonly compKgPrice: number;
  readonly shrinkageDiscount: number;
  readonly otherDiscounts: number;
  /** FORMAS DE PAGO cuadrado; null en preview (todavia no se lleno). */
  readonly payment: PurchasePayment | null;
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
  readonly address: string;
  readonly cellphone: string;
  readonly paymentMethod: string;
  readonly checkNumber: string | null;
  /** Muestra fija del Registro de Control guardada con la compra; null en las anteriores a la V15. */
  readonly sample: GreenCoffeeSample | null;
  /** Encabezado de "Factura verdes" (Registro de Control activo). */
  readonly purchasePoint: string;
  readonly prefix: string;
  readonly dianResolution: string;
  readonly resolutionDate: string;
  readonly resolutionFrom: number;
  readonly resolutionTo: number;
  readonly validity: number;
}

/** Muestra fija de cafe verde (GreenCoffeeSample del backend). */
export interface GreenCoffeeSample {
  readonly totalStoredWeight: number;
  readonly wastePercentage: number;
  readonly huskWeight: number;
  readonly huskPercentage: number;
  readonly healthyStoredWeight: number;
  readonly factor: number;
}
