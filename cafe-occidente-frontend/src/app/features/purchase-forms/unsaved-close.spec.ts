import { Location } from '@angular/common';
import { Injector, runInInjectionContext, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { ControlRecordService } from '../../core/services/control-record.service';
import { UnsavedCloseForm } from './unsaved-close';

/** Pantalla mínima: el anuncio vigente y la huella del Registro de Control los pone cada prueba. */
class TestForm extends UnsavedCloseForm {
  model: Record<string, string> = { idPart1: '990514' };
  readonly errorMessage = signal<string | null>(null);
  announcement: string | null = 'SDBU-31';

  protected hasUnsavedInput(): boolean {
    return true;
  }

  protected override currentAnnouncement(): Observable<string> | null {
    return this.announcement === null ? null : of(this.announcement);
  }

  start(): void {
    this.rememberPrice();
  }

  reject(key: string, locked: Set<string>): boolean {
    return this.rejectedWeight(key, locked, { tareNotBelowGross: 'mal' });
  }

  refocus(focus: (key: string) => void): void {
    this.refocusRetry(focus);
  }
}

/** Hallazgo #6 (16-nov): al salir sin guardar se avisa si el precio cambió mientras se digitaba. */
describe('UnsavedCloseForm: precio cambiado', () => {
  function setup() {
    const stamp = vi.fn(() => of({ stamp: 'rc-1' }));
    TestBed.configureTestingModule({
      providers: [
        { provide: ControlRecordService, useValue: { stamp } },
        { provide: Location, useValue: { back: vi.fn() } },
      ],
    });
    const form = runInInjectionContext(TestBed.inject(Injector), () => new TestForm());
    return { form, stamp };
  }

  it('a new announcement of the same coffee adds the warning', () => {
    const { form } = setup();
    form.start();
    form.announcement = 'SDBU-32';
    form.onClose();
    expect(form.pendingClose()).toBe(true);
    expect(form.priceChanged()).toBe(true);
  });

  it('a change in the Registro de Control adds the warning too', () => {
    const { form, stamp } = setup();
    form.start();
    stamp.mockReturnValue(of({ stamp: 'rc-2' }));
    void form.canDeactivate();
    expect(form.priceChanged()).toBe(true);
  });

  it('without changes, or before the screen has an announcement, there is no extra warning', () => {
    const { form } = setup();
    form.onClose();
    expect(form.priceChanged()).toBe(false);

    form.start();
    form.onClose();
    expect(form.priceChanged()).toBe(false);

    form.announcement = null;
    form.start();
    form.onClose();
    expect(form.priceChanged()).toBe(false);
  });

  it('the close warning adds the price line only when the price changed', () => {
    const { form } = setup();
    const m = { closeWarning: 'Hay datos.', priceChangedWarning: 'Cambió el precio.' } as never;
    expect(form.closeMessage(m)).toBe('Hay datos.');
    form.priceChanged.set(true);
    expect(form.closeMessage(m)).toBe('Hay datos. Cambió el precio.');
  });

  it('a rejected weight is cleared and the focus goes back to it when the warning closes', () => {
    const { form } = setup();
    const focus = vi.fn();
    form.model = { grossKg: '2', tare: '2' };
    expect(form.reject('tare', new Set(['tare']))).toBe(true);
    expect(form.errorMessage()).toBe('mal');
    form.refocus(focus);
    expect(focus).toHaveBeenCalledWith('tare');
    form.refocus(focus);
    expect(focus).toHaveBeenCalledTimes(1);
    expect(form.reject('grossKg', new Set())).toBe(false);
  });
});
