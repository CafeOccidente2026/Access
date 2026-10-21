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
  pdfMake.createPdf(docDefinition).getBlob((blob) => done(trustBlobUrl(URL.createObjectURL(blob), sanitizer)));
}

/** Único punto que salta el saneamiento de Angular. Es seguro porque solo acepta URLs "blob:", que
 *  crea URL.createObjectURL con el PDF que arma esta misma app: nunca una URL que venga de afuera. */
export function trustBlobUrl(url: string, sanitizer: DomSanitizer): SafeResourceUrl {
  if (!url.startsWith('blob:')) {
    throw new Error(`Solo se permiten URLs blob: para la vista previa del PDF (${url})`);
  }
  return sanitizer.bypassSecurityTrustResourceUrl(url);
}
