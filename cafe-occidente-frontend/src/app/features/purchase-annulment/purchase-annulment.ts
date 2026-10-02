import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';

import { AnnulmentCandidate } from '../../core/models/purchase-annulment.model';
import { ContentService } from '../../core/services/content.service';
import { NavigationService } from '../../core/services/navigation.service';
import { PurchaseAnnulmentService } from '../../core/services/purchase-annulment.service';
import { AccessWindowComponent, FormFlowDirective } from '../../shared/ui';
import { formatDisplayNumber } from '../../shared/utils/number-format';
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
  readonly exportedMessage: string;
  readonly futureWarning: string;
  readonly modules: Record<string, string>;
  readonly fields: { key: string; label: string; type?: 'date' | 'number' | 'money' }[];
  readonly messages: Record<'notFound' | 'annulled' | 'error', string>;
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
 * "Anular Documento" (macro ParaAnularFactura -> Cns_ComprasParaAnular -> AnularFactura): buscar la
 * factura, ver la ficha y anular con la misma confirmacion de Access. Si ya se exporto, Access no deja.
 */
@Component({
  selector: 'app-purchase-annulment',
  standalone: true,
  imports: [CommonModule, AccessWindowComponent, FormFlowDirective],
  templateUrl: './purchase-annulment.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PurchaseAnnulmentComponent {
  private readonly service = inject(PurchaseAnnulmentService);
  private readonly navigation = inject(NavigationService);

  readonly content = toSignal(inject(ContentService).loadJson<PurchaseAnnulmentContent>('purchase-annulment'));
  readonly candidates = signal<AnnulmentCandidate[]>([]);
  readonly selected = signal<AnnulmentCandidate | null>(null);
  /** Comando87: 'confirm' = Etiqueta97 + SI/NO; 'exported' = Etiqueta98 + Aceptar. */
  readonly prompt = signal<'confirm' | 'exported' | null>(null);
  readonly message = signal<string | null>(null);
  readonly error = signal(false);
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

  choose(candidate: AnnulmentCandidate): void {
    this.selected.set(candidate);
    this.prompt.set(null);
    this.message.set(null);
  }

  startAnnul(): void {
    this.message.set(null);
    this.prompt.set(this.selected()!.exported ? 'exported' : 'confirm');
  }

  confirm(): void {
    const candidate = this.selected()!;
    this.prompt.set(null);
    this.service.annul(candidate).subscribe({
      next: (annulled) => {
        this.selected.set(annulled);
        this.candidates.update((list) => list.map((c) => (c.module === annulled.module && c.id === annulled.id ? annulled : c)));
        this.error.set(false);
        this.message.set(this.content()!.messages.annulled.replace('{invoice}', String(annulled.invoiceNumber)));
      },
      error: (err) => this.fail(err?.error?.message ?? this.content()!.messages.error),
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
