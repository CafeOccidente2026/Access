import { describe, expect, it } from 'vitest';

import content from '../../../../public/assets/data/supplies-entry.json';
import { buildRequest, idSource, initialValues, invalidField, isoToday } from './supplies-entry-request';
import { SuppliesEntryContent } from './supplies-entry.model';

const data = content as SuppliesEntryContent;
const screens = data.screens;
const field = (screen: string, key: string) => screens[screen].fields.find((f) => f.key === key);

describe('supplies entry', () => {
  it('the transaction Id is read-only on every screen: the server generates it (RF §2.1)', () => {
    for (const [key, screen] of Object.entries(screens)) {
      const id = screen.fields.find((f) => f.key === 'transactionId');
      expect(id?.readonly, key).toBe(true);
      expect(id?.required, key).toBeFalsy();
    }
  });

  it('the date is automatic except in Ingresar Suministros, which starts with today (D2)', () => {
    for (const key of ['cash', 'adjustment', 'pettyCash', 'pettyCashExpense', 'issuedCheck', 'packaging', 'packagingLoan']) {
      expect(field(key, 'entryDate')?.readonly, key).toBe(true);
    }
    expect(field('supply', 'entryDate')?.readonly).toBeFalsy();
    expect(initialValues(screens['supply'], '2026-11-05')['entryDate']).toBe('2026-11-05');
    expect(isoToday(new Date(2026, 10, 5, 23, 30))).toBe('2026-11-05');
  });

  it('Caja Menor is RP / EFECTIVO and its cédula is the Fiel, not typed (RN-27.12.1.2)', () => {
    const values = initialValues(screens['pettyCash']);
    expect([values['fundCode'], values['paymentMethod']]).toEqual(['RP', 'EFECTIVO']);
    expect(field('pettyCash', 'idNumber')).toMatchObject({ readonly: true });
    expect(field('pettyCash', 'idNumber')?.required).toBeFalsy();
  });

  it('the Id follows the fund or the packaging type the user picks', () => {
    expect(idSource(screens['cash'])).toBe('fundCode');
    expect(idSource(screens['packagingLoan'])).toBe('packagingType');
    expect(idSource(screens['pettyCash'])).toBeNull();
  });

  it('reports the first missing field and a check that is not 4 digits', () => {
    const messages = data.messages;
    const values = { ...initialValues(screens['issuedCheck']), idNumber: '87304051', fundCode: 'RP', amount: '300' };
    expect(invalidField(screens['issuedCheck'], values, messages)).toBe('Falta: Cheque');
    expect(invalidField(screens['issuedCheck'], { ...values, checkNumber: '548' }, messages)).toBe(messages.checkDigits);
    expect(invalidField(screens['issuedCheck'], { ...values, checkNumber: '0548' }, messages)).toBeNull();
  });

  it('builds the requests without Id, with the check as text and the date only in Ingresar Suministros', () => {
    const cash = buildRequest(screens['cash'], {
      transactionId: 'SDTA2001', idNumber: '87304051', fundCode: 'RP', entryDate: '2026-11-05', detail: '',
      amount: '1234,5', paymentMethod: 'CHEQUE', checkNumber: '0548',
    });
    expect(cash).toEqual({
      entryDate: null, idNumber: '87304051', detail: null, fundCode: 'RP', amount: 1234.5, paymentMethod: 'CHEQUE',
      checkNumber: '0548',
    });

    const supply = buildRequest(screens['supply'], {
      idNumber: '87304051', fundCode: 'LF', entryDate: '2026-11-01', detail: 'TESORERIA', amount: '100000',
    });
    expect(supply).toMatchObject({ entryDate: '2026-11-01', checkNumber: null });

    const loan = buildRequest(screens['packagingLoan'], {
      transactionId: 'SDTA5PRE001', idNumber: '87304051', packagingType: 'NUEVO', entryDate: '2026-11-05', detail: 'x',
      quantity: '3',
    });
    expect(loan).toEqual({ idNumber: '87304051', detail: 'x', packagingType: 'NUEVO', quantity: 3 });
  });
});
