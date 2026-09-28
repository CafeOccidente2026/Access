import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it } from 'vitest';

import { ControlRecordComponent } from './control-record';
import { AgencyService } from '../../core/services/agency.service';
import { ControlRecordService } from '../../core/services/control-record.service';

describe('ControlRecordComponent', () => {
  function setup(getByAgency: () => unknown) {
    localStorage.setItem('cafeoccidente.agencyId', '3');
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AgencyService, useValue: { list: () => of([{ id: 3, name: 'El Tambo' }]) } },
        { provide: ControlRecordService, useValue: { getByAgency } },
      ],
    });
    return TestBed.createComponent(ControlRecordComponent).componentInstance;
  }

  it('opens with the session agency preselected and its record loaded', () => {
    const component = setup(() => of({ id: 1, agencyId: 3, agencyName: 'El Tambo', active: true, baseLoad: 125 }));
    expect(component.agencyId()).toBe(3);
    expect(component.isCreate()).toBe(false);
  });

  it('shows the empty create form when the session agency has no record yet', () => {
    const component = setup(() => throwError(() => new HttpErrorResponse({ status: 404 })));
    expect(component.agencyId()).toBe(3);
    expect(component.isCreate()).toBe(true);
  });
});
