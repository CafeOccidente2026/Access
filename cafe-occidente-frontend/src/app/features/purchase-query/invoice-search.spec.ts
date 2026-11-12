import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it, vi } from 'vitest';

import { AnnulmentCandidate } from '../../core/models/purchase-annulment.model';
import content from '../../../../public/assets/data/purchase-query.json';
import { InvoiceBrowser } from './invoice-browser';
import { InvoiceSearchComponent, InvoiceSearchTexts } from './invoice-search';

/** La barra comun de Consultar Compras y Corregir Compra (arreglo de la duplicacion del #87). */
describe('InvoiceSearchComponent', () => {
  const dry = { module: 'DRY', id: 1, prefix: 'SDTA', invoiceNumber: 43908 } as AnnulmentCandidate;
  const husk = { module: 'HUSK', id: 2, prefix: 'SDTA', invoiceNumber: 43908 } as AnnulmentCandidate;

  function render(candidates: AnnulmentCandidate[]) {
    const browser = {
      candidates: signal(candidates),
      selected: signal(candidates[0] ?? null),
      search: vi.fn(),
      go: vi.fn(),
      choose: vi.fn(),
    };
    const fixture = TestBed.createComponent(InvoiceSearchComponent);
    fixture.componentRef.setInput('browser', browser as unknown as InvoiceBrowser);
    fixture.componentRef.setInput('texts', content as unknown as InvoiceSearchTexts);
    fixture.detectChanges();
    return { browser, el: fixture.nativeElement as HTMLElement };
  }

  it('busca la factura digitada y recorre con |◄ ◄ ► ►|', () => {
    const { browser, el } = render([dry]);
    const input = el.querySelector<HTMLInputElement>('[data-field-key="invoice"]')!;
    input.value = '43908';
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    expect(browser.search).toHaveBeenCalledWith(expect.any(Event), '43908');

    el.querySelector<HTMLButtonElement>('app-invoice-navigator button')!.click();
    expect(browser.go).toHaveBeenCalled();
  });

  it('solo ofrece elegir el modulo cuando la factura esta en varios', () => {
    expect(render([dry]).el.textContent).not.toContain(content.modules.HUSK);
    TestBed.resetTestingModule();

    const { browser, el } = render([dry, husk]);
    const buttons = [...el.querySelectorAll<HTMLButtonElement>('[data-testid="candidates"] button')];
    expect(buttons.map((b) => b.textContent?.trim())).toEqual([
      `${content.modules.DRY} · SDTA 43908`,
      `${content.modules.HUSK} · SDTA 43908`,
    ]);
    buttons[1].click();
    expect(browser.choose).toHaveBeenCalledWith(husk);
  });
});
