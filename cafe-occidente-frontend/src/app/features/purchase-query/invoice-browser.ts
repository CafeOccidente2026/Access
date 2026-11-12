import { computed, linkedSignal, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Observable } from 'rxjs';

import { AnnulmentCandidate, AnnulmentDirection, AnnulmentModule } from '../../core/models/purchase-annulment.model';
import { PurchaseQueryDetail } from '../../core/models/purchase-query.model';
import { InvoiceListService } from '../../core/services/invoice-list.service';
import { serverError } from '../../shared/utils/server-error';

/** Busqueda por factura o paso |◄ ◄ ► ►| desde la factura que se muestra. */
type ListRequest =
  | { readonly invoice: number }
  | { readonly from: number | null; readonly direction: AnnulmentDirection };

/**
 * Consultar Compras y Corregir Compra (HU-05): buscar o recorrer las facturas de la agencia, elegir entre las de
 * varios modulos y traer la compra completa. Se crea en un campo del componente (contexto de inyeccion).
 */
export class InvoiceBrowser {
  private readonly request = signal<ListRequest | undefined>(undefined);
  private readonly list = rxResource({
    params: () => this.request(),
    stream: ({ params }) =>
      'invoice' in params ? this.source.findByInvoice(params.invoice) : this.source.navigate(params.from, params.direction),
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

  readonly detail = rxResource({
    params: () => {
      const s = this.selected();
      return s ? { module: s.module, id: s.id } : undefined;
    },
    stream: ({ params }) => this.loadDetail(params.module, params.id),
  });
  readonly detailValue = computed(() => (this.detail.hasValue() ? this.detail.value() : undefined));

  constructor(
    private readonly source: InvoiceListService,
    private readonly loadDetail: (module: AnnulmentModule, id: number) => Observable<PurchaseQueryDetail>,
  ) {}

  /** El aviso de la busqueda: el motivo del servidor si fallo, o que no hay esa factura o no hay mas. */
  listMessage(messages: { readonly notFound: string; readonly noMore: string; readonly error: string }): string | null {
    const failure = this.list.error() ?? this.detail.error();
    if (failure) {
      return serverError(failure, messages.error);
    }
    if (this.listValue()?.length === 0) {
      const request = this.request();
      return request && 'invoice' in request ? messages.notFound : messages.noMore;
    }
    return null;
  }

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
}
