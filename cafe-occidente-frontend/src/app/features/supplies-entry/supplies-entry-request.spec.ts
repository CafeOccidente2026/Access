import { describe, expect, it } from 'vitest';

import content from '../../../../public/assets/data/supplies-entry.json';
import { buildRequest, initialValues, isoToday, missingField } from './supplies-entry-request';
import { SuppliesEntryContent } from './supplies-entry.model';

const screens = (content as SuppliesEntryContent).screens;

describe('supplies entry', () => {
  it('uses today where Access had Date() and leaves Ingresar Suministros empty', () => {
    expect(initialValues(screens['cash'], '2026-09-30')['entryDate']).toBe('2026-09-30');
    expect(initialValues(screens['packaging'], '2026-09-30')['entryDate']).toBe('2026-09-30');
    expect(initialValues(screens['supply'], '2026-09-30')['entryDate']).toBe('');
    expect(isoToday(new Date(2026, 8, 30, 23, 30))).toBe('2026-09-30');
  });

  it('locks Caja Menor to RP / EFECTIVO', () => {
    const values = initialValues(screens['pettyCash']);
    expect([values['fundCode'], values['paymentMethod']]).toEqual(['RP', 'EFECTIVO']);
    expect(screens['pettyCash'].fields.find((f) => f.key === 'fundCode')?.readonly).toBe(true);
    expect(initialValues(screens['pettyCashExpense'])['fundCode']).toBe('RP');
  });

  it('reports the first missing required field in screen order', () => {
    const values = { ...initialValues(screens['issuedCheck']), idNumber: '87304051', transactionId: 'CHE1', fundCode: 'RP', amount: '300' };
    expect(missingField(screens['issuedCheck'], values)).toBe('Cheque');
    expect(missingField(screens['issuedCheck'], { ...values, checkNumber: '5400' })).toBeNull();
  });

  it('builds ledger and packaging requests with plain numbers', () => {
    const ledger = buildRequest(
      screens['cash'],
      { transactionId: ' sum1 ', idNumber: '87304051', fundCode: 'RP', entryDate: '2026-09-30', detail: '', amount: '1234,5', paymentMethod: 'CHEQUE', checkNumber: '5320' },
      4,
    );
    expect(ledger).toEqual({
      transactionId: 'sum1', agencyId: 4, entryDate: '2026-09-30', idNumber: '87304051', detail: null,
      fundCode: 'RP', amount: 1234.5, paymentMethod: 'CHEQUE', checkNumber: 5320,
    });
    const loan = buildRequest(
      screens['packagingLoan'],
      { transactionId: 'P1', idNumber: '87304051', packagingType: 'NUEVO', entryDate: '2026-09-30', detail: 'x', quantity: '3' },
      null,
    );
    expect(loan).toEqual({ transactionId: 'P1', agencyId: null, entryDate: '2026-09-30', idNumber: '87304051', detail: 'x', packagingType: 'NUEVO', quantity: 3 });
  });
});
