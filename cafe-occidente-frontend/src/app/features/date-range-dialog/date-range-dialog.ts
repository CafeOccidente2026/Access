import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';

import { ContentService } from '../../core/services/content.service';
import { NavigationService } from '../../core/services/navigation.service';
import { PurchaseReportService } from '../../core/services/purchase-report.service';
import { AppButtonComponent } from '../../shared/ui';
import { renderPdfPreview } from '../../shared/utils/pdf-preview';
import { DateRangeDialogContent } from './date-range-dialog.model';
import { buildPurchasesByDateDoc } from './purchases-by-date-pdf';

/**
 * "Consulta compras por fecha" (RF-25): el "Diálogo Compras" de Access con Desde y Hasta en calendario
 * (RN-25.1); Aceptar genera el informe "Compras por fecha" de la agencia de trabajo (RN-25.2) y lo
 * muestra embebido, sin ventana emergente. Solo lectura (RN-25.4).
 */
@Component({
  selector: 'app-date-range-dialog',
  standalone: true,
  imports: [CommonModule, AppButtonComponent],
  templateUrl: './date-range-dialog.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DateRangeDialogComponent {
  private readonly navigation = inject(NavigationService);
  private readonly service = inject(PurchaseReportService);
  private readonly sanitizer = inject(DomSanitizer);

  readonly data = toSignal(inject(ContentService).loadJson<DateRangeDialogContent>('date-range-dialog'));
  readonly fromDate = signal('');
  readonly toDate = signal('');
  /** Desde posterior a Hasta: se avisa y Aceptar queda deshabilitado. */
  readonly fromAfterTo = computed(() => !!this.fromDate() && !!this.toDate() && this.fromDate() > this.toDate());
  readonly canAccept = computed(() => !!this.fromDate() && !!this.toDate() && !this.fromAfterTo());

  /** Rango pedido; undefined = todavia no se ha aceptado. */
  private readonly requested = signal<{ from: string; to: string } | undefined>(undefined);
  readonly report = rxResource({
    params: () => this.requested(),
    stream: ({ params }) => this.service.byDate(params.from, params.to),
  });
  private readonly rows = computed(() => (this.report.hasValue() ? this.report.value() : undefined));

  readonly doc = computed(() => {
    const rows = this.rows();
    const range = this.requested();
    const data = this.data();
    return rows?.length && range && data ? buildPurchasesByDateDoc(rows, range.from, range.to, data.pdf) : null;
  });

  readonly message = computed(() => {
    const m = this.data()?.messages;
    if (!m) {
      return null;
    }
    if (this.fromAfterTo()) {
      return m.fromAfterTo;
    }
    if (this.report.error()) {
      return m.error;
    }
    return this.rows()?.length === 0 ? m.empty : null;
  });

  readonly pdfUrl = signal<SafeResourceUrl | null>(null);

  constructor() {
    effect(() => {
      const doc = this.doc();
      this.pdfUrl.set(null);
      if (doc) {
        renderPdfPreview(doc, this.sanitizer, (url) => this.pdfUrl.set(url));
      }
    });
  }

  onAccept(): void {
    if (!this.canAccept()) {
      return;
    }
    const range = { from: this.fromDate(), to: this.toDate() };
    const current = this.requested();
    if (current?.from === range.from && current.to === range.to) {
      this.report.reload();
    } else {
      this.requested.set(range);
    }
  }

  onCancel(): void {
    this.navigation.goTo('/compras');
  }
}
