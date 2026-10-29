import { afterEach, describe, expect, it } from 'vitest';

import { themeColor } from './theme-color';

describe('themeColor', () => {
  afterEach(() => document.documentElement.style.removeProperty('--color-prueba'));

  it('reads a token of styles.css for the PDFs', () => {
    document.documentElement.style.setProperty('--color-prueba', ' #b91c1c ');
    expect(themeColor('--color-prueba')).toBe('#b91c1c');
  });

  it('gives undefined when the token is not loaded, so pdfmake uses its default color', () => {
    expect(themeColor('--color-prueba')).toBeUndefined();
  });
});
