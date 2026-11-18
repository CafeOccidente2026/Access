import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

import { AnnulmentModule } from '../../core/models/purchase-annulment.model';
import { FormFlowDirective, InvoiceNavigatorComponent, NavigatorButton } from '../../shared/ui';
import { InvoiceBrowser } from './invoice-browser';

/** Los textos de la barra de busqueda (del JSON de cada pantalla). */
export interface InvoiceSearchTexts {
  readonly invoiceLabel: string;
  readonly searchLabel: string;
  readonly navigation: NavigatorButton[];
  readonly modules: Record<AnnulmentModule, string>;
}

/**
 * Factura + Buscar + |◄ ◄ ► ►| y, si la factura esta en varios modulos, cual mostrar. Comun a Consultar Compras y
 * Corregir Compra. Lo que la pantalla agregue (Volver) va entre Buscar y el navegador; su aviso, con el atributo
 * {@code message}, al final de la barra.
 */
@Component({
  selector: 'app-invoice-search',
  standalone: true,
  imports: [CommonModule, FormFlowDirective, InvoiceNavigatorComponent],
  template: `
    <form class="flex flex-wrap items-end gap-2 text-sm" appFormFlow (submit)="browser.search($event, invoiceInput.value)">
      <label class="flex flex-col gap-1">
        <span class="font-bold text-blue-900">{{ texts.invoiceLabel }}:</span>
        <input #invoiceInput type="text" inputmode="numeric" data-field-key="invoice" class="w-40 rounded border border-slate-300 px-2 py-1" />
      </label>
      <button type="submit" data-flow-submit class="rounded border border-slate-400 bg-slate-100 px-4 py-1.5 font-bold hover:bg-slate-200">{{ texts.searchLabel }}</button>
      <ng-content></ng-content>
      <app-invoice-navigator [buttons]="texts.navigation" (navigate)="browser.go($event)"></app-invoice-navigator>
      <ng-content select="[message]"></ng-content>
    </form>

    <div *ngIf="browser.candidates().length > 1" data-testid="candidates" class="mt-2 flex flex-wrap gap-2 text-sm">
      <button
        *ngFor="let cand of browser.candidates()"
        type="button"
        class="rounded border px-3 py-1"
        [class.border-blue-500]="browser.selected() === cand"
        [class.border-slate-300]="browser.selected() !== cand"
        (click)="browser.choose(cand)"
      >
        {{ texts.modules[cand.module] }} · {{ cand.prefix }} {{ cand.invoiceNumber }}
      </button>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InvoiceSearchComponent {
  @Input({ required: true }) browser!: InvoiceBrowser;
  @Input({ required: true }) texts!: InvoiceSearchTexts;
}
