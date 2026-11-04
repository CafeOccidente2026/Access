import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';

import { AnnulmentCandidate, AnnulmentDirection } from '../../core/models/purchase-annulment.model';
import { ContentService } from '../../core/services/content.service';
import { NavigationService } from '../../core/services/navigation.service';
import { PurchaseAnnulmentService } from '../../core/services/purchase-annulment.service';
import { AccessWindowComponent, FormFlowDirective, InvoiceNavigatorComponent, NavigatorButton } from '../../shared/ui';
import { formatDisplayNumber } from '../../shared/utils/number-format';
import { saveOnce } from '../../shared/utils/save-once';
import { shortDate } from '../conductor-form/conductor-form';

export interface PurchaseAnnulmentContent {
  readonly windowTitle: string;
  readonly invoiceLabel: string;
  readonly searchLabel: string;
  readonly backLabel: string;
  readonly backRoute: string;
  readonly annulLabel: string;
  readonly yesLabel: string;
  readonly noLabel: string;
  readonly acceptLabel: string;
  readonly confirmMessage: string;
  readonly lockedMessage: string;
  readonly modules: Record<string, string>;
  readonly fields: { key: string; label: string; type?: 'date' | 'number' | 'money' }[];
  /** |◄ ◄ ► ►|: texto del boton y su ayuda, por direccion. */
  readonly navigation: NavigatorButton[];
  readonly messages: Record<'notFound' | 'noMore' | 'annulled' | 'error', string>;
}

/** Valor de una fila de la ficha (Cns_ComprasParaAnular, todos los controles bloqueados). */
export function fieldValue(c: AnnulmentCandidate, key: string, type: string | undefined, content: PurchaseAnnulmentContent): string {
  switch (key) {
    case 'module':
      return content.modules[c.module] ?? c.module;
    case 'invoice':
      return `${c.prefix ?? ''} ${c.invoiceNumber}`.trim();
    case 'name':
      return [c.firstName, c.lastName].filter(Boolean).join(' ');
  }
  const value = (c as unknown as Record<string, unknown>)[key];
  if (value == null) {
    return '';
  }
  if (type === 'date') {
    return shortDate(String(value));
  }
  if (type === 'number' || type === 'money') {
    return formatDisplayNumber(Number(value), type === 'money' ? 'count' : 'currency');
  }
  return String(value);
}

/**
 * "Anular Documento" (macro ParaAnularFactura -> Cns_ComprasParaAnular -> AnularFactura): recorrer las
 * compras una por una o buscar la factura (RN-23.2), ver la ficha y anular con la confirmacion del RF
 * (RN-23.3). Una compra cerrada en Access no se anula.
 */
@Component({
  selector: 'app-purchase-annulment',
  standalone: true,
  imports: [CommonModule, AccessWindowComponent, FormFlowDirective, InvoiceNavigatorComponent],
  templateUrl: './purchase-annulment.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PurchaseAnnulmentComponent {
  private readonly service = inject(PurchaseAnnulmentService);
  private readonly navigation = inject(NavigationService);

  readonly content = toSignal(inject(ContentService).loadJson<PurchaseAnnulmentContent>('purchase-annulment'));
  readonly candidates = signal<AnnulmentCandidate[]>([]);
  readonly selected = signal<AnnulmentCandidate | null>(null);
  /** RN-23.3: 'confirm' = pregunta del RF con Aceptar/Cancelar; 'locked' = Etiqueta98 de Access + Aceptar. */
  readonly prompt = signal<'confirm' | 'locked' | null>(null);
  readonly message = signal<string | null>(null);
  readonly error = signal(false);
  /** Anulacion en curso: Aceptar queda deshabilitado y un segundo clic no vuelve a enviar. */
  readonly saving = signal(false);
  readonly value = fieldValue;

  search(event: Event, invoice: string): void {
    event.preventDefault();
    const number = Number(invoice.replace(/\D/g, ''));
    this.reset();
    if (!number) {
      return;
    }
    this.service.findByInvoice(number).subscribe({
      next: (list) => {
        this.candidates.set(list);
        this.selected.set(list.length === 1 ? list[0] : null);
        if (!list.length) {
          this.fail(this.content()!.messages.notFound);
        }
      },
      error: () => this.fail(this.content()!.messages.error),
    });
  }

  /** RN-23.2: la compra anterior o siguiente por factura; al final se queda en la que estaba. */
  go(direction: AnnulmentDirection): void {
    this.prompt.set(null);
    this.message.set(null);
    this.service.navigate(this.selected()?.invoiceNumber ?? null, direction).subscribe({
      next: (list) => {
        if (!list.length) {
          this.fail(this.content()!.messages.noMore);
          return;
        }
        this.candidates.set(list);
        this.selected.set(list[0]);
      },
      error: () => this.fail(this.content()!.messages.error),
    });
  }

  choose(candidate: AnnulmentCandidate): void {
    this.selected.set(candidate);
    this.prompt.set(null);
    this.message.set(null);
  }

  startAnnul(): void {
    this.message.set(null);
    this.prompt.set(this.selected()!.annulmentLocked ? 'locked' : 'confirm');
  }

  confirm(): void {
    const candidate = this.selected()!;
    saveOnce(this.saving, () => this.service.annul(candidate), {
      next: (annulled) => {
        this.prompt.set(null);
        this.selected.set(annulled);
        this.candidates.update((list) => list.map((c) => (c.module === annulled.module && c.id === annulled.id ? annulled : c)));
        this.error.set(false);
        this.message.set(this.content()!.messages.annulled.replace('{invoice}', String(annulled.invoiceNumber)));
      },
      error: (err) => {
        this.prompt.set(null);
        this.fail(err?.error?.message ?? this.content()!.messages.error);
      },
    });
  }

  back(): void {
    this.navigation.goTo(this.content()?.backRoute);
  }

  private reset(): void {
    this.candidates.set([]);
    this.selected.set(null);
    this.prompt.set(null);
    this.message.set(null);
  }

  private fail(text: string): void {
    this.error.set(true);
    this.message.set(text);
  }
}
