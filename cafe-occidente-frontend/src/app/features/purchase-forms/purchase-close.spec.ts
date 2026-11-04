import { Location } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Type } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { ContentService } from '../../core/services/content.service';
import { DryCoffeeFormComponent } from './dry-coffee/dry-coffee-form';
import { FuturePurchaseFormComponent } from './future-purchase/future-purchase-form';
import { GreenCoffeeFormComponent } from './green-coffee/green-coffee-form';
import { HuskFormComponent } from './husk/husk-form';

/** Lo que el cierre con datos sin imprimir expone a la plantilla y al guard de la ruta. */
interface ClosableForm {
  model: Record<string, string>;
  pendingClose: boolean;
  onClose(): void;
  canDeactivate(): boolean | Promise<boolean>;
  confirmClose(): void;
  cancelClose(): void;
}

/**
 * La X y las salidas por menú o atrás de seco, verde, pasilla y compra futura: con datos sin imprimir piden
 * confirmación; sin datos cierran. Red para mover esa lógica a un solo lugar sin cambiar lo que hace.
 */
describe.each([
  ['Café Seco', DryCoffeeFormComponent],
  ['Café Verde', GreenCoffeeFormComponent],
  ['Pasilla', HuskFormComponent],
  ['Compra Futura', FuturePurchaseFormComponent],
] as [string, Type<unknown>][])('%s: cerrar con datos sin imprimir', (_, type) => {
  function setup() {
    const back = vi.fn();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ContentService, useValue: { loadJson: () => of({ messages: {} }) } },
        { provide: Location, useValue: { back } },
      ],
    });
    const form = TestBed.createComponent(type).componentInstance as ClosableForm;
    return { form, back };
  }

  it('without data the X closes at once and the route lets you leave', () => {
    const { form, back } = setup();
    expect(form.canDeactivate()).toBe(true);
    form.onClose();
    expect(form.pendingClose).toBe(false);
    expect(back).toHaveBeenCalledTimes(1);
  });

  it('with data the X asks; Cancel keeps the data and Close discards it and goes back', () => {
    const { form, back } = setup();
    form.model['idPart1'] = '210514';

    form.onClose();
    expect(form.pendingClose).toBe(true);
    form.cancelClose();
    expect(form.pendingClose).toBe(false);
    expect(form.model['idPart1']).toBe('210514');
    expect(back).not.toHaveBeenCalled();

    form.onClose();
    form.confirmClose();
    expect(form.pendingClose).toBe(false);
    expect(form.model['idPart1']).toBeUndefined();
    expect(back).toHaveBeenCalledTimes(1);
    expect(form.canDeactivate()).toBe(true);
  });

  it('leaving by menu or back with data waits for the answer: Cancel stays, Close leaves without going back twice', async () => {
    const { form, back } = setup();
    form.model['idPart1'] = '210514';

    const stay = form.canDeactivate() as Promise<boolean>;
    expect(form.pendingClose).toBe(true);
    form.cancelClose();
    expect(await stay).toBe(false);
    expect(form.pendingClose).toBe(false);

    const leave = form.canDeactivate() as Promise<boolean>;
    form.confirmClose();
    expect(await leave).toBe(true);
    expect(form.pendingClose).toBe(false);
    expect(back).not.toHaveBeenCalled();
  });
});
