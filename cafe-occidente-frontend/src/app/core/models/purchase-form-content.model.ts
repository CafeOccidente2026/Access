import { FormFieldDefinition } from './form-field.model';
import { PaymentPanelDefinition } from './payment-method.model';

/**
 * Forma generica del contenido de un formulario de compra.
 * Cada pantalla (seco, verde, pasilla, otros, consulta) usa el subconjunto
 * de secciones que le aplica; las no usadas simplemente no vienen en el JSON.
 */
export interface PurchaseFormContent {
  readonly windowTitle: string;
  readonly theme: 'sky' | 'green' | 'teal' | 'cyan';
  readonly topFields: FormFieldDefinition[];
  readonly identificationFields: FormFieldDefinition[];
  readonly federationHeading?: string;
  readonly federationFields?: FormFieldDefinition[];
  /** Sobrescribe el color de resaltado de la fila federationFields (por defecto bg-orange-200). */
  readonly federationHighlightClass?: string;
  readonly contactFields?: FormFieldDefinition[];
  readonly qualityFields?: FormFieldDefinition[];
  readonly weightFields?: FormFieldDefinition[];
  readonly netWeightFields?: FormFieldDefinition[];
  readonly priceFields?: FormFieldDefinition[];
  readonly discountField?: FormFieldDefinition;
  readonly paymentTypeField?: FormFieldDefinition;
  readonly paymentPanel?: PaymentPanelDefinition;
  /** Ausente cuando la pantalla no liquida una compra (ej. Compras a Futuro: solo anuncia, no calcula Vr. Kilo). */
  readonly settlementHeading?: string;
  readonly settlementFields?: FormFieldDefinition[];
  readonly settlementSecondaryFields?: FormFieldDefinition[];
  readonly netToPayField?: FormFieldDefinition;
  readonly statusField?: FormFieldDefinition;
  readonly additionalDiscountFields?: FormFieldDefinition[];
  readonly reprintButtonLabel?: string;
  /** Reduce el ancho de las filas weightFields/netWeightFields para que quepan sin envolver (solo cafe seco por ahora). */
  readonly compactFields?: boolean;
}
