import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import type { TDocumentDefinitions } from 'pdfmake/interfaces';
import { Observable, map } from 'rxjs';

import { ContentService } from '../../core/services/content.service';
import { NavigationService } from '../../core/services/navigation.service';
import { PurchaseReportService } from '../../core/services/purchase-report.service';
import { AppButtonComponent } from '../../shared/ui';
import { renderPdfPreview } from '../../shared/utils/pdf-preview';
import { AnnouncementReportText, buildAnnouncementReportDoc, shortDate } from '../announcement-report/announcement-report-pdf';
import { FutureBalanceText, buildFutureBalanceDoc } from './future-balance-pdf';

/** 'announcement' = Generar Informe Anuncios a Futuro (HU-34); 'balance' = Generar Informe Saldo Anuncios (HU-35). */
type View = 'announcement' | 'balance';

export interface FutureReportContent {
  readonly views: Record<View, { windowTitle: string; prompt: string; pdfTitle: string }>;
  readonly announcementLabel: string;
  readonly fromLabel: string;
  readonly toLabel: string;
  readonly acceptLabel: string;
  readonly cancelLabel: string;
  readonly messages: { fromAfterTo: string; empty: string; error: string };
  readonly announcementPdf: AnnouncementReportText;
  readonly balancePdf: FutureBalanceText;
}

interface Request {
  readonly announcement: number;
  readonly from: string;
  readonly to: string;
}

/**
 * Los dos informes de Compras a Futuro, como los diálogos de Access: "Dialogo Anuncios" (Anuncio, Desde y Hasta,
 * RN-21.6.1) y el InputBox "Digite el número de anuncio a imprimir" (RN-21.7.1). Aceptar muestra el PDF embebido.
 * Solo lectura (RN-21.6.7, RN-21.7.6).
 */
@Component({
  selector: 'app-future-report',
  standalone: true,
  imports: [CommonModule, AppButtonComponent],
  templateUrl: './future-report.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FutureReportComponent {
  private readonly service = inject(PurchaseReportService);
  private readonly navigation = inject(NavigationService);
  private readonly sanitizer = inject(DomSanitizer);

  readonly viewKey: View = inject(ActivatedRoute).snapshot.data['view'];
  readonly data = toSignal(inject(ContentService).loadJson<FutureReportContent>('future-report'));
  readonly view = computed(() => this.data()?.views[this.viewKey]);

  readonly announcement = signal('');
  readonly fromDate = signal('');
  readonly toDate = signal('');
  readonly fromAfterTo = computed(() => !!this.fromDate() && !!this.toDate() && this.fromDate() > this.toDate());
  readonly canAccept = computed(() => {
    const hasNumber = /^\d+$/.test(this.announcement().trim());
    return this.viewKey === 'balance'
      ? hasNumber
      : hasNumber && !!this.fromDate() && !!this.toDate() && !this.fromAfterTo();
  });

  /** Lo pedido; undefined = todavía no se ha aceptado. */
  private readonly requested = signal<Request | undefined>(undefined);
  readonly report = rxResource({
    params: () => this.requested(),
    stream: ({ params }) => this.load(params),
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
    return this.report.hasValue() && this.report.value() === null ? m.empty : null;
  });

  readonly pdfUrl = signal<SafeResourceUrl | null>(null);

  constructor() {
    effect(() => {
      const doc = this.report.hasValue() ? this.report.value() : null;
      this.pdfUrl.set(null);
      if (doc) {
        renderPdfPreview(doc, this.sanitizer, (url) => this.pdfUrl.set(url));
      }
    });
  }

  /** El PDF del informe, o null si no hay nada que mostrar. */
  private load(request: Request): Observable<TDocumentDefinitions | null> {
    const data = this.data()!;
    if (this.viewKey === 'balance') {
      return this.service.futureBalance(request.announcement).pipe(
        map((rows) => (rows.length ? buildFutureBalanceDoc(rows, data.balancePdf) : null)));
    }
    const dates = `${shortDate(request.from)} - ${shortDate(request.to)}`;
    return this.service.futureAnnouncement(request.announcement, request.from, request.to).pipe(
      map((rows) => (rows.length ? buildAnnouncementReportDoc(rows, dates, data.announcementPdf) : null)));
  }

  onAccept(): void {
    if (!this.canAccept()) {
      return;
    }
    const request = { announcement: Number(this.announcement().trim()), from: this.fromDate(), to: this.toDate() };
    const current = this.requested();
    if (current && JSON.stringify(current) === JSON.stringify(request)) {
      this.report.reload();
    } else {
      this.requested.set(request);
    }
  }

  onCancel(): void {
    this.navigation.goTo('/compras/futuro');
  }
}
