import pdfMake from 'pdfmake/build/pdfmake';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';

import { setThemePalette, themeColor } from '../../shared/utils/theme-color';
import type { ReportPdfRequest } from './report-pdf-builders';

/** R3: el Worker arma el PDF con los colores que le manda la pantalla y responde el PDF o el error. */
describe('report-pdf.worker', () => {
  const scope = self as unknown as { onmessage: (e: { data: ReportPdfRequest }) => void };
  const request: ReportPdfRequest = {
    job: { kind: 'inventory', args: [[], true] },
    palette: { '--color-report-teal': '#0f766e' },
  };

  beforeAll(async () => {
    await import('./report-pdf.worker');
  });

  afterEach(() => {
    vi.restoreAllMocks();
    setThemePalette(null);
  });

  it('answers the PDF, built with the colors of the screen', () => {
    const blob = new Blob(['pdf']);
    vi.spyOn(pdfMake, 'createPdf').mockReturnValue({
      getBlob: (cb: (b: Blob) => void) => cb(blob),
    } as unknown as ReturnType<typeof pdfMake.createPdf>);
    const post = vi.spyOn(self, 'postMessage').mockImplementation(() => undefined);

    scope.onmessage({ data: request });
    expect(post).toHaveBeenCalledWith({ blob });
    expect(themeColor('--color-report-teal')).toBe('#0f766e');
  });

  it('answers the error so the screen builds the PDF itself', () => {
    vi.spyOn(pdfMake, 'createPdf').mockImplementation(() => {
      throw new Error('sin fuentes');
    });
    const post = vi.spyOn(self, 'postMessage').mockImplementation(() => undefined);

    scope.onmessage({ data: request });
    expect(post).toHaveBeenCalledWith({ error: 'Error: sin fuentes' });
  });
});
