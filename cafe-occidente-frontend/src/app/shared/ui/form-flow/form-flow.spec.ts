import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it, vi } from 'vitest';

import { FormFlowDirective } from './form-flow';

@Component({
  standalone: true,
  imports: [FormFlowDirective],
  template: `
    <div appFormFlow>
      <input id="fixed" readonly />
      <input id="a" />
      <input type="radio" name="g" id="r1" />
      <input type="radio" name="g" id="r2" checked />
      <select id="s"><option>F</option></select>
      <input type="date" id="d" />
      <button data-flow-submit (click)="saved = saved + 1">Guardar</button>
    </div>
  `,
})
class Host {
  saved = 0;
}

const enter = (el: Element) => el.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }));

describe('appFormFlow', () => {
  it('focuses the first editable field, Enter walks the fields and the last one saves', () => {
    vi.useFakeTimers();
    const fixture = TestBed.createComponent(Host);
    document.body.appendChild(fixture.nativeElement);
    fixture.detectChanges();
    const el = (id: string) => fixture.nativeElement.querySelector(`#${id}`) as HTMLElement;

    expect(document.activeElement).toBe(el('a'));
    expect(el('a').classList).toContain('field-flash');

    enter(el('a'));
    expect(document.activeElement).toBe(el('r2')); // un radio group = un campo (el marcado)
    enter(el('r2'));
    expect(document.activeElement).toBe(el('s'));
    enter(el('s'));
    expect(document.activeElement).toBe(el('d'));
    expect(fixture.componentInstance.saved).toBe(0);
    enter(el('d'));
    expect(fixture.componentInstance.saved).toBe(1);

    vi.advanceTimersByTime(1000);
    expect(el('a').classList).not.toContain('field-flash');
    vi.useRealTimers();
    fixture.nativeElement.remove();
  });
});
