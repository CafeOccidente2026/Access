export interface BeneficiaryReport {
  readonly windowTitle: string;
  /** Texto del InputBox de la consulta "Beneficiario". */
  readonly prompt?: string;
  /** RN-28.2.2: muestra "Todos los beneficiarios"; sin ella, cédula vacía = todos (como Access). */
  readonly allBeneficiaries?: boolean;
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
  /** Subtotal por Cod_Prod ("Suma {code}"). */
  readonly productSum: string;
  readonly grandTotal: string;
  readonly page: string;
}

export interface BeneficiaryReportContent {
  readonly idNumberLabel: string;
  readonly fromLabel: string;
  readonly toLabel: string;
  readonly agencyLabel: string;
  readonly allAgenciesLabel: string;
  readonly allBeneficiariesLabel: string;
  readonly acceptLabel: string;
  readonly cancelLabel: string;
  readonly backRoute: string;
  readonly messages: Record<'empty' | 'error' | 'dates' | 'idNumber', string>;
  readonly text: BeneficiaryReportText;
  readonly reports: Record<string, BeneficiaryReport>;
}
