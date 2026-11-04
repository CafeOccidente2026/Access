import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';

import { InvoiceNavigatorComponent, NavigatorButton } from './invoice-navigator';

const BUTTONS: NavigatorButton[] = [
  { direction: 'FIRST', label: '|◄', title: 'Primera compra' },
  { direction: 'PREV', label: '◄', title: 'Compra anterior' },
  { direction: 'NEXT', label: '►', title: 'Compra siguiente' },
  { direction: 'LAST', label: '►|', title: 'Última compra' },
];

describe('InvoiceNavigatorComponent', () => {
  it('shows |◄ ◄ ► ►| and tells which one was pressed', () => {
    const fixture = TestBed.createComponent(InvoiceNavigatorComponent);
    fixture.componentRef.setInput('buttons', BUTTONS);
    fixture.detectChanges();
    const pressed: string[] = [];
    fixture.componentInstance.navigate.subscribe((d) => pressed.push(d));

    const buttons = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button'));
    expect(buttons.map((b) => b.textContent?.trim())).toEqual(['|◄', '◄', '►', '►|']);
    expect(buttons[3].getAttribute('aria-label')).toBe('Última compra');
    buttons[2].click();
    buttons[0].click();
    expect(pressed).toEqual(['NEXT', 'FIRST']);
  });
});
