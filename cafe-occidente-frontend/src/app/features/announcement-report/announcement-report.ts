import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, ElementRef, computed, effect, inject, signal, viewChild } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';

import { PurchaseReportRow } from '../../core/models/purchase-report.model';
import { ContentService } from '../../core/services/content.service';
import { AnnouncementReportQuery, PurchaseReportService } from '../../core/services/purchase-report.service';
import { AccessWindowComponent, FormFlowDirective, PdfGeneratingComponent } from '../../shared/ui';
import { ReportPdfJob, renderReportPdf } from '../report-pdf/report-pdf';
import { AnnouncementReportText } from './announcement-report-pdf';

/** Resumen, Detallado o Ambos (Access imprimia los dos seguidos). */
export type AnnouncementReportKind = 'both' | 'summary' | 'detail';

export interface AnnouncementReportContent {
  readonly windowTitle: string;
  readonly announcementLabel: string;
  readonly fromLabel: string;
  readonly toLabel: string;
  readonly kindLabel: string;
  readonly kinds: Record<AnnouncementReportKind, string>;
  readonly generateLabel: string;
  readonly printSummaryLabel: string;
  readonly printDetailLabel: string;
  readonly summaryPdfTitle: string;
  readonly detailPdfTitle: string;
  readonly messages: Record<'fromAfterTo' | 'empty' | 'emptyAnnouncement' | 'error', string>;
  readonly pdf: AnnouncementReportText;
}

/** Un PDF de la pantalla: su vista previa y si se esta armando. */
class ReportPdf {
  readonly url = signal<SafeResourceUrl | null>(null);
  readonly generating = signal(false);
}

/**
 * "Generar Reporte Anuncio" (RF-24; Access: Form_MENUS COMPRAS.bas, Comando7 -> macro "Imprimir Anuncio"). Prueba del
 * 19-nov: como Access, por numero de Anuncio (de cualquier fecha si no hay fechas) o, sin anuncio, todos los anuncios
 * de Desde a Hasta. Tipo Resumen, Detallado o Ambos: cada uno es un PDF aparte, un anuncio por hoja. Solo la agencia de
 * trabajo (lo filtra el servidor).
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
  readonly kindOptions: readonly AnnouncementReportKind[] = ['both', 'summary', 'detail'];
  /** El Anuncio es solo el numero, como el InputBox de Access. */
  readonly digitsOnly = /\D/g;
  readonly announcement = signal('');
  readonly fromDate = signal(new Date().toLocaleDateString('en-CA'));
  readonly toDate = signal(new Date().toLocaleDateString('en-CA'));
  readonly kind = signal<AnnouncementReportKind>('both');
  /** Lo pedido con Generar; undefined = todavia no se ha generado (o no paso las validaciones). */
  private readonly requested = signal<{ query: AnnouncementReportQuery; kind: AnnouncementReportKind } | undefined>(undefined);
  private readonly invalid = signal<'fromAfterTo' | null>(null);

  readonly report = rxResource({
    params: () => this.requested()?.query,
    stream: ({ params }) => this.service.announcement(params),
  });

  private readonly rows = computed(() => (this.report.hasValue() ? this.report.value() : undefined));

  /** Los PDF a armar segun el Tipo pedido: el resumen, el detallado o los dos, por separado. */
  readonly jobs = computed((): { summary: ReportPdfJob | null; detail: ReportPdfJob | null } => {
    const rows = this.rows();
    const content = this.content();
    const kind = this.requested()?.kind;
    if (!rows?.length || !content || !kind) {
      return { summary: null, detail: null };
    }
    const args: [PurchaseReportRow[], AnnouncementReportText] = [rows, content.pdf];
    return {
      summary: kind === 'detail' ? null : { kind: 'announcementSummary', args },
      detail: kind === 'summary' ? null : { kind: 'announcementDetail', args },
    };
  });

  readonly message = computed(() => {
    const m = this.content()?.messages;
    if (!m) {
      return null;
    }
    const invalid = this.invalid();
    if (invalid) {
      return m[invalid];
    }
    if (this.report.error()) {
      return m.error;
    }
    if (this.rows()?.length !== 0) {
      return null;
    }
    const announcement = this.requested()?.query.announcement;
    return announcement ? m.emptyAnnouncement.replace('{announcement}', announcement) : m.empty;
  });

  readonly summary = new ReportPdf();
  readonly detail = new ReportPdf();
  readonly generatingPdf = computed(() => this.summary.generating() || this.detail.generating());
  private readonly summaryFrame = viewChild<ElementRef<HTMLIFrameElement>>('summaryFrame');
  private readonly detailFrame = viewChild<ElementRef<HTMLIFrameElement>>('detailFrame');

  constructor() {
    this.render(this.summary, () => this.jobs().summary);
    this.render(this.detail, () => this.jobs().detail);
  }

  private render(pdf: ReportPdf, job: () => ReportPdfJob | null): void {
    effect(() => {
      const current = job();
      pdf.url.set(null);
      if (current) {
        renderReportPdf(current, this.sanitizer, (url) => pdf.url.set(url), pdf.generating);
      }
    });
  }

  generate(): void {
    const announcement = this.announcement().trim();
    const from = this.fromDate();
    const to = this.toDate();
    const invalid = this.validate(announcement, from, to);
    this.invalid.set(invalid);
    if (invalid) {
      this.requested.set(undefined);
      return;
    }
    const query: AnnouncementReportQuery = { announcement: announcement || undefined, from: from || undefined, to: to || undefined };
    const current = this.requested();
    this.requested.set({ query, kind: this.kind() });
    if (current && JSON.stringify(current.query) === JSON.stringify(query)) {
      this.report.reload();
    }
  }

  /** Sin anuncio hacen falta Desde y Hasta y Desde no pasa de Hasta. El rango puede cruzar de año (cierre de año). */
  private validate(announcement: string, from: string, to: string): 'fromAfterTo' | null {
    if (!announcement && (from === '' || to === '')) {
      return 'fromAfterTo';
    }
    return from && to && from > to ? 'fromAfterTo' : null;
  }

  /** Imprime uno de los PDF embebidos. */
  print(which: 'summary' | 'detail'): void {
    (which === 'summary' ? this.summaryFrame() : this.detailFrame())?.nativeElement.contentWindow?.print();
  }
}
