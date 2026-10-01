import { describe, expect, it } from 'vitest';

import content from '../../../../public/assets/data/vendor-entry.json';
import { buildVendorRequest, validationError } from './vendor-entry-request';
import { VendorEntryContent } from './vendor-entry.model';

const data = content as VendorEntryContent;
const association = data.forms.association;
const person = data.forms.person;

describe('vendor entry', () => {
  it('has the Access item counts: 6 for association, 13 for person (with Fecha Afiliación and Tipo)', () => {
    expect(association.length + 2).toBe(6);
    expect(person.length + 2 + person.filter((f) => f.besideSex).length).toBe(13);
  });

  it('requires Id and first name, an agency and a valid email', () => {
    expect(validationError(person, { firstName: 'AURA' }, 4, data.messages)).toBe('Falta: Id Vendedor');
    expect(validationError(person, { idNumber: '1' }, 4, data.messages)).toBe('Falta: 1er Nombre');
    expect(validationError(person, { idNumber: '1', firstName: 'A' }, null, data.messages)).toBe(data.messages.chooseAgency);
    expect(validationError(person, { idNumber: '1', firstName: 'A', email: 'no-es-email' }, 4, data.messages)).toBe(data.messages.email);
    expect(validationError(person, { idNumber: '1', firstName: 'A', email: 'a@b.co' }, 4, data.messages)).toBeNull();
    expect(validationError(person, { idNumber: '1', firstName: 'A' }, 4, data.messages)).toBeNull();
  });

  it('association only sends its 4 editable fields', () => {
    const request = buildVendorRequest('association', association, {
      idNumber: ' 900723205 ', firstName: 'FUNDACION SUYUSAMA', address: 'CLL 20 24 64', lastName: 'X', sex: 'M', email: 'a@b.co',
    }, 4);
    expect(request).toEqual({
      association: true, idNumber: '900723205', firstName: 'FUNDACION SUYUSAMA', secondName: null, lastName: null,
      secondLastName: null, agencyId: 4, sex: null, phone: null, address: 'CLL 20 24 64', postalCode: null, email: null,
    });
  });

  it('person sends the 11 editable fields, blanks as null', () => {
    const request = buildVendorRequest('person', person, {
      idNumber: '59124124', firstName: 'AURA', secondName: '', lastName: 'TUTISTAR', sex: 'F', phone: '300',
      address: 'VDA', postalCode: '522060', email: 'aura@correo.co',
    }, 4);
    expect(request).toMatchObject({ association: false, secondName: null, lastName: 'TUTISTAR', sex: 'F', postalCode: '522060' });
  });
});
