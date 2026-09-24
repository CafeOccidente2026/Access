import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { describe, expect, it } from 'vitest';

import { InventoryConsultaComponent } from './inventory-consulta';
import { InventoryService } from '../../core/services/inventory.service';
import { InventoryMovementResponse } from '../../core/models/inventory.model';

describe('InventoryConsultaComponent', () => {
  const movements: InventoryMovementResponse[] = [
    {
      id: 1, purchaseModule: 'DRY_COFFEE', purchaseId: 1, agencyId: 1, agencyName: 'Buesaco',
      productCode: '0110002000002', specialType: 'RN', invoiceNumber: 27867, purchaseDate: '2026-09-23',
      sacos: 10, grossKg: 1250, netKg: 1200, remainingKg: 700, healthyPercentage: 90, inventoryValue: 8661600,
    },
    {
      id: 2, purchaseModule: 'OTHER_COFFEE', purchaseId: 1, agencyId: 1, agencyName: 'Buesaco',
      productCode: '0110002000002', specialType: 'RN', invoiceNumber: 27868, purchaseDate: '2026-09-23',
      sacos: 5, grossKg: 520, netKg: 500, remainingKg: 0, healthyPercentage: 88, inventoryValue: 1510500,
    },
  ];

  function setup() {
    localStorage.setItem('cafeoccidente.agencyId', '1');
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: InventoryService, useValue: { listMovements: () => of(movements) } },
      ],
    });
    return TestBed.createComponent(InventoryConsultaComponent).componentInstance;
  }

  it('shows every movement by default', () => {
    const component = setup();
    expect(component.visibleMovements()).toHaveLength(2);
  });

  it('filters out movements with no remaining balance when the checkbox is on', () => {
    const component = setup();
    component.onlyWithBalance.set(true);
    expect(component.visibleMovements()).toHaveLength(1);
    expect(component.visibleMovements()[0].id).toBe(1);
  });
});
