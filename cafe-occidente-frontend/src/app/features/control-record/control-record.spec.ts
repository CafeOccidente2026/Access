import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { describe, expect, it } from 'vitest';

import { ControlRecordComponent } from './control-record';
import { AgencyService } from '../../core/services/agency.service';
import { ContentService } from '../../core/services/content.service';
import { ControlRecordService } from '../../core/services/control-record.service';

describe('ControlRecordComponent', () => {
  function setup(getByAgency: (id: number) => unknown) {
    localStorage.setItem('cafeoccidente.agencyId', '3');
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AgencyService, useValue: { list: () => of([{ id: 3, name: 'El Tambo' }]) } },
        { provide: ControlRecordService, useValue: { getByAgency } },
        {
          provide: ContentService,
          useValue: { loadJson: () => of({ leftColumn: [
              { key: 'prefix', label: 'Prefijo', type: 'text' },
              { key: 'withholdingPercentage', label: 'Porcentaje Rete Fuente', type: 'percentage' },
            ], rightColumn: [] }) },
        },
      ],
    });
    return TestBed.createComponent(ControlRecordComponent).componentInstance;
  }

  it('opens with the session agency preselected and its record loaded', () => {
    const component = setup(() => of({ id: 1, agencyId: 3, agencyName: 'El Tambo', active: true, baseLoad: 125 }));
    expect(component.agencyId()).toBe(3);
    expect(component.isCreate()).toBe(false);
  });

  it('a late response from the previously selected agency never overwrites the current one', () => {
    const pending: Record<number, Subject<unknown>> = { 3: new Subject(), 1: new Subject(), 7: new Subject() };
    const component = setup((id: number) => pending[id]);
    const prefix = () => [...component.leftFields(), ...component.rightFields()].find((f) => f.key === 'prefix')?.value;

    component.onAgencyChange('1'); // Buesaco
    component.onAgencyChange('7'); // agencia sin registro
    pending[7].error(new HttpErrorResponse({ status: 404 }));
    pending[1].next({ id: 1, agencyId: 1, agencyName: 'Buesaco', active: true, prefix: 'SDBU' }); // llega tarde
    pending[3].next({ id: 2, agencyId: 3, agencyName: 'El Tambo', active: true, prefix: 'SDTA' }); // mas tarde aun

    expect(component.agencyId()).toBe(7);
    expect(component.isCreate()).toBe(true);
    expect(prefix()).toBe('');
  });

  it('loads decimals with a comma so editing 0,5 -> 0,6 saves 0.6, not 6', () => {
    const component = setup(() => of({ id: 2, agencyId: 3, agencyName: 'El Tambo', active: true, withholdingPercentage: 0.5, prefix: 'SDTA' }));
    const request = () => (component as unknown as { buildRequest(): { withholdingPercentage: number } }).buildRequest();
    expect(request().withholdingPercentage).toBe(0.5); // guardar sin tocar no cambia nada

    const loaded = component.leftFields().find((f) => f.key === 'withholdingPercentage')!.value as string;
    expect(loaded).toBe('0,5');
    // Lo que hace form-field al editar un campo numerico: deja solo digitos y coma. Con "0.5" quedaba "06".
    const edited = (loaded.slice(0, -1) + '6').replace(/[^0-9,]/g, '');
    component.onFieldValueChange({ key: 'withholdingPercentage', value: edited });
    expect(request().withholdingPercentage).toBe(0.6);
  });

  it('shows the empty create form when the session agency has no record yet', () => {
    const component = setup(() => throwError(() => new HttpErrorResponse({ status: 404 })));
    expect(component.agencyId()).toBe(3);
    expect(component.isCreate()).toBe(true);
  });
});
