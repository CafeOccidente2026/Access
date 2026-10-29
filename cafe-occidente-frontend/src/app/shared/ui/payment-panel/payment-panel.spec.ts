import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';

import { PaymentPanelDefinition } from '../../../core/models/payment-method.model';
import { PaymentPanelComponent } from './payment-panel';

const definition: PaymentPanelDefinition = {
  heading: 'FORMAS\nDE\nPAGO',
  methods: [
    { key: 'cash', label: 'Efectivo' },
    { key: 'check', label: 'Cheque' },
    { key: 'transfer', label: 'Transfer' },
    { key: 'card', label: 'Datáfono' },
  ],
  checkNumberLabel: 'Núm Cheque',
  totalLabel: 'Tot F de Pago',
  unbalancedMessage: '',
  checkNumberMessage: '',
};

describe('PaymentPanelComponent', () => {
  it('6g3 (Consultar Compras): shows a saved payment, with its check number and total, read only', () => {
    const fixture = TestBed.createComponent(PaymentPanelComponent);
    fixture.componentRef.setInput('definition', definition);
    fixture.componentRef.setInput('saved', { cashAmount: 655579, checkAmount: 300000, transferAmount: 200000, cardAmount: 100000, checkNumber: '4321' });
    fixture.detectChanges();

    const inputs = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('input')) as HTMLInputElement[];
    expect(inputs.map((i) => i.value)).toEqual(['655.579', '300.000', '4321', '200.000', '100.000', '1.255.579']);
    expect(inputs.every((i) => i.readOnly)).toBe(true);
  });
});
