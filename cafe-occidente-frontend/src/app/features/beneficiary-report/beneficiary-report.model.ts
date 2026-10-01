export interface BeneficiaryReport {
  readonly windowTitle: string;
  /** Texto del InputBox de la consulta "Beneficiario". */
  readonly prompt?: string;
  readonly pdfTitle: string;
  /** detail = "Beneficiario" (nombre en el encabezado); summary = "Beneficiario resumen" (nombre por fila). */
  readonly layout: 'detail' | 'summary';
  readonly dates: boolean;
  readonly columns: string[];
}

export interface BeneficiaryReportText {
  readonly company: string;
  readonly nit: string;
  readonly cedula: string;
  readonly groupSummary: string;
  readonly recordSingular: string;
  readonly recordPlural: string;
  readonly sum: string;
  readonly grandTotal: string;
  readonly page: string;
}

export interface BeneficiaryReportContent {
  readonly idNumberLabel: string;
  readonly fromLabel: string;
  readonly toLabel: string;
  readonly agencyLabel: string;
  readonly allAgenciesHint: string;
  readonly acceptLabel: string;
  readonly cancelLabel: string;
  readonly backRoute: string;
  readonly messages: Record<'empty' | 'error' | 'dates', string>;
  readonly text: BeneficiaryReportText;
  readonly reports: Record<string, BeneficiaryReport>;
}
