import { of } from 'rxjs';

import { GrowerService } from '../../core/services/grower.service';
import { federationValues, loadFederation } from './federation';

/** C3 (prueba del 11-nov, punto 3): el bloque Información Federación sale solo de NESS. */
describe('federationValues', () => {
  it('with NESS it takes the cédula, name, program and quota of NESS', () => {
    expect(federationValues({ programa: 'NESPRESSO', cupo: '200', cedula: '990054004', nombres: 'DORA' })).toEqual({
      idNumber: '990054004', fullName: 'DORA', program: 'NESPRESSO', quota: '200',
    });
  });

  it('without NESS the block stays empty', () => {
    expect(federationValues(null)).toEqual({ idNumber: '', fullName: '', program: '', quota: '' });
  });

  it('loadFederation asks NESS with the cédula and the special, fills the model and repaints', () => {
    const findProgram = vi.fn(() => of({ programa: 'NESPRESSO', cupo: '200', cedula: '210514', nombres: 'PAZ' }));
    const model: Record<string, string> = { idPart1: ' 210514 ' };
    const repaint = vi.fn();
    loadFederation({ findProgram } as unknown as GrowerService, model['idPart1'], 'NESPRESSO - FTUSA', model, repaint);
    expect(findProgram).toHaveBeenCalledWith('210514', 'NESPRESSO - FTUSA');
    expect(model).toMatchObject({ idNumber: '210514', fullName: 'PAZ', program: 'NESPRESSO', quota: '200' });
    expect(repaint).toHaveBeenCalled();
  });

  it('loadFederation does nothing without a cédula', () => {
    const findProgram = vi.fn();
    loadFederation({ findProgram } as unknown as GrowerService, '  ', undefined, {}, vi.fn());
    expect(findProgram).not.toHaveBeenCalled();
  });
});
