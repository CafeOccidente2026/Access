import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, linkedSignal, signal } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';

import { PurchaseFormContent } from '../../core/models/purchase-form-content.model';
import { AnnulmentCandidate, AnnulmentDirection, AnnulmentModule } from '../../core/models/purchase-annulment.model';
import { ContentService } from '../../core/services/content.service';
import { PurchaseQueryService } from '../../core/services/purchase-query.service';
import { FormFlowDirective, InvoiceNavigatorComponent, NavigatorButton, PurchaseFormViewComponent } from '../../shared/ui';
import { openPdf } from '../../shared/utils/pdf-preview';
import { themeColor } from '../../shared/utils/theme-color';
import { loadLogoDataUrl } from '../purchase-forms/dry-coffee/dry-coffee-invoice';
import { fillQueryContent } from './purchase-query-fields';
import { buildReprintDoc } from './purchase-reprint';

export interface PurchaseQueryContent extends PurchaseFormContent {
  readonly invoiceLabel: string;
  readonly searchLabel: string;
  readonly navigation: NavigatorButton[];
  readonly modules: Record<AnnulmentModule, string>;
  /** Marca de las reimpresiones de una compra anulada. */
  readonly annulledMark: string;
  readonly messages: Record<'notFound' | 'noMore' | 'error' | 'invoiceError', string>;
}

/** Busqueda por factura o paso |◄ ◄ ► ►| desde la factura que se muestra. */
type ListRequest =
  | { readonly invoice: number }
  | { readonly from: number | null; readonly direction: AnnulmentDirection };

/**
 * "Consultar Compras" (RF-26; macro "Consultas Compras" -> formulario Cns_Compras): las compras de la
 * agencia de trabajo una por una o por factura (RN-26.1 a RN-26.3), en solo lectura (RN-26.4), con su
 * ESTADO (RN-26.5), y "Reimprimir Dcto Equiv" (RN-26.6).
 */
@Component({
  selector: 'app-purchase-query',
  standalone: true,
  imports: [CommonModule, FormFlowDirective, InvoiceNavigatorComponent, PurchaseFormViewComponent],
  templateUrl: './purchase-query.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PurchaseQueryComponent {
  private readonly service = inject(PurchaseQueryService);
  private readonly logoDataUrlPromise = loadLogoDataUrl('assets/images/cafe-occidente-logo.png');

  readonly content = toSignal(inject(ContentService).loadJson<PurchaseQueryContent>('purchase-query'));

  private readonly request = signal<ListRequest | undefined>(undefined);
  private readonly list = rxResource({
    params: () => this.request(),
    stream: ({ params }) =>
      'invoice' in params ? this.service.findByInvoice(params.invoice) : this.service.navigate(params.from, params.direction),
  });
  private readonly listValue = computed(() => (this.list.hasValue() ? this.list.value() : undefined));

  /** Las compras de la factura que se muestra; si no hay mas en esa direccion, se quedan las de antes. */
  readonly candidates = linkedSignal<AnnulmentCandidate[] | undefined, AnnulmentCandidate[]>({
    source: this.listValue,
    computation: (list, previous) => (list?.length ? list : (previous?.value ?? [])),
  });
  /** Una factura de Access puede repetirse entre modulos: se muestra la primera y se puede elegir. */
  readonly selected = linkedSignal<AnnulmentCandidate[], AnnulmentCandidate | null>({
    source: this.candidates,
    computation: (list) => list[0] ?? null,
  });

  private readonly detail = rxResource({
    params: () => {
      const s = this.selected();
      return s ? { module: s.module, id: s.id } : undefined;
    },
    stream: ({ params }) => this.service.detail(params.module, params.id),
  });
  private readonly detailValue = computed(() => (this.detail.hasValue() ? this.detail.value() : undefined));

  /** La ficha llena; null mientras no hay compra. */
  readonly view = computed(() => {
    const detail = this.detailValue();
    const content = this.content();
    return detail && content ? fillQueryContent(content, detail) : null;
  });

  /** La reimpresión falló; se borra al cambiar de compra. */
  private readonly pdfError = linkedSignal({ source: this.selected, computation: () => false });

  readonly message = computed(() => {
    const m = this.content()?.messages;
    if (!m) {
      return null;
    }
    if (this.pdfError()) {
      return m.invoiceError;
    }
    if (this.list.error() || this.detail.error()) {
      return m.error;
    }
    if (this.listValue()?.length === 0) {
      const request = this.request();
      return request && 'invoice' in request ? m.notFound : m.noMore;
    }
    return null;
  });

  search(event: Event, invoice: string): void {
    event.preventDefault();
    const number = Number(invoice.replace(/\D/g, ''));
    if (number) {
      this.request.set({ invoice: number });
    }
  }

  go(direction: AnnulmentDirection): void {
    this.request.set({ from: this.selected()?.invoiceNumber ?? null, direction });
  }

  choose(candidate: AnnulmentCandidate): void {
    this.selected.set(candidate);
  }

  /** RN-26.6: el documento equivalente de la compra mostrada, con "ANULADA" si fue anulada. */
  reprint(): void {
    const detail = this.detailValue();
    const content = this.content();
    if (!detail || !content) {
      return;
    }
    this.pdfError.set(false);
    openPdf(
      this.logoDataUrlPromise.then((logo) => buildReprintDoc(detail, logo, content.annulledMark, themeColor('--color-annulled'))),
      () => this.pdfError.set(true),
    );
  }
}
