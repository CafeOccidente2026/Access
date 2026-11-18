import { DomSanitizer } from '@angular/platform-browser';
import { describe, expect, it, vi } from 'vitest';
import pdfMake from 'pdfmake/build/pdfmake';
import { printBlob, printPdf, trustBlobUrl } from './pdf-preview';

describe('trustBlobUrl', () => {
  const sanitizer = { bypassSecurityTrustResourceUrl: vi.fn((url: string) => ({ url })) } as unknown as DomSanitizer;

  it('accepts a blob: url', () => {
    const url = 'blob:http://localhost:4200/0b7c1f2e-1234';
    expect(trustBlobUrl(url, sanitizer)).toEqual({ url });
    expect(sanitizer.bypassSecurityTrustResourceUrl).toHaveBeenCalledWith(url);
  });

  it.each(['http://evil.example/x.pdf', 'javascript:alert(1)', 'data:application/pdf;base64,AAAA'])(
    'rejects %s',
    (url) => {
      expect(() => trustBlobUrl(url, sanitizer)).toThrow();
    },
  );
});

/** C9 (prueba del 11-nov, punto 10): facturas, reimpresion y remision se imprimen sin abrir otra pestana. */
describe('printPdf', () => {
  it('calls onError when the document cannot be built', async () => {
    const onError = vi.fn();
    printPdf(Promise.reject(new Error('sin logo')), onError);
    await vi.waitFor(() => expect(onError).toHaveBeenCalledTimes(1));
  });

  it('calls onError when pdfmake fails, instead of leaving the promise unhandled', async () => {
    const createPdf = vi.spyOn(pdfMake, 'createPdf').mockImplementation(() => {
      throw new Error('pdfmake');
    });
    const onError = vi.fn();
    printPdf(Promise.resolve({ content: [] }), onError);
    await vi.waitFor(() => expect(onError).toHaveBeenCalledTimes(1));
    createPdf.mockRestore();
  });

  it('builds the PDF and sends it to print, without opening another tab', async () => {
    const blob = new Blob(['%PDF'], { type: 'application/pdf' });
    const createPdf = vi.spyOn(pdfMake, 'createPdf').mockReturnValue({
      getBlob: (done: (b: Blob) => void) => done(blob),
    } as unknown as ReturnType<typeof pdfMake.createPdf>);
    const open = vi.spyOn(window, 'open');
    const createObjectURL = vi.fn(() => 'blob:http://localhost/factura');
    URL.createObjectURL = createObjectURL;
    URL.revokeObjectURL = vi.fn();

    printPdf(Promise.resolve({ content: [] }), vi.fn());

    await vi.waitFor(() => expect(createObjectURL).toHaveBeenCalledWith(blob));
    expect(document.querySelector('iframe[data-print-frame]')?.getAttribute('src')).toBe('blob:http://localhost/factura');
    expect(open).not.toHaveBeenCalled();
    document.querySelectorAll('iframe[data-print-frame]').forEach((f) => f.remove());
    createPdf.mockRestore();
  });
});

describe('printBlob', () => {
  it('prints from a hidden frame once the PDF loads, and cleans up later', () => {
    vi.useFakeTimers();
    URL.createObjectURL = vi.fn(() => 'blob:http://localhost/remision');
    const revoke = vi.fn();
    URL.revokeObjectURL = revoke;

    printBlob(new Blob(['%PDF']));
    const frame = document.querySelector('iframe[data-print-frame]') as HTMLIFrameElement;
    expect(frame.getAttribute('aria-hidden')).toBe('true');
    expect(frame.style.width).toBe('0px');
    const print = vi.fn();
    Object.defineProperty(frame, 'contentWindow', { value: { focus: vi.fn(), print } });
    frame.dispatchEvent(new Event('load'));
    expect(print).toHaveBeenCalledTimes(1);

    vi.advanceTimersByTime(60_000);
    expect(revoke).toHaveBeenCalledWith('blob:http://localhost/remision');
    expect(document.querySelector('iframe[data-print-frame]')).toBeNull();
    vi.useRealTimers();
  });
});
