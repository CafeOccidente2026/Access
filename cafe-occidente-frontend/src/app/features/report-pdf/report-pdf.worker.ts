import pdfMake from 'pdfmake/build/pdfmake';
import pdfFonts from 'pdfmake/build/vfs_fonts';

import { setThemePalette } from '../../shared/utils/theme-color';
import { ReportPdfReply, ReportPdfRequest, buildReportDoc } from './report-pdf-builders';

/** R3: arma el informe y su PDF fuera de la pantalla. Responde el PDF o el error (la pantalla lo arma entonces ella). */
const scope = self as unknown as {
  onmessage: ((event: MessageEvent<ReportPdfRequest>) => void) | null;
  postMessage(reply: ReportPdfReply): void;
};

scope.onmessage = ({ data }) => {
  try {
    setThemePalette(data.palette);
    pdfMake.vfs = pdfFonts;
    pdfMake.createPdf(buildReportDoc(data.job)).getBlob((blob) => scope.postMessage({ blob }));
  } catch (error) {
    scope.postMessage({ error: String(error) });
  }
};
