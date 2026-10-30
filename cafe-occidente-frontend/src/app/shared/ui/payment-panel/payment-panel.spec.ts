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

  it('starts with only Efectivo and the total; lowering a form and leaving it shows the next one beside it', async () => {
    const fixture = TestBed.createComponent(PaymentPanelComponent);
    fixture.componentRef.setInput('definition', definition);
    fixture.componentRef.setInput('netToPay', 1000);
    fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;
    const keys = () => Array.from(host.querySelectorAll('input[data-field-key]')).map((i) => i.getAttribute('data-field-key'));
    const field = (key: string) => host.querySelector<HTMLInputElement>(`[data-field-key="${key}"]`)!;
    const lower = (key: string, value: string) => {
      field(key).value = value;
      field(key).dispatchEvent(new Event('input'));
      field(key).dispatchEvent(new Event('blur'));
      fixture.detectChanges();
    };

    expect(keys()).toEqual(['payCash']);
    expect(field('payCash').value).toBe('1.000');

    lower('payCash', '600');
    expect(keys()).toEqual(['payCash', 'payCheck', 'payCheckNumber']);
    expect(field('payCheck').value).toBe('400');

    lower('payCheck', '100');
    expect(keys()).toEqual(['payCash', 'payCheck', 'payCheckNumber', 'payTransfer']);
    expect(field('payTransfer').value).toBe('300');

    lower('payTransfer', '200');
    expect(keys()).toEqual(['payCash', 'payCheck', 'payCheckNumber', 'payTransfer', 'payCard']);
    expect(field('payCard').value).toBe('100');
    expect(field('payCard').readOnly).toBe(true);

    lower('payCash', '1000');
    expect(keys()).toEqual(['payCash']);
  });
});
