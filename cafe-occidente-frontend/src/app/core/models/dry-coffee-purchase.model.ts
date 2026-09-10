export interface Agency {
  readonly id: number;
  readonly name: string;
}

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
  readonly agencyId: number;
  readonly fundId: number;
}

/** Campos de entrada manual del formulario Compras Café Seco (Pr_AlmDefec no va: sale de ControlRecord). */
export interface DryCoffeePurchaseRequest {
  readonly agencyId: number;
  readonly fundId: number;
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
  readonly municipalityName: string;
}
