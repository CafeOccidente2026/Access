import { describe, expect, it } from 'vitest';

import menu from '../../../../public/assets/data/main-menu.json';
import { attachmentName } from './data-export';

describe('data export', () => {
  it('takes the zip name from Content-Disposition', () => {
    expect(attachmentName('attachment; filename="AplicComprasArchivos-20261001-1030.zip"', 'x.zip'))
      .toBe('AplicComprasArchivos-20261001-1030.zip');
    expect(attachmentName(null, 'x.zip')).toBe('x.zip');
  });

  it('only ADMIN sees Exportar Informacion and Exportado Especial', () => {
    const adminOnly = menu.options.filter((o) => 'adminOnly' in o && o.adminOnly).map((o) => o.label);
    expect(adminOnly).toEqual(expect.arrayContaining(['Exportar Informacion', 'Exportado Especial']));
  });
});
