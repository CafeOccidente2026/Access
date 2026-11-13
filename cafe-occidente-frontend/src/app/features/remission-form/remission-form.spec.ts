import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import pdfMake from 'pdfmake/build/pdfmake';
import { describe, expect, it, vi } from 'vitest';

import content from '../../../../public/assets/data/remission-form.json';
import { RemissionFormComponent } from './remission-form';
import { ContentService } from '../../core/services/content.service';
import { GrowerService } from '../../core/services/grower.service';
import { InventoryService } from '../../core/services/inventory.service';
import { InventoryMovementResponse, RemissionResponse } from '../../core/models/inventory.model';

/** C7 (prueba del 11-nov, punto 7): Registrar Salidas con casillas, como Genera Remesa. */
describe('RemissionFormComponent', () => {
  const base = {
    purchaseModule: 'DRY_COFFEE' as const, purchaseId: 1, agencyId: 1, agencyName: 'Buesaco',
    productCode: '0110002000002', specialType: 'RN', purchaseDate: '2026-09-23', healthyPercentage: 90, inventoryValue: 8661600,
  };
  const movements: InventoryMovementResponse[] = [
    { ...base, id: 10, invoiceNumber: 27867, sacos: 10, grossKg: 1250, netKg: 1200, remainingKg: 700, idNumber: '990514', fundCode: 'RP' },
    { ...base, id: 11, invoiceNumber: 27868, sacos: 5, grossKg: 520, netKg: 500, remainingKg: 0, idNumber: '990514', fundCode: 'RP' },
    { ...base, id: 12, invoiceNumber: 27869, sacos: 4, grossKg: 310, netKg: 300, remainingKg: 300, idNumber: '99197019', fundCode: 'LF' },
  ];
  const saved: RemissionResponse = {
    id: 1, remissionNumber: 1, agencyId: 1, agencyName: 'Buesaco', remissionDate: '2026-09-24', destination: 'Bodega central',
    conductorIdNumber: null, conductorName: null, transportCompany: null, vehiclePlate: null, exported: false,
    displayNumber: 'SDBU-RP-0001', lines: [{ id: 1, inventoryMovementId: 10, quantity: 300, unitValue: 7218, outputValue: 2165400 }],
  } as unknown as RemissionResponse;

  let createSpy: ReturnType<typeof vi.fn>;
  let component: RemissionFormComponent;
  let growers: { findByIdNumber: ReturnType<typeof vi.fn>; createConductor: ReturnType<typeof vi.fn> };

  function setup() {
    createSpy = vi.fn(() => of(saved));
    growers = {
      findByIdNumber: vi.fn(() => of({ id: 1, idNumber: '9935221', firstName: 'CONDUCTOR', lastName: 'PRUEBA' })),
      createConductor: vi.fn(() => of({ id: 2, idNumber: '9935221', firstName: 'CONDUCTOR', lastName: 'NUEVO' })),
    };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ContentService, useValue: { loadJson: () => of(content) } },
        { provide: InventoryService, useValue: { listMovements: () => of(movements), createRemission: createSpy } },
        { provide: GrowerService, useValue: growers },
      ],
    });
    component = TestBed.createComponent(RemissionFormComponent).componentInstance;
  }

  const row = (id: number) => movements.find((m) => m.id === id)!;

  it('lists only the entries with balance of the chosen fund, with their cédula', () => {
    setup();
    expect(component.rows().map((m) => [m.invoiceNumber, m.idNumber])).toEqual([[27867, '990514']]);

    component.toggle(row(10));
    component.setFund('LF');
    expect(component.rows().map((m) => m.invoiceNumber)).toEqual([27869]);
    // Una remision es de un solo fondo: al cambiarlo se quitan las marcas.
    expect(component.isPicked(row(10))).toBe(false);
  });

  it('checking an entry takes its whole balance and the sacos and gross kilos of the purchase', () => {
    setup();
    component.toggle(row(10));

    expect(component.pickValue(row(10), 'quantity')).toBe('700');
    expect(component.pickValue(row(10), 'sacos')).toBe('10');
    expect(component.pickValue(row(10), 'grossKg')).toBe('1250');
    expect(component.totals()).toEqual({ quantity: 700, sacos: 10, grossKg: 1250 });

    component.toggle(row(10));
    expect(component.isPicked(row(10))).toBe(false);
    expect(component.canSave()).toBe(false);
  });

  it('a partial exit edits the quantity, sacos and gross kilos, and the totals follow', () => {
    setup();
    component.toggle(row(10));
    component.setPick(row(10), 'quantity', '300');
    component.setPick(row(10), 'sacos', '4');
    component.setPick(row(10), 'grossKg', '305');

    expect(component.totals()).toEqual({ quantity: 300, sacos: 4, grossKg: 305 });

    component.save();
    expect(createSpy).toHaveBeenCalledWith(
      expect.objectContaining({ lines: [{ inventoryMovementId: 10, quantity: 300, sacos: 4, grossKg: 305 }] }),
    );
    expect(component.result()?.displayNumber).toBe('SDBU-RP-0001');
  });

  it('cannot save without a checked entry', () => {
    setup();
    component.save();
    expect(component.canSave()).toBe(false);
    expect(createSpy).not.toHaveBeenCalled();
  });

  it('two clicks on Guardar make a single remission while the first request is in progress', () => {
    setup();
    const pending = new Subject<RemissionResponse>();
    createSpy.mockReturnValue(pending);
    component.toggle(row(10));

    component.save();
    component.save();

    expect(createSpy).toHaveBeenCalledTimes(1);
    expect(component.saving()).toBe(true);
    pending.next(saved);
    pending.complete();
    expect(component.saving()).toBe(false);
  });

  it('shows the reason of the server (e.g. the balance was exceeded)', () => {
    setup();
    createSpy.mockReturnValue(throwError(() => ({ error: { message: 'La salida no puede ser superior al saldo disponible' } })));
    component.toggle(row(10));
    component.setPick(row(10), 'quantity', '9999');

    component.save();

    expect(component.errorMessage()).toBe('La salida no puede ser superior al saldo disponible');
  });

  it('print() says so when the PDF cannot be built, instead of leaving the promise unhandled', async () => {
    setup();
    const createPdf = vi.spyOn(pdfMake, 'createPdf').mockImplementation(() => {
      throw new Error('pdfmake');
    });
    component.toggle(row(10));
    component.save();

    component.print();

    await vi.waitFor(() => expect(component.errorMessage()).toBe(content.messages.pdfError));
    createPdf.mockRestore();
  });

  it('a new remission clears the marks and the result', () => {
    setup();
    component.toggle(row(10));
    component.save();

    component.reset();

    expect(component.result()).toBeNull();
    expect(component.isPicked(row(10))).toBe(false);
  });

  it('the conductor is looked up by cédula; an unknown one can be added from here', () => {
    setup();
    component.conductorIdNumber.set('9935221');
    component.onConductorBlur();
    expect(component.conductor()?.firstName).toBe('CONDUCTOR');

    growers.findByIdNumber.mockReturnValue(throwError(() => ({ status: 404 })));
    component.onConductorBlur();
    expect(component.conductorNotFound()).toBe(true);

    component.openNewConductorForm();
    component.saveNewConductor();
    expect(growers.createConductor).not.toHaveBeenCalled();
    component.newConductorFirstName.set('CONDUCTOR');
    component.newConductorLastName.set('NUEVO');
    component.saveNewConductor();
    expect(component.conductor()?.lastName).toBe('NUEVO');
    expect(component.showNewConductorForm()).toBe(false);

    growers.createConductor.mockReturnValue(throwError(() => ({ error: { message: 'Ya existe' } })));
    component.openNewConductorForm();
    component.newConductorFirstName.set('X');
    component.newConductorLastName.set('Y');
    component.saveNewConductor();
    expect(component.newConductorError()).toBe('Ya existe');

    component.conductorIdNumber.set(' ');
    component.onConductorBlur();
    expect(component.conductor()).toBeNull();
  });

  it('shows the date as day/month/year and the saved remission with its number', () => {
    setup();
    expect(component.date('2026-09-23')).toBe('23/09/2026');
    expect(component.savedText(saved)).toBe('Remisión N° SDBU-RP-0001 registrada correctamente.');
    component.print();
    expect(component.errorMessage()).toBeNull();
  });
});
