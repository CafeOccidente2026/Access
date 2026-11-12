import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';

import { Grower } from '../../core/models/grower.model';
import { GrowerService } from '../../core/services/grower.service';
import { lookupSeller } from './seller-lookup';

/** Busqueda del vendedor de Seco, Verde y Pasilla (paso 4). */
describe('lookupSeller', () => {
  const grower = {
    idNumber: '990514', firstName: 'CAFICULTOR', secondName: null, lastName: 'DOS', secondLastName: null,
    growerType: 'C', address: 'VEREDA', phone: '', deceased: false,
  } as unknown as Grower;

  function form(idPart1 = '990514', messages?: { deceasedBlocked: string; idNumberNotFound: string }) {
    return {
      model: { idPart1 } as Record<string, string | undefined>,
      errorMessage: signal<string | null>(null),
      blockedByDeceased: signal(false),
      messages: () => messages,
    };
  }

  function run(f: ReturnType<typeof form>, findByIdNumber: () => unknown) {
    const locked = new Set<string>();
    const repaint = vi.fn();
    const found = vi.fn();
    const growers = { findByIdNumber: vi.fn(findByIdNumber) } as unknown as GrowerService;
    lookupSeller(f, growers, locked, repaint, found);
    return { locked, repaint, found, growers };
  }

  it('fills the seller, locks what came filled and goes on; a blank cellphone stays editable', () => {
    const f = form();
    const { locked, repaint, found } = run(f, () => of(grower));
    expect(f.model).toMatchObject({ firstName: 'CAFICULTOR', lastName: 'DOS', idType: 'C', address: 'VEREDA', cellphone: '' });
    expect([...locked]).toEqual(['idPart1', 'firstName', 'lastName', 'idType', 'address']);
    expect(repaint).toHaveBeenCalled();
    expect(found).toHaveBeenCalled();
  });

  it('a deceased grower blocks the form and does not go on', () => {
    const f = form('990514', { deceasedBlocked: 'Fallecido', idNumberNotFound: 'No existe' });
    const { found } = run(f, () => of({ ...grower, deceased: true }));
    expect(f.blockedByDeceased()).toBe(true);
    expect(f.errorMessage()).toBe('Fallecido');
    expect(found).not.toHaveBeenCalled();
  });

  it('a cédula that does not exist says so, with the JSON text or the default one', () => {
    const withText = form('1', { deceasedBlocked: 'Fallecido', idNumberNotFound: 'No existe' });
    run(withText, () => throwError(() => new Error('404')));
    expect(withText.errorMessage()).toBe('No existe');

    const withoutText = form('1');
    run(withoutText, () => throwError(() => new Error('404')));
    expect(withoutText.errorMessage()).toBe('Este número de cédula no existe.');
    expect(form('1').blockedByDeceased()).toBe(false);
  });

  it('does not search without a cédula', () => {
    const { growers } = run(form('  '), () => of(grower));
    expect(growers.findByIdNumber).not.toHaveBeenCalled();
  });
});
