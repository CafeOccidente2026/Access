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
  readonly contactFields?: FormFieldDefinition[];
  readonly qualityFields?: FormFieldDefinition[];
  readonly weightFields?: FormFieldDefinition[];
  readonly netWeightFields?: FormFieldDefinition[];
  readonly priceFields?: FormFieldDefinition[];
  readonly discountField?: FormFieldDefinition;
  readonly paymentTypeField?: FormFieldDefinition;
  readonly paymentPanel?: PaymentPanelDefinition;
  readonly settlementHeading: string;
  readonly settlementFields: FormFieldDefinition[];
  readonly settlementSecondaryFields?: FormFieldDefinition[];
  readonly netToPayField?: FormFieldDefinition;
  readonly statusField?: FormFieldDefinition;
  readonly additionalDiscountFields?: FormFieldDefinition[];
  readonly reprintButtonLabel?: string;
}
