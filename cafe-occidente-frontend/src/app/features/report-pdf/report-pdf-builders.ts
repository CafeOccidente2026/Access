import type { TDocumentDefinitions } from 'pdfmake/interfaces';

import { buildAnnouncementReportDoc } from '../announcement-report/announcement-report-pdf';
import { buildBeneficiaryDoc } from '../beneficiary-report/beneficiary-report-pdf';
import { buildPurchasesByDateDoc } from '../date-range-dialog/purchases-by-date-pdf';
import { buildFutureBalanceDoc } from '../future-report/future-balance-pdf';
import { buildInventoryReportDoc } from '../inventory-report/inventory-report-pdf';
import { buildSuppliesReportDoc } from '../supplies-report/supplies-report-pdf';

/**
 * R3 (rendimiento): los informes se arman en un Web Worker para que la pantalla no se congele (R0: el de compras de
 * 1 año la dejaba 7 s quieta). El documento tiene funciones (lineas, pie de pagina) que no viajan al Worker: viaja el
 * nombre del informe con sus datos, y el Worker lo arma con estos mismos generadores.
 */
const BUILDERS = {
  announcement: buildAnnouncementReportDoc,
  beneficiary: buildBeneficiaryDoc,
  purchasesByDate: buildPurchasesByDateDoc,
  futureBalance: buildFutureBalanceDoc,
  inventory: buildInventoryReportDoc,
  supplies: buildSuppliesReportDoc,
};

type Builders = typeof BUILDERS;

/** Un informe por armar: su nombre y los argumentos de su generador (datos simples, que se pueden copiar al Worker). */
export type ReportPdfJob = { [K in keyof Builders]: { kind: K; args: Parameters<Builders[K]> } }[keyof Builders];

export function buildReportDoc(job: ReportPdfJob): TDocumentDefinitions {
  const build = BUILDERS[job.kind] as (...args: unknown[]) => TDocumentDefinitions;
  return build(...job.args);
}

/** Mensaje que la pantalla manda al Worker y el que recibe. */
export interface ReportPdfRequest {
  job: ReportPdfJob;
  palette: Record<string, string>;
}
export type ReportPdfReply = { blob: Blob } | { error: string };
