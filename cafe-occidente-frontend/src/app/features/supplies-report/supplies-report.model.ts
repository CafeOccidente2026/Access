/** Filtro del dialogo (Fondo, Tipo, Desde/Hasta Fecha, Forma Pago). Sin type = lista de opciones. */
export interface SuppliesReportFilter {
  readonly key: string;
  readonly label: string;
  readonly type?: 'date';
  readonly options?: string[];
  readonly required?: boolean;
}

export interface SuppliesReport {
  readonly windowTitle: string;
  readonly endpoint: string;
  /** Relacion Cheques: POST porque marca los cheques impresos. */
  readonly post?: boolean;
  readonly layout: 'movement' | 'supplies' | 'packaging' | 'checks' | 'paymentMethods';
  readonly pdfTitle?: string;
  readonly fixedParams?: Record<string, string>;
  readonly filters: SuppliesReportFilter[];
  readonly columns: string[];
  readonly excel?: { readonly fileName: string; readonly columns: string[] };
  readonly backRoute: string;
}

export interface SuppliesReportText {
  readonly company: string;
  readonly checksTitle: string;
  readonly agencyGroup: string;
  readonly checksGroupSummary: string;
  readonly paymentTitle: string;
  readonly paymentAgency: string;
  readonly paymentGroupSummary: string;
  readonly sum: string;
  readonly grandTotal: string;
  readonly page: string;
}

export interface SuppliesReportContent {
  readonly acceptLabel: string;
  readonly cancelLabel: string;
  readonly downloadLabel: string;
  readonly agencyLabel: string;
  readonly allAgenciesHint: string;
  readonly messages: Record<'empty' | 'error' | 'required', string>;
  readonly text: SuppliesReportText;
  readonly reports: Record<string, SuppliesReport>;
}
