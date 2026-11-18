import { WritableSignal } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';

import { renderPdfPreview, trustBlobUrl } from '../../shared/utils/pdf-preview';
import { pdfPalette } from '../../shared/utils/theme-color';
import { ReportPdfJob, ReportPdfReply, ReportPdfRequest, buildReportDoc } from './report-pdf-builders';

export type { ReportPdfJob } from './report-pdf-builders';

/**
 * Arma el PDF en el Worker y muestra la vista previa. `generating` queda en true mientras tanto (aviso
 * "Generando PDF…"). Sin Worker, o si falla, lo arma en la pantalla como antes.
 */
export function renderReportPdf(
  job: ReportPdfJob,
  sanitizer: DomSanitizer,
  done: (url: SafeResourceUrl) => void,
  generating?: WritableSignal<boolean>,
): void {
  generating?.set(true);
  const finish = (url: SafeResourceUrl) => {
    generating?.set(false);
    done(url);
  };
  const onScreen = () => renderPdfPreview(buildReportDoc(job), sanitizer, finish);
  if (typeof Worker === 'undefined') {
    onScreen();
    return;
  }
  const worker = new Worker(new URL('./report-pdf.worker', import.meta.url), { type: 'module' });
  worker.onmessage = ({ data }: MessageEvent<ReportPdfReply>) => {
    worker.terminate();
    if ('blob' in data) {
      finish(trustBlobUrl(URL.createObjectURL(data.blob), sanitizer));
    } else {
      onScreen();
    }
  };
  worker.onerror = () => {
    worker.terminate();
    onScreen();
  };
  worker.postMessage({ job, palette: pdfPalette() } satisfies ReportPdfRequest);
}
