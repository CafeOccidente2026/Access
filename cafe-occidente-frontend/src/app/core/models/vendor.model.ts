/** Alta de "Catalogar Terceros" (VendorCreateRequest del backend). */
export interface VendorCreateRequest {
  readonly legalPerson: boolean;
  readonly idNumber: string;
  readonly firstName: string | null;
  readonly secondName: string | null;
  readonly lastName: string | null;
  readonly secondLastName: string | null;
  readonly sex: string | null;
  readonly phone: string | null;
  readonly address: string | null;
  readonly postalCode: string | null;
  readonly email: string | null;
  readonly businessName: string | null;
  readonly classification: string | null;
}

/** Los 28 campos del formulario "Asociados" de Access. */
export interface Associate {
  readonly idNumber: string;
  readonly firstName: string;
  readonly secondName: string | null;
  readonly lastName: string | null;
  readonly secondLastName: string | null;
  readonly agencyName: string | null;
  readonly birthDate: string | null;
  readonly birthPlace: string | null;
  readonly maritalStatus: string | null;
  readonly coffeeIdCard: string | null;
  readonly address: string | null;
  readonly phone: string | null;
  readonly affiliationDate: string | null;
  readonly actNumber: string | null;
  readonly accepted: boolean | null;
  readonly eligible: boolean | null;
  readonly withdrawn: boolean;
  readonly deceased: boolean;
  readonly observation: string | null;
  readonly growerType: string | null;
  readonly transportCompany: string | null;
  readonly vehiclePlate: string | null;
  readonly exported: boolean | null;
  readonly isAssociation: boolean | null;
  readonly isNew: boolean | null;
  readonly cityCode: string | null;
  readonly sex: string | null;
  readonly country: string | null;
}

/** Un registro por pantalla: position 0-based ("Registro n de total"). */
export interface AssociatePage {
  readonly position: number;
  readonly total: number;
  readonly associate: Associate;
}

/** Fila de las consultas "Beneficiario" / "Beneficiario Resumen". */
export interface BeneficiaryRow {
  readonly agencyName: string;
  readonly purchaseDate: string;
  readonly fundCode: string;
  readonly invoiceNumber: number;
  readonly idNumber: string;
  readonly firstName: string;
  readonly lastName: string | null;
  readonly greenKg: number;
  readonly netKg: number;
  readonly healthyPercentage: number;
  readonly grossValue: number;
  readonly associateContribution: number;
  readonly cooperativeDiscount: number;
  readonly freightDiscount: number;
  readonly withholding: number;
  readonly otherDiscounts: number;
  readonly netToPay: number;
  readonly specialType: string | null;
  /** Cod_Prod: el informe agrupa por tipo de café dentro de cada cédula (RN-28.2.6). */
  readonly productCode: string;
}

export interface NessQuotaRow {
  readonly idNumber: string;
  readonly names: string;
  readonly program: string;
  readonly quota: number;
}

export interface NessQuotaPage {
  readonly rows: NessQuotaRow[];
  readonly total: number;
}

export interface NessQuotaBalanceRow extends NessQuotaRow {
  readonly invoicedKg: number;
  readonly balance: number;
}

/** Vista previa o resultado de Actualiza Cupos NESS (HU-54); con errores no se cargo nada. */
export interface NessQuotaUploadResult {
  readonly errors: string[];
  readonly warnings: string[];
  readonly rows: number;
  readonly added: number;
  readonly changed: number;
  readonly unchanged: number;
  readonly removed: number;
}

/** Una carga anterior de la lista NESS: quien, cuando y que archivo. */
export interface NessQuotaUploadRecord extends Omit<NessQuotaUploadResult, 'errors' | 'warnings'> {
  readonly id: number;
  readonly date: string;
  readonly username: string;
  readonly fileName: string;
}
