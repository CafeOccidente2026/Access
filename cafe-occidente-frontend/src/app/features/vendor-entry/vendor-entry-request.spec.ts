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
    expect(validationError(person, { firstName: 'AURA' }, data.messages)).toBe('Falta: Id Vendedor');
    expect(validationError(person, { idNumber: '1' }, data.messages)).toBe('Falta: 1er Nombre');
    expect(validationError(person, { idNumber: '1', firstName: 'A', email: 'no-es-email' }, data.messages)).toBe(data.messages.email);
    expect(validationError(person, { idNumber: '1', firstName: 'A', email: 'a@b.co' }, data.messages)).toBeNull();
    expect(validationError(person, { idNumber: '1', firstName: 'A' }, data.messages)).toBeNull();
  });

  it('association only sends its 4 editable fields', () => {
    const request = buildVendorRequest('association', association, {
      idNumber: ' 900723205 ', firstName: 'FUNDACION SUYUSAMA', address: 'CLL 20 24 64', lastName: 'X', sex: 'M', email: 'a@b.co',
    });
    expect(request).toEqual({
      association: true, idNumber: '900723205', firstName: 'FUNDACION SUYUSAMA', secondName: null, lastName: null,
      secondLastName: null, sex: null, phone: null, address: 'CLL 20 24 64', postalCode: null, email: null,
    });
  });

  it('person sends the 11 editable fields, blanks as null', () => {
    const request = buildVendorRequest('person', person, {
      idNumber: '59124124', firstName: 'AURA', secondName: '', lastName: 'TUTISTAR', sex: 'F', phone: '300',
      address: 'VDA', postalCode: '522060', email: 'aura@correo.co',
    });
    expect(request).toMatchObject({ association: false, secondName: null, lastName: 'TUTISTAR', sex: 'F', postalCode: '522060' });
  });
});

/** La regex de antes (S8786, retroceso super lineal): la validacion nueva acepta y rechaza lo mismo. */
const OLD_EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

describe('email check without backtracking accepts and rejects exactly as before', () => {
  const fields = [{ key: 'email', label: 'Correo', required: false }] as never;
  const verdict = (email: string) => validationError(fields, { email }, (content as VendorEntryContent).messages) === null;
  // Antes y ahora el correo se valida recortado; vacio no se valida.
  const before = (email: string) => email.trim() === '' || OLD_EMAIL.test(email.trim());

  it.each([
    'a@b.co', 'ana.maria@cafe.com.co', 'x@y.z', 'x@y.', 'x@.y', 'x@y', '@b.co', 'a@@b.co', 'a b@c.co', 'a@b c.co',
    'a@b..c', 'a@.b.c', 'a@b.c.', 'a.@b.c', '.a@b.c', 'a@b.c d', '', ' ', 'a@b.c\n', 'ñandú@café.co', 'a@b.c.d.e.f',
  ])('"%s"', (email) => {
    expect(verdict(email)).toBe(before(email));
  });

  it('agrees on generated addresses', () => {
    const alphabet = 'ab.@ .@a1-_';
    let seed = 7;
    const next = () => (seed = (seed * 1103515245 + 12345) % 2147483648) / 2147483648;
    for (let n = 0; n < 5000; n++) {
      const email = Array.from({ length: 1 + Math.floor(next() * 12) }, () => alphabet[Math.floor(next() * alphabet.length)]).join('');
      expect(verdict(email), JSON.stringify(email)).toBe(before(email));
    }
  });
});
