import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';

import { API_BASE_URL } from '../../core/config/api.config';
import { AssociatePage } from '../../core/models/vendor.model';
import { ContentService } from '../../core/services/content.service';
import content from '../../../../public/assets/data/associates.json';
import { AssociatesComponent } from './associates';

const URL = `${API_BASE_URL}/vendors/associates`;

function page(position: number, idNumber: string, total = 3): AssociatePage {
  return {
    position,
    total,
    associate: {
      idNumber, firstName: 'ASOCIADA', secondName: 'MARIA', lastName: 'PRUEBA', secondLastName: null,
      agencyName: 'EL TAMBO', birthDate: '1970-03-01', birthPlace: null, maritalStatus: null, coffeeIdCard: null,
      address: 'VDA LA PALMA', phone: '3001234567', affiliationDate: null, actNumber: null, accepted: true,
      eligible: false, withdrawn: false, deceased: false, observation: 'SIN NOVEDAD', growerType: 'C',
      transportCompany: null, vehiclePlate: null, exported: false, isAssociation: false, isNew: true,
      cityCode: null, sex: 'F', country: null,
    },
  };
}

const value = (el: HTMLElement, label: string) =>
  (el.querySelector(`[aria-label="${label}"]`) as HTMLInputElement).value;

describe('AssociatesComponent (HU-53)', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ContentService, useValue: { loadJson: () => of(content) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  function render(first = page(0, '99124124')) {
    const fixture = TestBed.createComponent(AssociatesComponent);
    http.expectOne(`${URL}?position=0`).flush(first);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const click = (label: string) => {
      [...el.querySelectorAll('button')].find((b) => b.textContent?.trim() === label)!.click();
      fixture.detectChanges();
    };
    const answer = (url: string, body: AssociatePage) => {
      http.expectOne(url).flush(body);
      fixture.detectChanges();
    };
    return { fixture, el, click, answer };
  }

  it('RN-28.3.4/5: shows the 28 Access fields and none is editable', () => {
    const { el } = render();
    const controls = [...el.querySelectorAll<HTMLInputElement | HTMLTextAreaElement>('[aria-label]')]
      .filter((c) => c.id !== 'associates-search');

    expect(controls).toHaveLength(28);
    for (const c of controls) {
      expect(c instanceof HTMLInputElement && c.type === 'checkbox' ? c.disabled : c.readOnly).toBe(true);
    }
    expect(value(el, 'IdAsociado')).toBe('99124124');
    expect(value(el, 'Agencia')).toBe('EL TAMBO');
    expect(value(el, 'FechaNacimiento')).toBe('01/03/1970');
    expect(value(el, 'Aceptado')).toBe('Verdadero');
    expect(value(el, 'Habil')).toBe('Falso');
    expect(value(el, 'Observacion')).toBe('SIN NOVEDAD');
    expect((el.querySelector('[aria-label="Nuevo"]') as HTMLInputElement).checked).toBe(true);
    expect(el.textContent).toContain('1 de 3');
  });

  it('CA-1: the arrows ask the next, previous, last and first position', () => {
    const { el, click, answer } = render();

    click('▶');
    answer(`${URL}?position=1`, page(1, '2'));
    click('▶|');
    answer(`${URL}?position=2`, page(2, '3'));
    expect(el.textContent).toContain('3 de 3');
    click('◀');
    answer(`${URL}?position=1`, page(1, '2'));
    click('|◀');
    answer(`${URL}?position=0`, page(0, '1'));
    expect(value(el, 'IdAsociado')).toBe('1');
  });

  it('CA-1: at the first or last record the arrows ask nothing', () => {
    const { click, answer } = render();
    click('◀');
    click('|◀');
    http.expectNone(() => true);

    click('▶|');
    answer(`${URL}?position=2`, page(2, '3'));
    click('▶');
    click('▶|');
    http.expectNone(() => true);
  });

  it('CA-2: the search shows that third party directly, without dots', () => {
    const { el, fixture } = render();
    (el.querySelector('#associates-search') as HTMLInputElement).value = '99.124.124';
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    http.expectOne(`${URL}/99124124`).flush(page(1, '99124124'));
    fixture.detectChanges();

    expect(value(el, 'IdAsociado')).toBe('99124124');
    expect(el.textContent).toContain('2 de 3');
  });

  it('CA-2: an empty search asks nothing and an unknown one says so', () => {
    const { el, fixture } = render();
    const form = el.querySelector('form')!;
    const input = el.querySelector('#associates-search') as HTMLInputElement;
    input.value = '  ';
    form.dispatchEvent(new Event('submit'));
    http.expectNone(() => true);

    input.value = '1';
    form.dispatchEvent(new Event('submit'));
    http.expectOne(`${URL}/1`).flush(null, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(el.textContent).toContain(content.messages.notFound);
    expect(value(el, 'IdAsociado')).toBe('99124124');
  });
});
