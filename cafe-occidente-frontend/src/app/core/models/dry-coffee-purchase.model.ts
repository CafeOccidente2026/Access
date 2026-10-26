import { PurchasePayment } from './payment-method.model';

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
  readonly specialType: string;
}

/** "Actualizar Anuncio con Factor" (RN-08.2, RN-08.3): un precio para todos los tipos de cafe del fondo. */
export interface AnnouncementRequest {
  readonly fundId: number;
  readonly basePriceLoad: number;
}

/** Fila del listado "Actualizar" (HU-11): el tipo de cafe con su anuncio vigente (null si no tiene). */
export interface AnnouncementType {
  readonly specialType: string;
  readonly basePriceLoad: number | null;
  readonly specialSurcharge: number | null;
  readonly defectiveUnitPrice: number | null;
  readonly announcementDate: string | null;
}

/** Guardar un solo tipo desde el listado (RN-08.6); sin precio se usa el vigente del tipo. */
export interface AnnouncementTypeRequest {
  readonly fundId: number;
  readonly specialType: string;
  readonly basePriceLoad: number | null;
  readonly specialSurcharge: number;
  readonly defectiveUnitPrice: number;
}

/** Los dos campos que llena el admin en "Actualizar Anuncio Pasilla" (Fondo/Especial van fijos). */
export interface HuskAnnouncementRequest {
  readonly basePriceLoad: number;
  readonly pointPrice: number;
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
  readonly freightDiscount: number;
  readonly otherDiscounts: number;
  /** FORMAS DE PAGO cuadrado; null en preview (todavia no se lleno). */
  readonly payment: PurchasePayment | null;
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
  /** "Nom" del Cod_Prod (null si no vino en producto_migrar.csv). */
  readonly productName: string | null;
}
