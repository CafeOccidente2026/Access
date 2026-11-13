import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, linkedSignal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';

import { PurchaseFormContent } from '../../core/models/purchase-form-content.model';
import { AnnulmentCandidate, AnnulmentDirection, AnnulmentModule } from '../../core/models/purchase-annulment.model';
import { ContentService } from '../../core/services/content.service';
import { PurchaseQueryService } from '../../core/services/purchase-query.service';
import { NavigatorButton, PurchaseFormViewComponent } from '../../shared/ui';
import { printPdf } from '../../shared/utils/pdf-preview';
import { themeColor } from '../../shared/utils/theme-color';
import { loadLogoDataUrl } from '../purchase-forms/dry-coffee/dry-coffee-invoice';
import { fillQueryContent } from './purchase-query-fields';
import { buildReprintDoc } from './purchase-reprint';
import { InvoiceBrowser } from './invoice-browser';
import { InvoiceSearchComponent } from './invoice-search';

export interface PurchaseQueryContent extends PurchaseFormContent {
  readonly invoiceLabel: string;
  readonly searchLabel: string;
  readonly navigation: NavigatorButton[];
  readonly modules: Record<AnnulmentModule, string>;
  /** Marca de las reimpresiones de una compra anulada. */
  readonly annulledMark: string;
  /** HU-05: va junto al ESTADO de una compra corregida. */
  readonly correctedMark: string;
  readonly messages: Record<'notFound' | 'noMore' | 'error' | 'invoiceError', string>;
}

/**
 * "Consultar Compras" (RF-26; macro "Consultas Compras" -> formulario Cns_Compras): las compras de la
 * agencia de trabajo una por una o por factura (RN-26.1 a RN-26.3), en solo lectura (RN-26.4), con su
 * ESTADO (RN-26.5), y "Reimprimir Dcto Equiv" (RN-26.6).
 */
@Component({
  selector: 'app-purchase-query',
  standalone: true,
  imports: [CommonModule, InvoiceSearchComponent, PurchaseFormViewComponent],
  templateUrl: './purchase-query.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PurchaseQueryComponent {
  private readonly service = inject(PurchaseQueryService);
  private readonly logoDataUrlPromise = loadLogoDataUrl('assets/images/cafe-occidente-logo.png');

  readonly content = toSignal(inject(ContentService).loadJson<PurchaseQueryContent>('purchase-query'));

  /** Buscar, recorrer y elegir la compra (comun con Corregir Compra). */
  readonly browser = new InvoiceBrowser(this.service, (module, id) => this.service.detail(module, id));
  readonly candidates = this.browser.candidates;
  readonly selected = this.browser.selected;
  private readonly detailValue = this.browser.detailValue;

  /** La ficha llena; null mientras no hay compra. */
  readonly view = computed(() => {
    const detail = this.detailValue();
    const content = this.content();
    return detail && content ? fillQueryContent(content, detail, content.correctedMark) : null;
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
    return this.browser.listMessage(m);
  });

  search(event: Event, invoice: string): void {
    this.browser.search(event, invoice);
  }

  go(direction: AnnulmentDirection): void {
    this.browser.go(direction);
  }

  choose(candidate: AnnulmentCandidate): void {
    this.browser.choose(candidate);
  }

  /** RN-26.6: el documento equivalente de la compra mostrada, con "ANULADA" si fue anulada. */
  reprint(): void {
    const detail = this.detailValue();
    const content = this.content();
    if (!detail || !content) {
      return;
    }
    this.pdfError.set(false);
    printPdf(
      this.logoDataUrlPromise.then((logo) => buildReprintDoc(detail, logo, content.annulledMark, themeColor('--color-annulled'))),
      () => this.pdfError.set(true),
    );
  }
}
