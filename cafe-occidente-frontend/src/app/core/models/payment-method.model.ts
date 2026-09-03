export interface PaymentMethodEntry {
  readonly label: string;
  readonly value: string | number;
}

export interface PaymentPanelDefinition {
  readonly heading: string;
  readonly methods: PaymentMethodEntry[];
  readonly totalLabel: string;
  readonly totalValue: string | number;
}
