import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { AnnulmentDirection } from '../../../core/models/purchase-annulment.model';

/** Un boton de la barra: direccion, texto y ayuda (del JSON de la pantalla). */
export interface NavigatorButton {
  readonly direction: AnnulmentDirection;
  readonly label: string;
  readonly title: string;
}

/** Botones |◄ ◄ ► ►| para recorrer las compras una por una (Anular Documento y Consultar Compras). */
@Component({
  selector: 'app-invoice-navigator',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="flex gap-1" data-testid="navigation">
      <button
        *ngFor="let b of buttons()"
        type="button"
        class="w-10 rounded border border-slate-400 bg-slate-100 py-1 font-bold hover:bg-slate-200"
        [title]="b.title"
        [attr.aria-label]="b.title"
        (click)="navigate.emit(b.direction)"
      >{{ b.label }}</button>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InvoiceNavigatorComponent {
  readonly buttons = input.required<readonly NavigatorButton[]>();
  readonly navigate = output<AnnulmentDirection>();
}
