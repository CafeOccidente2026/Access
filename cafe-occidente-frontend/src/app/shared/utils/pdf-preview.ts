import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import pdfMake from 'pdfmake/build/pdfmake';
import pdfFonts from 'pdfmake/build/vfs_fonts';
import type { TDocumentDefinitions } from 'pdfmake/interfaces';

/** Mismo mecanismo que el Reporte de Anuncio: el PDF se muestra en un iframe de la pantalla (blob
 *  URL), sin ventana emergente que el navegador pueda bloquear. */
export function renderPdfPreview(
  docDefinition: TDocumentDefinitions,
  sanitizer: DomSanitizer,
  done: (url: SafeResourceUrl) => void,
): void {
  pdfMake.vfs = pdfFonts;
  pdfMake.createPdf(docDefinition).getBlob((blob) => done(sanitizer.bypassSecurityTrustResourceUrl(URL.createObjectURL(blob))));
}
