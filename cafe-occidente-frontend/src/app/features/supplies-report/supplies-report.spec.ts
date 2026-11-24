import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { SuppliesReportRow } from '../../core/models/supplies.model';
import { ContentService } from '../../core/services/content.service';
import { SuppliesService } from '../../core/services/supplies.service';
import { TableExporter } from '../../shared/utils/export-table';
import content from '../../../../public/assets/data/supplies-report.json';
import { SuppliesReportComponent } from './supplies-report';

const CHECK: SuppliesReportRow = {
  agencyName: 'EL TAMBO', transactionId: 'SDTA-3SUM-001', fundCode: 'RP', date: '2026-11-23', idNumber: '99304051',
  firstNames: 'CAFICULTOR', lastNames: 'PRUEBA', detail: 'SUMINISTRO', inflow: 25000000, outflow: 0, balance: 0,
  paymentMethod: 'CHEQUE', checkNumber: '5320',
};

/** Demo del 23-nov: Relacion Cheques y Relacion Cheques Especial se exportan a Excel y a Word, ademas del PDF. */
describe('SuppliesReportComponent: exportar la Relacion Cheques', () => {
  function open(report: string, rows: SuppliesReportRow[]) {
    const exporter = { xlsx: vi.fn(() => Promise.resolve()), docx: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ContentService, useValue: { loadJson: () => of(content) } },
        { provide: SuppliesService, useValue: { report: () => of(rows) } },
        { provide: TableExporter, useValue: exporter },
        { provide: ActivatedRoute, useValue: { snapshot: { data: { report } } } },
      ],
    });
    const fixture = TestBed.createComponent(SuppliesReportComponent);
    fixture.detectChanges();
    return { fixture, component: fixture.componentInstance, exporter, el: fixture.nativeElement as HTMLElement };
  }

  for (const [report, fileName] of [['checks', 'relacion cheques'], ['checksSpecial', 'relacion cheques especial']]) {
    it(`${report}: after Aceptar, Exportar a Excel and Exportar a Word download the same table as the PDF`, () => {
      const { fixture, component, exporter, el } = open(report, [CHECK]);
      expect(el.querySelector('[data-testid="export-word"]')).toBeNull();
      component.setFilter('from', '2026-11-23');
      component.setFilter('to', '2026-11-23');
      component.accept();
      fixture.detectChanges();

      (el.querySelector('[data-testid="export-excel"]') as HTMLButtonElement).click();
      (el.querySelector('[data-testid="export-word"]') as HTMLButtonElement).click();

      const table = expect.objectContaining({ title: 'RELACION CHEQUES', columns: content.reports[report as 'checks'].columns });
      expect(exporter.xlsx).toHaveBeenCalledWith(table, fileName);
      expect(exporter.docx).toHaveBeenCalledWith(table, fileName);
    });
  }

  it('an empty Relacion Cheques (Suma total 0) can be exported too', () => {
    const { fixture, component, exporter, el } = open('checks', []);
    component.accept();
    fixture.detectChanges();
    (el.querySelector('[data-testid="export-excel"]') as HTMLButtonElement).click();
    expect(exporter.xlsx).toHaveBeenCalledWith(expect.objectContaining({ title: 'RELACION CHEQUES' }), 'relacion cheques');
  });

  it('the other supplies reports do not show the export buttons', () => {
    const { fixture, component, el } = open('cash', [CHECK]);
    component.setFilter('fund', 'RP');
    component.accept();
    fixture.detectChanges();
    expect(el.querySelector('[data-testid="export-excel"]')).toBeNull();
    expect(el.querySelector('[data-testid="export-word"]')).toBeNull();
  });
});
