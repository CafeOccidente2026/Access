import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import content from '../../../../public/assets/data/beneficiary-report.json';
import { BeneficiaryReportComponent } from './beneficiary-report';
import { AuthService } from '../../core/services/auth.service';
import { ContentService } from '../../core/services/content.service';
import { NavigationService } from '../../core/services/navigation.service';
import { VendorService } from '../../core/services/vendor.service';

describe('BeneficiaryReportComponent', () => {
  function setup(report = 'beneficiary') {
    const beneficiary = vi.fn(() => of([]));
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ContentService, useValue: { loadJson: () => of(content) } },
        { provide: VendorService, useValue: { beneficiary } },
        { provide: NavigationService, useValue: { goTo: vi.fn() } },
        { provide: AuthService, useValue: { isAdmin: () => false, agencyName: () => 'El Tambo' } },
        { provide: ActivatedRoute, useValue: { snapshot: { data: { report } } } },
      ],
    });
    const fixture = TestBed.createComponent(BeneficiaryReportComponent);
    fixture.detectChanges();
    return { c: fixture.componentInstance, fixture, beneficiary };
  }

  it('RN-28.2.1: asks "Ingrese el número de cédula del Beneficiario a consultar"', () => {
    const { fixture } = setup();
    expect(fixture.nativeElement.textContent).toContain('Ingrese el número de cédula del Beneficiario a consultar');
  });

  it('prueba del 11-nov (punto 4): if the server rejects it, shows its reason', () => {
    const { c, beneficiary } = setup();
    beneficiary.mockReturnValue(throwError(() => ({ error: { message: 'Falta o está mal: Desde Fecha' } })));
    c.idNumber.set('79374104');
    c.accept(new Event('submit'));
    expect(c.message()).toBe('Falta o está mal: Desde Fecha');
  });

  it('CA-1: the typed cedula generates the report of that beneficiary', () => {
    const { c, beneficiary } = setup();
    c.idNumber.set('79.374.104');
    c.accept(new Event('submit'));
    expect(beneficiary).toHaveBeenCalledWith(expect.objectContaining({ idNumber: '79374104', allAgencies: null }));
  });

  it('without cedula and without "Todos los beneficiarios" it asks for the cedula', () => {
    const { c, beneficiary } = setup();
    c.accept(new Event('submit'));
    expect(c.message()).toBe(content.messages.idNumber);
    expect(beneficiary).not.toHaveBeenCalled();
  });

  it('CA-2: "Todos los beneficiarios" sends no cedula', () => {
    const { c, beneficiary, fixture } = setup();
    expect(fixture.nativeElement.textContent).toContain('Todos los beneficiarios');
    c.idNumber.set('79374104');
    c.allBeneficiaries.set(true);
    c.accept(new Event('submit'));
    expect(beneficiary).toHaveBeenCalledWith(expect.objectContaining({ idNumber: '' }));
  });

  it('the report by dates keeps the Access behavior: empty cedula = all', () => {
    const { c, beneficiary, fixture } = setup('summary');
    expect(fixture.nativeElement.textContent).not.toContain('Todos los beneficiarios');
    c.from.set('2026-05-01');
    c.to.set('2026-05-31');
    c.accept(new Event('submit'));
    expect(beneficiary).toHaveBeenCalledWith(expect.objectContaining({ idNumber: '', from: '2026-05-01' }));
  });
});
