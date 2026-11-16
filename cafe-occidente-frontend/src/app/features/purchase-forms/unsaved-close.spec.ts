import { Location } from '@angular/common';
import { Injector, runInInjectionContext } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { ControlRecordService } from '../../core/services/control-record.service';
import { UnsavedCloseForm } from './unsaved-close';

/** Pantalla mínima: el anuncio vigente y la huella del Registro de Control los pone cada prueba. */
class TestForm extends UnsavedCloseForm {
  model: Record<string, string> = { idPart1: '990514' };
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
});
