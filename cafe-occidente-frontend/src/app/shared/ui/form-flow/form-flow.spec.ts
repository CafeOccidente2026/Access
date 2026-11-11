import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it, vi } from 'vitest';

import { AppButtonComponent } from '../app-button/app-button';
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
    vi.advanceTimersByTime(0);
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

describe('appFormFlow con listas (prueba del 9-nov, segunda)', () => {
  it('choosing an option of a list or changing a select moves on to the next field (once the screen repaints)', () => {
    vi.useFakeTimers();
    const fixture = TestBed.createComponent(Host);
    document.body.appendChild(fixture.nativeElement);
    fixture.detectChanges();
    const el = (id: string) => fixture.nativeElement.querySelector(`#${id}`) as HTMLElement;

    // Un combobox avisa con flow-next al elegir una opcion (clic o Enter).
    el('a').dispatchEvent(new CustomEvent('flow-next', { bubbles: true }));
    vi.advanceTimersByTime(0);
    expect(document.activeElement).toBe(el('r2'));

    el('s').focus();
    el('s').dispatchEvent(new Event('change', { bubbles: true }));
    vi.advanceTimersByTime(0);
    expect(document.activeElement).toBe(el('d'));
    vi.useRealTimers();
    fixture.nativeElement.remove();
  });

  it('decision del 10-nov: an arrow only changes the option and keeps the focus; Enter moves on', () => {
    vi.useFakeTimers();
    const fixture = TestBed.createComponent(Host);
    document.body.appendChild(fixture.nativeElement);
    fixture.detectChanges();
    vi.advanceTimersByTime(0); // foco inicial de la pantalla
    const select = fixture.nativeElement.querySelector('#s') as HTMLElement;

    select.focus();
    select.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }));
    select.dispatchEvent(new Event('change', { bubbles: true }));
    vi.advanceTimersByTime(0);
    expect(document.activeElement).toBe(select);

    enter(select);
    expect(document.activeElement).toBe(fixture.nativeElement.querySelector('#d'));

    select.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
    select.focus();
    select.dispatchEvent(new Event('change', { bubbles: true }));
    vi.advanceTimersByTime(0);
    expect(document.activeElement).toBe(fixture.nativeElement.querySelector('#d'));
    vi.useRealTimers();
    fixture.nativeElement.remove();
  });
});

@Component({
  standalone: true,
  imports: [FormFlowDirective, AppButtonComponent],
  template: `
    <div appFormFlow>
      <input id="x" />
      <app-button data-flow-submit label="Generar" (pressed)="pressed = pressed + 1"></app-button>
    </div>
  `,
})
class HostWithAppButton {
  pressed = 0;
}

describe('appFormFlow con app-button', () => {
  it('Enter on the last field presses the app-button marked data-flow-submit', () => {
    const fixture = TestBed.createComponent(HostWithAppButton);
    document.body.appendChild(fixture.nativeElement);
    fixture.detectChanges();
    enter(fixture.nativeElement.querySelector('#x'));
    expect(fixture.componentInstance.pressed).toBe(1);
    fixture.nativeElement.remove();
  });
});

@Component({
  standalone: true,
  imports: [FormFlowDirective],
  template: `
    <div appFormFlow><input id="first" /></div>
    <div [appFormFlow]="false"><input id="second" /></div>
  `,
})
class HostWithTwoFlows {}

describe('appFormFlow con dos grupos', () => {
  it('a group marked [appFormFlow]="false" walks with Enter but does not take the first focus', () => {
    vi.useFakeTimers();
    const fixture = TestBed.createComponent(HostWithTwoFlows);
    document.body.appendChild(fixture.nativeElement);
    fixture.detectChanges();
    vi.advanceTimersByTime(0);
    expect(document.activeElement?.id).toBe('first');
    vi.useRealTimers();
    fixture.nativeElement.remove();
  });
});

@Component({
  standalone: true,
  imports: [FormFlowDirective],
  template: `<div appFormFlow><input readonly aria-label="Pto Compra" /><button data-flow-submit>Aceptar</button><button>Cancelar</button></div>`,
})
class ReportHost {}

describe('appFormFlow sin campos (prueba del 10-nov, informes de Suministros)', () => {
  it('with nothing to type, the first focus goes to the marked button, so Enter accepts', () => {
    vi.useFakeTimers();
    const fixture = TestBed.createComponent(ReportHost);
    document.body.appendChild(fixture.nativeElement);
    fixture.detectChanges();
    vi.advanceTimersByTime(0);
    expect(document.activeElement?.textContent).toBe('Aceptar');
    vi.useRealTimers();
    fixture.nativeElement.remove();
  });
});

