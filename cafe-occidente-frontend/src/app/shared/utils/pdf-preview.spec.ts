import { DomSanitizer } from '@angular/platform-browser';
import { describe, expect, it, vi } from 'vitest';
import { trustBlobUrl } from './pdf-preview';

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
