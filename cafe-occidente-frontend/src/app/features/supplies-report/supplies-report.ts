import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';

import { SuppliesReportRow } from '../../core/models/supplies.model';
import { AuthService } from '../../core/services/auth.service';
import { ContentService } from '../../core/services/content.service';
import { NavigationService } from '../../core/services/navigation.service';
import { SuppliesService } from '../../core/services/supplies.service';
import { AccessWindowComponent, AgencyPickerComponent, ComboboxComponent } from '../../shared/ui';
import { renderPdfPreview } from '../../shared/utils/pdf-preview';
import { buildSuppliesReportDoc, paymentMethodsSheet } from './supplies-report-pdf';
import { SuppliesReportContent } from './supplies-report.model';

/**
 * Informes de "MENUS SUMINISTROS": dialogo (Fondo, Tipo, fechas o Forma Pago segun el JSON) y vista
 * previa en PDF. ADMIN elige agencia (vacia = todas); USER ve la suya, fija.
 */
@Component({
  selector: 'app-supplies-report',
  standalone: true,
  imports: [CommonModule, AccessWindowComponent, AgencyPickerComponent, ComboboxComponent],
  templateUrl: './supplies-report.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SuppliesReportComponent {
  private readonly suppliesService = inject(SuppliesService);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly navigation = inject(NavigationService);
  private readonly reportKey: string = inject(ActivatedRoute).snapshot.data['report'];

  readonly admin = inject(AuthService).isAdmin();
  readonly content = toSignal(inject(ContentService).loadJson<SuppliesReportContent>('supplies-report'));
  readonly report = computed(() => this.content()?.reports[this.reportKey]);

  readonly agencyId = signal<number | null>(inject(AuthService).agencyId());
  readonly filters = signal<Record<string, string>>({});
  readonly message = signal<string | null>(null);
  readonly pdfUrl = signal<SafeResourceUrl | null>(null);
  readonly rows = signal<SuppliesReportRow[]>([]);

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
    const missing = report.filters.find((f) => f.required && !(f.options ?? [filters[f.key]]).includes(filters[f.key]));
    if (missing) {
      this.message.set(content.messages.required.replace('{label}', missing.label));
      return;
    }
    const params = { agencyId: this.agencyId(), ...report.fixedParams, ...filters };
    this.suppliesService.report(report.endpoint, params, report.post).subscribe({
      next: (rows) => {
        if (rows.length === 0 && report.layout !== 'checks') {
          this.message.set(content.messages.empty);
          return;
        }
        this.rows.set(rows);
        const doc = buildSuppliesReportDoc(report, content.text, rows, filters['type'] ?? null);
        renderPdfPreview(doc, this.sanitizer, (url) => this.pdfUrl.set(url));
      },
      error: () => this.message.set(content.messages.error),
    });
  }

  /** "formas de pago.xlsx": la consulta FormaPago; la libreria se carga solo al descargar. */
  async downloadExcel(): Promise<void> {
    const excel = this.report()!.excel!;
    const { default: writeExcelFile } = await import('write-excel-file/browser');
    await writeExcelFile(paymentMethodsSheet(excel.columns, this.rows())).toFile(`${excel.fileName}.xlsx`);
  }
}
