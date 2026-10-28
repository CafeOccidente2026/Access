import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { QuotaBillingFormComponent } from './quota-billing-form';
import { ContentService } from '../../../core/services/content.service';
import { DryCoffeePurchaseService } from '../../../core/services/dry-coffee-purchase.service';
import { GrowerService } from '../../../core/services/grower.service';
import { PurchaseFormContent } from '../../../core/models/purchase-form-content.model';

describe('QuotaBillingFormComponent - pesos de la muestra y castigo', () => {
  const content: PurchaseFormContent = {
    windowTitle: 'Cupo',
    theme: 'teal',
    topFields: [],
    identificationFields: [],
    qualityFields: [{ key: 'healthyStoredWeight', label: 'Peso Alm Sana', type: 'currency' }],
  } as PurchaseFormContent;

  function setup() {
    const qualitySpy = vi.fn((total?: number, husk?: number) =>
      of(
        husk === undefined
          ? { wastePercentage: 20, defectivePercentage: null, healthyPercentage: null }
          : { wastePercentage: null, defectivePercentage: 4, healthyStoredWeight: 190, healthyPercentage: 92.11 },
      ),
    );
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ContentService, useValue: { loadJson: () => of(content) } },
        { provide: DryCoffeePurchaseService, useValue: { funds: () => of([]), qualityPercentages: qualitySpy } },
        { provide: GrowerService, useValue: {} },
      ],
    });
    const component = TestBed.createComponent(QuotaBillingFormComponent).componentInstance;
    return { component, qualitySpy };
  }

  it('Q2: the healthy almond comes calculated from the total and husk weights', () => {
    const { component, qualitySpy } = setup();
    component.model['totalStoredWeight'] = '200';
    component.onFieldCommitted('totalStoredWeight');
    component.model['totalHuskWeight'] = '10';
    component.onFieldCommitted('totalHuskWeight');

    expect(qualitySpy).toHaveBeenLastCalledWith(200, 10);
    expect(component.model['healthyStoredWeight']).toBe('190');
    expect(component.model['factor']).toBe('92.11');
    const field = component.viewContent()?.qualityFields?.find((f) => f.key === 'healthyStoredWeight');
    expect(field?.readonly).toBe(true);
    expect(field?.value).toBe('190');
  });

  it('Q1: a positive penalty is shown as negative', () => {
    const { component } = setup();
    component.model['penalty'] = '23';
    component.onFieldCommitted('penalty');

    expect(component.model['penalty']).toBe('-23');
  });
});
