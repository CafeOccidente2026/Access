import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, ElementRef, computed, effect, inject, signal, viewChild } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';

import { ContentService } from '../../core/services/content.service';
import { PurchaseReportService } from '../../core/services/purchase-report.service';
import { AccessWindowComponent, FormFlowDirective, PdfGeneratingComponent } from '../../shared/ui';
import { renderReportPdf } from '../report-pdf/report-pdf';
import { AnnouncementReportText, shortDate } from './announcement-report-pdf';

export interface AnnouncementReportContent {
  readonly windowTitle: string;
  readonly dateLabel: string;
  readonly generateLabel: string;
  readonly printLabel: string;
  readonly pdfTitle: string;
  readonly messages: Record<'wrongYear' | 'empty' | 'error', string>;
  readonly pdf: AnnouncementReportText;
}

/**
 * "Generar Reporte Anuncio" (RF-24; Form_MENUS COMPRAS.bas, Comando7 -> macro "Imprimir Anuncio"):
 * todas las compras del dia elegido (hoy por defecto) en la agencia de trabajo. RESUMEN y ANUNCIO van en
 * un solo PDF embebido (sin ventana emergente que el navegador pueda bloquear) e Imprimir los imprime
 * juntos (RN-24.4).
 */
@Component({
  selector: 'app-announcement-report',
  standalone: true,
  imports: [CommonModule, AccessWindowComponent, FormFlowDirective, PdfGeneratingComponent],
  templateUrl: './announcement-report.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AnnouncementReportComponent {
  private readonly service = inject(PurchaseReportService);
  private readonly sanitizer = inject(DomSanitizer);

  readonly content = toSignal(inject(ContentService).loadJson<AnnouncementReportContent>('announcement-report'));
  readonly year = new Date().getFullYear();
  readonly minDate = `${this.year}-01-01`;
  readonly maxDate = `${this.year}-12-31`;
  readonly date = signal(new Date().toLocaleDateString('en-CA'));
  /** Fecha del reporte pedido; undefined = todavia no se ha generado. */
  private readonly requested = signal<string | undefined>(undefined);
  private readonly wrongYear = signal(false);

  readonly report = rxResource({
    params: () => this.requested(),
    stream: ({ params }) => this.service.byDate(params, params),
  });

  /** Las compras del reporte; undefined mientras carga o si fallo. */
  private readonly rows = computed(() => (this.report.hasValue() ? this.report.value() : undefined));

  readonly doc = computed(() => {
    const rows = this.rows();
    const content = this.content();
    const date = this.requested();
    return rows?.length && content && date ? { kind: 'announcement' as const, args: [rows, shortDate(date), content.pdf] as [typeof rows, string, typeof content.pdf] }
      : null;
  });

  readonly message = computed(() => {
    const m = this.content()?.messages;
    if (!m) {
      return null;
    }
    if (this.wrongYear()) {
      return m.wrongYear.replace('{year}', String(this.year));
    }
    if (this.report.error()) {
      return m.error;
    }
    return this.rows()?.length === 0 ? m.empty : null;
  });

  readonly pdfUrl = signal<SafeResourceUrl | null>(null);
  /** R3: el Web Worker esta armando el PDF (aviso "Generando PDF..."). */
  readonly generatingPdf = signal(false);
  private readonly frame = viewChild<ElementRef<HTMLIFrameElement>>('frame');

  constructor() {
    effect(() => {
      const doc = this.doc();
      this.pdfUrl.set(null);
      if (doc) {
        renderReportPdf(doc, this.sanitizer, (url) => this.pdfUrl.set(url), this.generatingPdf);
      }
    });
  }

  generate(): void {
    const date = this.date();
    this.wrongYear.set(!date.startsWith(`${this.year}-`));
    if (this.wrongYear()) {
      this.requested.set(undefined);
    } else if (this.requested() === date) {
      this.report.reload();
    } else {
      this.requested.set(date);
    }
  }

  /** Imprime el PDF embebido: el resumen y el detalle juntos. */
  print(): void {
    this.frame()?.nativeElement.contentWindow?.print();
  }
}
