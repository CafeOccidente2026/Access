import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';

import { SuppliesReportRow } from '../../core/models/supplies.model';
import { ContentService } from '../../core/services/content.service';
import { NavigationService } from '../../core/services/navigation.service';
import { SuppliesService } from '../../core/services/supplies.service';
import {
  AccessWindowComponent, AgencyPickerComponent, ComboboxComponent, FormFlowDirective, PdfGeneratingComponent,
} from '../../shared/ui';
import { checksTable, paymentMethodsSheet } from './supplies-report-pdf';
import { TableExporter } from '../../shared/utils/export-table';
import { renderReportPdf } from '../report-pdf/report-pdf';
import { SuppliesReportContent } from './supplies-report.model';
import { serverError } from '../../shared/utils/server-error';

/**
 * Informes de "MENUS SUMINISTROS": dialogo (Fondo, Tipo, fechas o Forma Pago segun el JSON) y vista
 * previa en PDF. ADMIN elige agencia (vacia = todas); USER ve la suya, fija.
 */
@Component({
  selector: 'app-supplies-report',
  standalone: true,
  imports: [CommonModule, AccessWindowComponent, AgencyPickerComponent, ComboboxComponent, FormFlowDirective, PdfGeneratingComponent],
  templateUrl: './supplies-report.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SuppliesReportComponent {
  private readonly suppliesService = inject(SuppliesService);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly navigation = inject(NavigationService);
  private readonly exporter = inject(TableExporter);
  private readonly reportKey: string = inject(ActivatedRoute).snapshot.data['report'];

  readonly content = toSignal(inject(ContentService).loadJson<SuppliesReportContent>('supplies-report'));
  readonly report = computed(() => this.content()?.reports[this.reportKey]);

  readonly filters = signal<Record<string, string>>({});
  readonly message = signal<string | null>(null);
  readonly pdfUrl = signal<SafeResourceUrl | null>(null);
  /** R3: el Web Worker esta armando el PDF (aviso "Generando PDF..."). */
  readonly generatingPdf = signal(false);
  readonly rows = signal<SuppliesReportRow[]>([]);
  /** Ya llego el informe (la Relacion Cheques puede venir vacia y tambien se exporta). */
  readonly reported = signal(false);

  setFilter(key: string, value: string): void {
    this.filters.update((f) => ({ ...f, [key]: value }));
  }

  cancel(): void {
    this.navigation.goTo(this.report()?.backRoute);
  }

  accept(): void {
    const report = this.report()!;
    const content = this.content()!;
    const filters = this.filters();
    this.message.set(null);
    this.pdfUrl.set(null);
    this.rows.set([]);
    this.reported.set(false);
    const missing = report.filters.find((f) => f.required && !(f.options ?? [filters[f.key]]).includes(filters[f.key]));
    if (missing) {
      this.message.set(content.messages.required.replace('{label}', missing.label));
      return;
    }
    const params = { ...report.fixedParams, ...filters };
    this.suppliesService.report(report.endpoint, params, report.post).subscribe({
      next: (rows) => {
        if (rows.length === 0 && report.layout !== 'checks') {
          this.message.set(content.messages.empty);
          return;
        }
        this.rows.set(rows);
        this.reported.set(true);
        renderReportPdf({ kind: 'supplies', args: [report, content.text, rows, filters['type'] ?? null] }, this.sanitizer,
          (url) => this.pdfUrl.set(url), this.generatingPdf);
      },
      error: (err) => this.message.set(serverError(err, content.messages.error)),
    });
  }

  /** Demo del 23-nov: la Relacion Cheques en Excel o en Word, con las columnas y totales del PDF. */
  exportChecks(format: 'xlsx' | 'docx'): void {
    const report = this.report()!;
    const table = checksTable(report, this.content()!.text, this.rows());
    if (format === 'xlsx') {
      void this.exporter.xlsx(table, report.exportFileName!);
    } else {
      this.exporter.docx(table, report.exportFileName!);
    }
  }

  /** "formas de pago.xlsx": la consulta FormaPago; la libreria se carga solo al descargar. */
  async downloadExcel(): Promise<void> {
    const excel = this.report()!.excel!;
    const { default: writeExcelFile } = await import('write-excel-file/browser');
    await writeExcelFile(paymentMethodsSheet(excel.columns, this.rows())).toFile(`${excel.fileName}.xlsx`);
  }
}
