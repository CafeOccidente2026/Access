/** Etiquetas del formulario Compras Café Seco (todas desde JSON, sin texto embebido). */
export interface DryCoffeeFormContent {
  readonly windowTitle: string;
  readonly sections: {
    readonly agency: string;
    readonly grower: string;
    readonly weights: string;
    readonly pricing: string;
    readonly discounts: string;
    readonly settlement: string;
  };
  readonly labels: Record<string, string>;
  readonly specialTypeOptions: string[];
  readonly growerTypeOptions: { readonly value: string; readonly label: string }[];
  readonly paymentMethodOptions: string[];
  readonly printButton: string;
  readonly savedMessage: string;
  readonly errorMessage: string;
  readonly closeWithoutPrintingWarning: string;
  readonly confirmDiscard: string;
  readonly cancelDiscard: string;
}
