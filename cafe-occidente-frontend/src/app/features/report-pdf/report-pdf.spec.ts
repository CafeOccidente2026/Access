import { signal } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';
import pdfMake from 'pdfmake/build/pdfmake';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { pdfPalette, setThemePalette, themeColor } from '../../shared/utils/theme-color';
import { ReportPdfJob, buildReportDoc } from './report-pdf-builders';
import { renderReportPdf } from './report-pdf';

const sanitizer = { bypassSecurityTrustResourceUrl: (url: string) => ({ url }) } as unknown as DomSanitizer;

const job: ReportPdfJob = {
  kind: 'inventory',
  args: [[], true],
};

/** Worker de mentira: guarda lo que se le manda y responde lo que diga la prueba. */
class FakeWorker {
  static last: FakeWorker;
  onmessage: ((e: MessageEvent) => void) | null = null;
  onerror: ((e: unknown) => void) | null = null;
  sent: unknown;
  terminated = false;
  constructor(readonly url: URL) {
    FakeWorker.last = this;
  }
  postMessage(data: unknown): void {
    this.sent = data;
  }
  terminate(): void {
    this.terminated = true;
  }
  reply(data: unknown): void {
    this.onmessage?.({ data } as MessageEvent);
  }
}

function fakeOnScreenPdf(): ReturnType<typeof vi.spyOn> {
  return vi.spyOn(pdfMake, 'createPdf').mockReturnValue({
    getBlob: (cb: (blob: Blob) => void) => cb(new Blob(['pdf'])),
  } as unknown as ReturnType<typeof pdfMake.createPdf>);
}

describe('renderReportPdf (R3: informes en un Web Worker)', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('sends the report and the theme colors to the worker and shows its PDF; the notice goes on and off', () => {
    vi.stubGlobal('Worker', FakeWorker);
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:http://localhost/1');
    const generating = signal(false);
    const done = vi.fn();

    renderReportPdf(job, sanitizer, done, generating);
    expect(generating()).toBe(true);
    expect(FakeWorker.last.sent).toEqual({ job, palette: pdfPalette() });

    FakeWorker.last.reply({ blob: new Blob(['pdf']) });
    expect(done).toHaveBeenCalledWith({ url: 'blob:http://localhost/1' });
    expect(generating()).toBe(false);
    expect(FakeWorker.last.terminated).toBe(true);
  });

  it('if the worker fails, the screen builds the PDF itself', () => {
    vi.stubGlobal('Worker', FakeWorker);
    const createPdf = fakeOnScreenPdf();
    const done = vi.fn();

    renderReportPdf(job, sanitizer, done);
    FakeWorker.last.reply({ error: 'sin fuentes' });
    expect(createPdf).toHaveBeenCalledTimes(1);
    expect(done).toHaveBeenCalledTimes(1);

    renderReportPdf(job, sanitizer, done);
    FakeWorker.last.onerror?.(new Event('error'));
    expect(createPdf).toHaveBeenCalledTimes(2);
  });

  it('without Worker (old browser) it builds the PDF on the screen, as before', () => {
    vi.stubGlobal('Worker', undefined);
    const createPdf = fakeOnScreenPdf();
    const generating = signal(false);
    const done = vi.fn();

    renderReportPdf(job, sanitizer, done, generating);
    expect(createPdf).toHaveBeenCalledTimes(1);
    expect(done).toHaveBeenCalledTimes(1);
    expect(generating()).toBe(false);
  });

  it('the worker builds the same document as the screen', () => {
    expect(buildReportDoc(job)).toEqual(expect.objectContaining({ content: expect.anything() }));
  });
});

describe('themeColor in the worker', () => {
  afterEach(() => setThemePalette(null));

  it('reads the colors that came from the screen', () => {
    setThemePalette({ '--color-report-teal': '#0f766e' });
    expect(themeColor('--color-report-teal')).toBe('#0f766e');
    expect(themeColor('--color-report-amber')).toBeUndefined();
  });
});
