import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { describe, expect, it } from 'vitest';

import content from '../../../../public/assets/data/control-record.json';
import { FormFieldDefinition } from '../../core/models';
import { FormFieldComponent } from '../../shared/ui';
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

  it('D1: with the backend response, Kg de Excelso shows 70 and Factor Base shows 94 (real labels)', () => {
    localStorage.setItem('cafeoccidente.agencyId', '1');
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AgencyService, useValue: { list: () => of([{ id: 1, name: 'Buesaco' }]) } },
        { provide: ControlRecordService, useValue: { getByAgency: () => of({ id: 1, agencyId: 1, agencyName: 'Buesaco', active: true, excelsoKg: 70, baseFactor: 94 }) } },
        { provide: ContentService, useValue: { loadJson: () => of(content) } },
      ],
    });
    const component = TestBed.createComponent(ControlRecordComponent).componentInstance;
    const byLabel = (label: string) => [...component.leftFields(), ...component.rightFields()].find((f) => f.label === label)?.value;
    expect(byLabel('Kg de Excelso')).toBe('70');
    expect(byLabel('Factor Base')).toBe('94');
  });

  it('CA-1: Punto de Compra is filled by the server and never editable (RN-06.3)', () => {
    localStorage.setItem('cafeoccidente.agencyId', '1');
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AgencyService, useValue: { list: () => of([{ id: 1, name: 'Buesaco' }]) } },
        { provide: ControlRecordService, useValue: { getByAgency: () => of({ id: 1, agencyId: 1, agencyName: 'Buesaco', active: true, purchasePoint: 'BUESACO1 OCCIDENTE' }) } },
        { provide: ContentService, useValue: { loadJson: () => of(content) } },
      ],
    });
    const component = TestBed.createComponent(ControlRecordComponent).componentInstance;
    const point = component.leftFields().find((f) => f.key === 'purchasePoint')!;
    expect(point.value).toBe('BUESACO1 OCCIDENTE');
    expect(point.readonly).toBe(true);
    expect(component.leftFields().find((f) => f.key === 'prefix')!.readonly).toBe(false);
    expect('purchasePoint' in (component as unknown as { buildRequest(): object }).buildRequest()).toBe(false);
  });

  it('CA-3: the RN-06.2 and green coffee fields are numeric, so typing letters is not possible', () => {
    const numeric = ['Núm Registro Control', 'Factor Base', 'Base Rete Fuente', 'Base Carga', 'Porcentaje Rete Fuente',
      'Base Pasilla', '% Kg de Pasilla Promedio', 'Peso Tot Alm Café Verde', 'Peso Tot Pasilla Café Verde',
      'Peso Alm Sana Café Verde', 'Pr Alm Sana Café Verde', 'Pr_AlmDefec Café Verde'];
    const fields = [...content.leftColumn, ...content.rightColumn] as FormFieldDefinition[];
    for (const label of numeric) {
      expect(['number', 'count', 'currency', 'percentage'], label).toContain(fields.find((f) => f.label === label)?.type);
    }
    const input = TestBed.createComponent(FormFieldComponent).componentInstance;
    input.field = fields.find((f) => f.label === 'Base Pasilla')!;
    input.onInput('1a2,5b');
    expect(input.inputValue).toBe('12,5');
  });

  it('shows the empty create form when the session agency has no record yet', () => {
    const component = setup(() => throwError(() => new HttpErrorResponse({ status: 404 })));
    expect(component.agencyId()).toBe(3);
    expect(component.isCreate()).toBe(true);
  });
});
