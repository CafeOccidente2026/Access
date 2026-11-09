import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { describe, expect, it } from 'vitest';

import dry from '../../../../../public/assets/data/purchase-form-dry.json';
import { PurchaseFormContent } from '../../../core/models';
import { PurchaseFormViewComponent } from './purchase-form-view';

describe('PurchaseFormViewComponent', () => {
  it('a complete payment sends the focus to Imprimir, so Enter prints (prueba del 9-nov, punto 8)', async () => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(PurchaseFormViewComponent);
    document.body.appendChild(fixture.nativeElement);
    fixture.componentRef.setInput('content', {
      ...(dry as unknown as PurchaseFormContent),
      paymentNetToPay: 1000,
      reprintButtonLabel: 'Imprimir',
    });
    fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;

    host.querySelector<HTMLInputElement>('[data-field-key="payCash"]')!
      .dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    await new Promise((resolve) => setTimeout(resolve));

    expect(document.activeElement?.textContent?.trim()).toBe('Imprimir');
    fixture.nativeElement.remove();
  });
});
