import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';

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
});
