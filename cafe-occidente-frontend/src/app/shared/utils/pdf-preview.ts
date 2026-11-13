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

/** El marco de impresion se quita despues de este rato (el navegador ya tiene el documento). */
const PRINT_FRAME_LIFETIME_MS = 60_000;

/**
 * C9 (prueba del 11-nov, punto 10): imprime el PDF que se arma con `docDefinition` (factura, reimpresion, remision)
 * sin abrir otra pestaña. Con Chrome abierto con --kiosk-printing sale directo a la impresora predeterminada de
 * Windows; sin la bandera aparece el cuadro de impresion. Si armarlo falla, avisa con `onError`.
 */
export function printPdf(docDefinition: Promise<TDocumentDefinitions>, onError: () => void): void {
  docDefinition
    .then((doc) => new Promise<Blob>((resolve) => {
      pdfMake.vfs = pdfFonts;
      pdfMake.createPdf(doc).getBlob(resolve);
    }))
    .then((blob) => printBlob(blob))
    .catch(onError);
}

/** Carga el PDF en un marco oculto y lo manda a imprimir cuando termina de cargar. */
// ponytail: el marco se borra a los 60 s; afterprint no se dispara de forma confiable con el visor de PDF.
export function printBlob(blob: Blob, doc: Document = document): void {
  const url = URL.createObjectURL(blob);
  const frame = doc.createElement('iframe');
  frame.setAttribute('data-print-frame', '');
  frame.setAttribute('aria-hidden', 'true');
  Object.assign(frame.style, { position: 'fixed', width: '0', height: '0', border: '0', right: '0', bottom: '0' });
  frame.addEventListener('load', () => {
    frame.contentWindow?.focus();
    frame.contentWindow?.print();
  });
  setTimeout(() => {
    URL.revokeObjectURL(url);
    frame.remove();
  }, PRINT_FRAME_LIFETIME_MS);
  frame.src = url;
  doc.body.appendChild(frame);
}

/** Único punto que salta el saneamiento de Angular. Es seguro porque solo acepta URLs "blob:", que
 *  crea URL.createObjectURL con el PDF que arma esta misma app: nunca una URL que venga de afuera. */
export function trustBlobUrl(url: string, sanitizer: DomSanitizer): SafeResourceUrl {
  if (!url.startsWith('blob:')) {
    throw new Error(`Solo se permiten URLs blob: para la vista previa del PDF (${url})`);
  }
  return sanitizer.bypassSecurityTrustResourceUrl(url);
}
