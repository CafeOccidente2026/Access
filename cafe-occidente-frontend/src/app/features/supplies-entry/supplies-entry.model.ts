import { FormFieldDefinition } from '../../core/models';

/** Campo de una pantalla de Suministros. "agency" e "idNumber" tienen control propio (agency-picker
 *  y cedula con puntos en vivo); el resto se dibuja con app-form-field. */
export interface SuppliesField extends FormFieldDefinition {
  readonly required?: boolean;
  /** Fecha por defecto = hoy (Date() en Access). Con readonly queda fija. */
  readonly defaultToday?: boolean;
}

export interface SuppliesScreen {
  readonly windowTitle: string;
  readonly endpoint: string;
  readonly kind: 'ledger' | 'packaging';
  readonly backRoute: string;
  readonly fields: SuppliesField[];
  /** Prestamo Empaques: muestra Nombre y Apellidos del asociado junto a la cedula. */
  readonly lookupGrower?: boolean;
  /** Prestamo Empaques: boton "Imprimir Préstamo" (reporte "Prest Empaques", 2 copias). */
  readonly printLoan?: boolean;
}

export interface LoanReceiptContent {
  readonly salutation: string;
  readonly company: string;
  readonly department: string;
  readonly body: string;
  readonly idPrefix: string;
}

export interface SuppliesEntryContent {
  readonly saveLabel: string;
  readonly backLabel: string;
  readonly printLoanLabel: string;
  readonly newLoanLabel: string;
  readonly messages: Record<'saved' | 'saveError' | 'required' | 'chooseAgency' | 'growerNotFound', string>;
  readonly screens: Record<string, SuppliesScreen>;
  readonly loanReceipt: LoanReceiptContent;
}
