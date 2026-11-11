import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it, vi } from 'vitest';

import { ConfirmDialogComponent } from './confirm-dialog';

describe('ConfirmDialogComponent', () => {
  it('opens with the focus on Aceptar, so Enter accepts it (prueba del 9-nov, puntos 8 y 9)', () => {
    const fixture = TestBed.createComponent(ConfirmDialogComponent);
    document.body.appendChild(fixture.nativeElement);
    fixture.componentRef.setInput('message', 'El Factor supera el umbral');
    fixture.componentRef.setInput('confirmLabel', 'Aceptar');
    fixture.componentRef.setInput('cancelLabel', 'Cancelar');
    fixture.detectChanges();

    expect(document.activeElement?.textContent?.trim()).toBe('Aceptar');
    fixture.nativeElement.remove();
  });

  function open(message: string) {
    const fixture = TestBed.createComponent(ConfirmDialogComponent);
    document.body.appendChild(fixture.nativeElement);
    fixture.componentRef.setInput('message', message);
    fixture.detectChanges();
    return fixture;
  }

  it('prueba del 10-nov: while open, a field that takes the focus behind it gives it back to Aceptar', () => {
    vi.useFakeTimers();
    const field = document.body.appendChild(document.createElement('input'));
    const fixture = open('Digite el celular del vendedor.');
    field.focus();
    expect(document.activeElement?.textContent?.trim()).toBe('Aceptar');

    // Al cerrar, el foco va a ese campo (a donde iba la pantalla).
    fixture.nativeElement.remove();
    fixture.destroy();
    vi.runAllTimers();
    expect(document.activeElement).toBe(field);
    field.remove();
    vi.useRealTimers();
  });

  it('on close, if the field it came from is gone, the focus goes to the first editable field of the screen', () => {
    vi.useFakeTimers();
    const screen = document.body.appendChild(document.createElement('div'));
    screen.innerHTML = '<input readonly id="agency" /><input id="fund" /><button id="print">Imprimir</button>';
    (screen.querySelector('#print') as HTMLElement).focus();
    const fixture = open('Compra registrada.');
    screen.querySelector('#print')!.remove();

    fixture.nativeElement.remove();
    fixture.destroy();
    vi.runAllTimers();
    expect(document.activeElement).toBe(screen.querySelector('#fund'));
    screen.remove();
    vi.useRealTimers();
  });
});

/** Como purchase-form.html: "Compra registrada" y "No se pudo generar la factura" pueden quedar abiertos a la vez. */
@Component({
  standalone: true,
  imports: [ConfirmDialogComponent],
  template: `
    <input id="field" />
    @if (saved()) { <app-confirm-dialog id="saved" message="Compra registrada." /> }
    @if (failed()) { <app-confirm-dialog id="failed" message="No se pudo generar la factura" /> }
  `,
})
class TwoDialogsHost {
  readonly saved = signal(true);
  readonly failed = signal(false);
}

describe('ConfirmDialogComponent con dos avisos (PR #82)', () => {
  it('two dialogs open at once do not take the focus from each other; the last one keeps it', () => {
    vi.useFakeTimers();
    const fixture = TestBed.createComponent(TwoDialogsHost);
    document.body.appendChild(fixture.nativeElement);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const accept = (id: string) => el.querySelector(`#${id} button`) as HTMLElement;
    const field = el.querySelector('#field') as HTMLElement;

    fixture.componentInstance.failed.set(true);
    fixture.detectChanges();
    expect(document.activeElement).toBe(accept('failed'));
    // Sin la pila esto era un ciclo infinito (RangeError: Maximum call stack size exceeded).
    accept('saved').focus();
    field.focus();
    expect(document.activeElement).toBe(accept('failed'));

    // Cerrado el de arriba, el de abajo vuelve a cuidar el foco.
    fixture.componentInstance.failed.set(false);
    fixture.detectChanges();
    vi.runAllTimers();
    field.focus();
    expect(document.activeElement).toBe(accept('saved'));

    fixture.componentInstance.saved.set(false);
    fixture.detectChanges();
    vi.runAllTimers();
    fixture.nativeElement.remove();
    vi.useRealTimers();
  });
});
