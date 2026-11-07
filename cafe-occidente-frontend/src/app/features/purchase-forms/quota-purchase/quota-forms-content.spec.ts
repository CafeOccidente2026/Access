import { describe, expect, it } from 'vitest';

import billing from '../../../../../public/assets/data/purchase-form-quota-billing.json';
import quota from '../../../../../public/assets/data/purchase-form-quota-purchase.json';
import { FormFieldDefinition } from '../../../core/models';
import { FormFieldComponent } from '../../../shared/ui';

/** El campo Anuncio de Cupos y de Anunciadas muestra el número tal cual ("1"), no como cantidad ("1,00"). */
describe.each([
  ['Facturar Anuncios con Cupos', quota],
  ['Facturar Compras Anunciadas', billing],
])('%s: campo Anuncio', (_, content) => {
  it('shows the announcement number as typed, without decimals', () => {
    const announcement = (content.topFields as FormFieldDefinition[]).find((f) => f.key === 'announcement')!;
    const field = new FormFieldComponent();
    field.field = { ...announcement, value: '1' };

    field.ngOnChanges();

    expect(field.inputValue).toBe('1');
  });
});
