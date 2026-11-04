import type { Content, TDocumentDefinitions } from 'pdfmake/interfaces';

import { SuppliesEntryResponse } from '../../core/models/supplies.model';
import { formatThousands } from '../../shared/utils/number-format';
import { LoanReceiptContent } from './supplies-entry.model';

/** "d de mmmm de yyyy" de Access. */
export function longSpanishDate(iso: string): string {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d).toLocaleDateString('es-CO', { day: 'numeric', month: 'long', year: 'numeric' });
}

/**
 * Reporte "Prest Empaques": constancia del asociado. Comando15_Click lo mandaba a la impresora dos
 * veces (una copia para cada parte); aca son dos paginas del mismo PDF.
 */
export function buildLoanReceiptDoc(
  text: LoanReceiptContent,
  saved: SuppliesEntryResponse,
  quantity: number,
  packagingType: string,
): TDocumentDefinitions {
  const name = `${saved.firstNames ?? ''} ${saved.lastNames ?? ''}`.trim();
  const idNumber = formatThousands(saved.idNumber);
  const body = text.body
    .replace('{name}', name)
    .replace('{idNumber}', idNumber)
    .replace('{quantity}', String(quantity))
    .replace('{type}', packagingType);
  const copy = (pageBreak: boolean): Content => ({
    stack: [
      { text: text.salutation },
      { text: text.company, bold: true },
      { text: `${saved.agencyName} ${text.department}`, margin: [0, 0, 0, 24] },
      { text: `${saved.agencyName}, ${longSpanishDate(saved.entryDate)}`, margin: [0, 0, 0, 24] },
      { text: body, alignment: 'justify', lineHeight: 1.4, margin: [0, 0, 0, 60] },
      { canvas: [{ type: 'line', x1: 0, y1: 0, x2: 220, y2: 0, lineWidth: 0.75 }] },
      { text: name },
      { text: `${text.idPrefix}${idNumber}` },
    ],
    pageBreak: pageBreak ? 'after' : undefined,
  });
  return {
    pageSize: 'LETTER',
    pageMargins: [72, 72, 72, 72],
    defaultStyle: { font: 'Roboto', fontSize: 11 },
    content: [copy(true), copy(false)],
  };
}
