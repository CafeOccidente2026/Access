import { describe, expect, it } from 'vitest';

import { commitPayment, initialPayment, readyPayment, visibleMethods } from './payment-cascade';

const NET = 4944624;

describe('payment cascade (Form_COMPRAS.bas FPef/FPch/FPtx/FPdat)', () => {
  it('starts all in cash, ready to print like Access', () => {
    expect(readyPayment(NET, initialPayment(NET))).toEqual({
      cashAmount: NET, checkAmount: 0, transferAmount: 0, cardAmount: 0, checkNumber: null,
    });
  });

  it('lowering cash moves the rest to check, then asks for the check number', () => {
    let state = { ...initialPayment(NET), amounts: { cash: 1944624, check: 0, transfer: 0, card: 0 } };
    let step = commitPayment(NET, state, 'cash');
    expect(step.next).toBe('check');
    expect(step.state.amounts.check).toBe(3000000);
    step = commitPayment(NET, step.state, 'check');
    expect(step.next).toBe('checkNumber');
    expect(readyPayment(NET, step.state)).toBeNull(); // cheque sin numero
    state = { ...step.state, checkNumber: '5379' };
    step = commitPayment(NET, state, 'checkNumber');
    expect(step.next).toBeNull();
    expect(readyPayment(NET, step.state)).toMatchObject({ cashAmount: 1944624, checkAmount: 3000000, checkNumber: '5379' });
  });

  it('keeps cascading to transfer and card with what is still missing', () => {
    let step = commitPayment(NET, { amounts: { cash: 0, check: 1000000, transfer: 0, card: 0 }, checkNumber: '7' }, 'checkNumber');
    expect(step.next).toBe('transfer');
    expect(step.state.amounts.transfer).toBe(NET - 1000000);
    step = commitPayment(NET, { ...step.state, amounts: { ...step.state.amounts, transfer: 2000000 } }, 'transfer');
    expect(step.next).toBe('card');
    expect(step.state.amounts.card).toBe(NET - 3000000);
  });

  it('is DESCUADRADA when the sum goes over, or card still does not close it', () => {
    const over = commitPayment(NET, { amounts: { cash: NET + 1, check: 0, transfer: 0, card: 0 }, checkNumber: '' }, 'cash');
    expect(over).toMatchObject({ unbalanced: true, next: 'cash' });
    expect(over.state.amounts).toEqual({ cash: 0, check: 0, transfer: 0, card: 0 });
    const short = commitPayment(NET, { amounts: { cash: 1, check: 0, transfer: 0, card: 2 }, checkNumber: '' }, 'card');
    expect(short.unbalanced).toBe(true);
  });

  it('rejects a zero or non-numeric check number', () => {
    const state = { amounts: { cash: 0, check: NET, transfer: 0, card: 0 }, checkNumber: '0' };
    expect(readyPayment(NET, state)).toBeNull();
    expect(readyPayment(NET, { ...state, checkNumber: '12A' })).toBeNull();
    expect(readyPayment(NET, { ...state, checkNumber: '0123' })?.checkNumber).toBe('0123');
  });

  it('shows only Efectivo at first, and each form appears as the previous one is lowered (FPch/FPtx/FPdat .Visible)', () => {
    let state = initialPayment(NET);
    expect(visibleMethods(state)).toEqual(['cash']);
    state = commitPayment(NET, { ...state, amounts: { ...state.amounts, cash: 1000000 } }, 'cash').state;
    expect(visibleMethods(state)).toEqual(['cash', 'check']);
    state = commitPayment(NET, { amounts: { ...state.amounts, check: 1000000 }, checkNumber: '7' }, 'checkNumber').state;
    expect(visibleMethods(state)).toEqual(['cash', 'check', 'transfer']);
    state = commitPayment(NET, { ...state, amounts: { ...state.amounts, transfer: 1000000 } }, 'transfer').state;
    expect(visibleMethods(state)).toEqual(['cash', 'check', 'transfer', 'card']);
    expect(state.amounts.card).toBe(NET - 3000000);
  });

  it('raising Efectivo back to the total hides and zeroes the forms on its right', () => {
    const state = { amounts: { cash: 1000000, check: 1000000, transfer: 1000000, card: NET - 3000000 }, checkNumber: '7' };
    const step = commitPayment(NET, { ...state, amounts: { ...state.amounts, cash: NET } }, 'cash');
    expect(step.state.amounts).toEqual({ cash: NET, check: 0, transfer: 0, card: 0 });
    expect(visibleMethods(step.state)).toEqual(['cash']);
  });
});
