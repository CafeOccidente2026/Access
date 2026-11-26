import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { API_BASE_URL } from '../../core/config/api.config';
import { AnnulmentLogPage } from '../../core/models/annulment-log.model';
import { AgencyService } from '../../core/services/agency.service';
import { ContentService } from '../../core/services/content.service';
import { UserService } from '../../core/services/user.service';
import { TableExporter } from '../../shared/utils/export-table';
import content from '../../../../public/assets/data/annulment-log.json';
import access from '../../../../public/assets/data/main-access.json';
import { AnnulmentLogComponent } from './annulment-log';

const URL = `${API_BASE_URL}/annulment-log`;
const PAGE: AnnulmentLogPage = {
  total: 2,
  items: [
    {
      id: 2, origin: 'WEB', documentType: 'COMPRA_SECO', documentNumber: 'SDTA 43388', agencyName: 'El Tambo', documentId: 9,
      annulledAt: '2026-11-26T15:30:00Z', username: 'admin_prueba', reason: 'Error de digitación en los kilos',
      values: { netKg: '1200.5', netToPay: '9267436', paymentMethod: 'EFECTIVO', idNumber: '990000998' }, note: null,
    },
    {
      id: 1, origin: 'ACCESS', documentType: 'COMPRA_SECO', documentNumber: 'SDTA 43000', agencyName: 'El Tambo', documentId: 7,
      annulledAt: null, username: null, reason: null, values: { netKg: '0' }, note: 'Access deja la compra en 0 al anular',
    },
  ],
};

describe('AnnulmentLogComponent (26-nov)', () => {
  let http: HttpTestingController;
  const exporter = { xlsx: vi.fn(() => Promise.resolve()) };

  beforeEach(() => {
    exporter.xlsx.mockClear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ContentService, useValue: { loadJson: () => of(content) } },
        { provide: AgencyService, useValue: { list: () => of([{ id: 4, name: 'El Tambo' }]) } },
        { provide: UserService, useValue: { list: () => of([{ id: 7, username: 'admin_prueba' }]) } },
        { provide: TableExporter, useValue: exporter },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  function render() {
    const fixture = TestBed.createComponent(AnnulmentLogComponent);
    fixture.detectChanges();
    return { fixture, component: fixture.componentInstance, el: fixture.nativeElement as HTMLElement };
  }

  it('is only in Acceso Principal, for the Administrator', () => {
    const option = access.options.find((o) => o.route === '/consultar-anulaciones');
    expect(option?.label).toBe('Consultar anulaciones');
    expect(option?.roles).toEqual(['ADMINISTRADOR']);
  });

  it('shows each annulment with who, when, why and its values; the ones from Access say so', () => {
    const { fixture, el } = render();
    http.expectOne((r) => r.url === URL && r.params.get('page') === '0').flush(PAGE);
    fixture.detectChanges();

    const text = el.textContent ?? '';
    expect(text).toContain('SDTA 43388');
    expect(text).toContain('admin_prueba');
    expect(text).toContain('Error de digitación en los kilos');
    expect(text).toContain(`${content.values['netToPay']}: 9.267.436`);
    expect(text).toContain(content.origins['ACCESS']);
    expect(text).toContain('Access deja la compra en 0 al anular');
  });

  it('filters by agency, type, user and dates', () => {
    const { component } = render();
    http.expectOne((r) => r.url === URL).flush(PAGE);

    component.agencyId = 4;
    component.documentType = 'REMISION';
    component.userId = 7;
    component.from = '2026-11-01';
    component.to = '2026-11-30';
    component.search();

    const request = http.expectOne((r) => r.url === URL);
    const p = request.request.params;
    expect([p.get('agencyId'), p.get('documentType'), p.get('userId'), p.get('from'), p.get('to')])
      .toEqual(['4', 'REMISION', '7', '2026-11-01', '2026-11-30']);
    request.flush({ total: 0, items: [] });
  });

  it('exports to Excel every annulment of the filter, with its values', async () => {
    const { component } = render();
    http.expectOne((r) => r.url === URL).flush(PAGE);

    const done = component.export();
    http.expectOne((r) => r.url === URL && r.params.get('size') === '5000').flush(PAGE);
    await done;

    expect(exporter.xlsx).toHaveBeenCalledTimes(1);
    const [table, fileName] = exporter.xlsx.mock.calls[0] as unknown as [{ columns: string[]; rows: { cells: unknown[] }[] }, string];
    expect(table.columns).toEqual(content.columns);
    expect(table.rows).toHaveLength(2);
    expect(table.rows[0].cells).toContain('Error de digitación en los kilos');
    expect(fileName).toBe(content.fileName);
  });
});
