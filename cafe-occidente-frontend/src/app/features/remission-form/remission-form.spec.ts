import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { RemissionFormComponent } from './remission-form';
import { GrowerService } from '../../core/services/grower.service';
import { InventoryService } from '../../core/services/inventory.service';
import { InventoryMovementResponse } from '../../core/models/inventory.model';

describe('RemissionFormComponent', () => {
  const movements: InventoryMovementResponse[] = [
    {
      id: 10, purchaseModule: 'DRY_COFFEE', purchaseId: 1, agencyId: 1, agencyName: 'Buesaco',
      productCode: '0110002000002', specialType: 'RN', invoiceNumber: 27867, purchaseDate: '2026-09-23',
      sacos: 10, grossKg: 1250, netKg: 1200, remainingKg: 700, healthyPercentage: 90, inventoryValue: 8661600,
    },
    {
      id: 11, purchaseModule: 'OTHER_COFFEE', purchaseId: 1, agencyId: 1, agencyName: 'Buesaco',
      productCode: '0110002000002', specialType: 'RN', invoiceNumber: 27868, purchaseDate: '2026-09-23',
      sacos: 5, grossKg: 520, netKg: 500, remainingKg: 0, healthyPercentage: 88, inventoryValue: 1510500,
    },
  ];

  let createSpy: ReturnType<typeof vi.fn>;
  let component: RemissionFormComponent;

  function setup() {
    createSpy = vi.fn(() => of({ id: 1, remissionNumber: 1, agencyId: 1, agencyName: 'Buesaco', remissionDate: '2026-09-24', destination: 'Bodega central', conductorIdNumber: null, conductorName: null, transportCompany: null, vehiclePlate: null, exported: false, lines: [{ id: 1, inventoryMovementId: 10, quantity: 300, unitValue: 7218, outputValue: 2165400 }] }));
    localStorage.setItem('cafeoccidente.agencyId', '1');
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: InventoryService, useValue: { listMovements: () => of(movements), createRemission: createSpy } },
        { provide: GrowerService, useValue: { findByIdNumber: vi.fn(() => of({ id: 1, idNumber: '210514', firstName: 'PAZ', lastName: 'CASTELBLANCO', transportCompany: 'Transportes X', vehiclePlate: 'ABC123' })) } },
      ],
    });
    component = TestBed.createComponent(RemissionFormComponent).componentInstance;
  }

  it('only offers movements with remaining balance for a new line', () => {
    setup();
    expect(component.availableMovements()).toHaveLength(1);
    expect(component.availableMovements()[0].id).toBe(10);
  });

  it('cannot save with no lines added', () => {
    setup();
    expect(component.canSave()).toBe(false);
  });

  it('addLine() appends a draft line and canSave() becomes true', () => {
    setup();
    component.selectedMovementId.set(10);
    component.draftQuantity.set('300');
    component.addLine();

    expect(component.lines()).toHaveLength(1);
    expect(component.canSave()).toBe(true);
  });

  it('save() sends the accumulated lines and shows the saved remission', () => {
    setup();
    component.selectedMovementId.set(10);
    component.draftQuantity.set('300');
    component.addLine();

    component.save();

    expect(createSpy).toHaveBeenCalledWith(
      expect.objectContaining({ agencyId: 1, lines: [{ inventoryMovementId: 10, quantity: 300 }] }),
    );
    expect(component.result()?.remissionNumber).toBe(1);
  });

  it('shows the real backend error (e.g. saldo excedido) instead of a generic one', () => {
    setup();
    createSpy.mockReturnValue(
      throwError(() => ({ error: { message: 'La salida no puede ser superior al saldo disponible (saldo: 700 kg, factura 27867)' } })),
    );
    component.selectedMovementId.set(10);
    component.draftQuantity.set('9999');
    component.addLine();

    component.save();

    expect(component.errorMessage()).toBe('La salida no puede ser superior al saldo disponible (saldo: 700 kg, factura 27867)');
  });
});
