import { federationValues } from './federation';

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
});
