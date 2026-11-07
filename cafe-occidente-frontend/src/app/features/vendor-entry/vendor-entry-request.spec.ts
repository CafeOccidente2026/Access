import { describe, expect, it } from 'vitest';

import content from '../../../../public/assets/data/vendor-entry.json';
import { buildVendorRequest, validationError } from './vendor-entry-request';
import { VendorEntryContent } from './vendor-entry.model';

const data = content as VendorEntryContent;
const legal = data.forms.legal;
const natural = data.forms.natural;

describe('vendor entry', () => {
  it('has the RF-28.1 field tables: 13 for natural and 11 for legal person (with Fecha Afiliación, Tipo and Sexo)', () => {
    const count = (fields: typeof natural) => fields.length + 2 + fields.filter((f) => f.besideSex).length;
    expect(count(natural)).toBe(13);
    expect(count(legal)).toBe(11);
  });

  it('RN-28.1.1: asks for Persona Jurídica or Persona Natural', () => {
    expect(data.dialog.question).toBe('¿Qué tipo de vendedor va a ingresar?');
    expect(data.dialog.options.map((o) => o.label)).toEqual(['Persona Jurídica', 'Persona Natural']);
  });

  it('requires Id and first name, an agency and a valid email', () => {
    expect(validationError(natural, { firstName: 'AURA' }, data.messages)).toBe('Falta: Id Vendedor');
    expect(validationError(natural, { idNumber: '1' }, data.messages)).toBe('Falta: 1er Nombre');
    expect(validationError(natural, { idNumber: '1', firstName: 'A', email: 'no-es-email' }, data.messages)).toBe(data.messages.email);
    expect(validationError(natural, { idNumber: '1', firstName: 'A', email: 'a@b.co' }, data.messages)).toBeNull();
    expect(validationError(natural, { idNumber: '1', firstName: 'A' }, data.messages)).toBeNull();
  });

  it('HU-51 CA-2: legal person requires NIT, Razón Social and Clasificación', () => {
    expect(validationError(legal, { businessName: 'ASOPEMA', classification: 'ASOCIACION' }, data.messages)).toBe('Falta: Id Vendedor (NIT)');
    expect(validationError(legal, { idNumber: '900', classification: 'ASOCIACION' }, data.messages)).toBe('Falta: Razón Social');
    expect(validationError(legal, { idNumber: '900', businessName: 'ASOPEMA' }, data.messages)).toBe('Falta: Clasificación');
    expect(validationError(legal, { idNumber: '900', businessName: 'ASOPEMA', classification: 'FUNDACION' }, data.messages)).toBeNull();
  });

  it('legal person sends Razón Social, Clasificación, Sexo and the contact data, not the names', () => {
    const request = buildVendorRequest('legal', legal, {
      idNumber: ' 900723205 ', businessName: 'FUNDACION SUYUSAMA', classification: 'FUNDACION', address: 'CLL 20 24 64',
      lastName: 'X', sex: 'E', phone: '300', postalCode: '520001', email: 'a@b.co',
    });
    expect(request).toEqual({
      legalPerson: true, idNumber: '900723205', firstName: null, secondName: null, lastName: null, secondLastName: null,
      sex: 'E', phone: '300', address: 'CLL 20 24 64', postalCode: '520001', email: 'a@b.co',
      businessName: 'FUNDACION SUYUSAMA', classification: 'FUNDACION',
    });
  });

  it('natural person sends the 11 editable fields, blanks as null', () => {
    const request = buildVendorRequest('natural', natural, {
      idNumber: '59124124', firstName: 'AURA', secondName: '', lastName: 'TUTISTAR', sex: 'F', phone: '300',
      address: 'VDA', postalCode: '522060', email: 'aura@correo.co', businessName: 'X',
    });
    expect(request).toMatchObject({
      legalPerson: false, firstName: 'AURA', secondName: null, lastName: 'TUTISTAR', sex: 'F', postalCode: '522060',
      businessName: null, classification: null,
    });
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
