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

/** Registrar Salidas con casillas; respuestas del area (23-nov): cada factura sale completa y genera su remesa. */
describe('RemissionFormComponent', () => {
  const base = {
    purchaseModule: 'DRY_COFFEE' as const, purchaseId: 1, agencyId: 1, agencyName: 'Buesaco',
    productCode: '0110002000002', specialType: 'RN', purchaseDate: '2026-09-23', healthyPercentage: 90, inventoryValue: 8661600,
    remitted: false,
  };
  const movements: InventoryMovementResponse[] = [
    { ...base, id: 10, invoiceNumber: 27867, sacos: 10, grossKg: 1250, netKg: 1200, remainingKg: 700, idNumber: '990514', fundCode: 'RP' },
    { ...base, id: 11, invoiceNumber: 27868, sacos: 5, grossKg: 520, netKg: 500, remainingKg: 0, idNumber: '990514', fundCode: 'RP' },
    { ...base, id: 12, invoiceNumber: 27869, sacos: 4, grossKg: 310, netKg: 300, remainingKg: 300, idNumber: '99197019', fundCode: 'LF' },
    { ...base, id: 13, invoiceNumber: 27860, purchaseDate: '2026-09-20', sacos: 1, grossKg: 52, netKg: 50, remainingKg: 50, idNumber: '990514', fundCode: 'RP' },
    { ...base, id: 14, invoiceNumber: 27870, specialType: 'ESTANDAR', sacos: 2, grossKg: 101, netKg: 100, remainingKg: 100, idNumber: '990514', fundCode: 'RP' },
    // Ya esta en una remesa (de Genera Remesa o de Access): no se puede volver a despachar.
    { ...base, id: 15, invoiceNumber: 27850, sacos: 3, grossKg: 150, netKg: 145, remainingKg: 145, idNumber: '990514', fundCode: 'RP', remitted: true },
  ];
  const saved: RemissionResponse = {
    id: 1, remissionNumber: 1, agencyId: 1, agencyName: 'Buesaco', remissionDate: '2026-09-24', destination: 'Bodega central',
    conductorIdNumber: null, conductorName: null, transportCompany: null, vehiclePlate: null, exported: false,
    displayNumber: 'SDBU-125', status: 'VIGENTE', remittanceId: 9, fromAccess: false, lines: [{ id: 1, inventoryMovementId: 10, quantity: 300, unitValue: 7218, outputValue: 2165400 }],
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

  it('demo del 23-nov: no list until the Tipo de café is chosen; then only that type, by date and invoice, without the remitted ones', () => {
    setup();
    expect(component.rows()).toEqual([]);
    expect(component.specials()).toEqual(['ESTANDAR', 'RN']);

    component.setSpecial('RN');
    expect(component.rows().map((m) => [m.invoiceNumber, m.idNumber])).toEqual([[27860, '990514'], [27867, '990514']]);
  });

  it('changing the Tipo de café reloads the list and clears what was checked', () => {
    setup();
    component.setSpecial('RN');
    component.toggle(row(10));

    component.setSpecial('ESTANDAR');
    expect(component.rows().map((m) => m.invoiceNumber)).toEqual([27870]);
    expect(component.isPicked(row(10))).toBe(false);
  });

  it('lists only the entries with balance of the chosen fund; changing the fund clears the marks and the type', () => {
    setup();
    component.setSpecial('RN');
    component.toggle(row(10));
    component.setFund('LF');
    // Una remision es de un solo fondo: al cambiarlo se quitan las marcas y se vuelve a elegir el tipo.
    expect(component.isPicked(row(10))).toBe(false);
    expect(component.rows()).toEqual([]);
    expect(component.specials()).toEqual(['RN']);

    component.setSpecial('RN');
    expect(component.rows().map((m) => m.invoiceNumber)).toEqual([27869]);
  });

  /** Marca facturas y elige el tipo de empaque: lo minimo para guardar. */
  function pick(...ids: number[]) {
    ids.forEach((id) => component.toggle(row(id)));
    component.packagingType.set('USADO');
  }

  it('checking an invoice takes its whole balance with its sacos and gross kilos; the packaging suggests the sacos', () => {
    setup();
    component.toggle(row(10));
    component.toggle(row(13));

    expect(component.totals()).toEqual({ quantity: 750, sacos: 11, grossKg: 1302 });
    expect(component.packagingQuantity()).toBe('11');

    component.toggle(row(13));
    expect(component.packagingQuantity()).toBe('10');
    component.toggle(row(10));
    expect(component.isPicked(row(10))).toBe(false);
    expect(component.canSave()).toBe(false);
  });

  it('saves the checked invoices with the packaging chosen; the quantity of packaging can be changed', () => {
    setup();
    pick(10, 13);
    component.packagingQuantity.set('8');

    component.save();

    expect(createSpy).toHaveBeenCalledWith(expect.objectContaining({
      lines: [{ inventoryMovementId: 10 }, { inventoryMovementId: 13 }], packagingQuantity: 8, packagingType: 'USADO',
    }));
    expect(component.result()?.displayNumber).toBe('SDBU-125');
  });

  it('cannot save without a checked invoice, a quantity of packaging or its type', () => {
    setup();
    component.save();
    component.toggle(row(10));
    expect(component.canSave()).toBe(false);
    component.packagingType.set('NUEVO');
    expect(component.canSave()).toBe(true);
    component.packagingQuantity.set('0');
    expect(component.canSave()).toBe(false);
    component.save();
    expect(createSpy).not.toHaveBeenCalled();
  });

  it('two clicks on Guardar make a single remission while the first request is in progress', () => {
    setup();
    const pending = new Subject<RemissionResponse>();
    createSpy.mockReturnValue(pending);
    pick(10);

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
    createSpy.mockReturnValue(throwError(() => ({ error: { message: 'La factura 27867 ya no está pendiente de remesar' } })));
    pick(10);

    component.save();

    expect(component.errorMessage()).toBe('La factura 27867 ya no está pendiente de remesar');
  });

  it('print() says so when the PDF cannot be built, instead of leaving the promise unhandled', async () => {
    setup();
    const createPdf = vi.spyOn(pdfMake, 'createPdf').mockImplementation(() => {
      throw new Error('pdfmake');
    });
    pick(10);
    component.save();

    component.print();

    await vi.waitFor(() => expect(component.errorMessage()).toBe(content.messages.pdfError));
    createPdf.mockRestore();
  });

  it('a new remission clears the marks, the packaging and the result', () => {
    setup();
    pick(10);
    component.save();

    component.reset();

    expect(component.result()).toBeNull();
    expect(component.isPicked(row(10))).toBe(false);
    expect(component.packagingType()).toBe('');
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
    expect(component.savedText(saved)).toBe('Remisión N° SDBU-125 registrada correctamente. Su remesa tiene el mismo número.');
    component.print();
    expect(component.errorMessage()).toBeNull();
  });
});
