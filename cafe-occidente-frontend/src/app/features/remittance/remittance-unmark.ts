import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';

import { Remittance } from '../../core/models/remittance.model';
import { ContentService } from '../../core/services/content.service';
import { RemittanceService } from '../../core/services/remittance.service';
import { AppButtonComponent } from '../../shared/ui/app-button/app-button';
import { ConfirmDialogComponent } from '../../shared/ui/confirm-dialog/confirm-dialog';
import { saveOnce } from '../../shared/utils/save-once';
import { serverError } from '../../shared/utils/server-error';

/** Textos (public/assets/data/remittance-unmark.json). */
export interface RemittanceUnmarkContent {
  readonly title: string;
  readonly numberLabel: string;
  readonly searchLabel: string;
  readonly unmarkLabel: string;
  readonly confirm: string;
  readonly yesLabel: string;
  readonly noLabel: string;
  readonly statuses: Record<string, string>;
  readonly messages: Record<'missingNumber' | 'notFound' | 'fromRemission' | 'unmarked' | 'error', string>;
}

const fill = (text: string, number: string) => text.replace('{number}', number);

/**
 * "Desmarca Remesa" de Access, solo para el Administrador y solo para las remesas viejas sin remision (las de Access o
 * las de Genera Remesa): busca por numero, pide confirmacion con el foco en No y sus compras vuelven a pendientes. Las
 * remesas que salieron con una remision se deshacen con Anular Remision.
 */
@Component({
  selector: 'app-remittance-unmark',
  standalone: true,
  imports: [CommonModule, AppButtonComponent, ConfirmDialogComponent],
  template: `
    <form *ngIf="content() as c" class="flex flex-wrap items-end gap-3 rounded border border-slate-300 p-2 text-sm"
      (submit)="$event.preventDefault(); search(number.value)">
      <span class="self-center font-semibold text-blue-900">{{ c.title }}</span>
      <label class="flex flex-col gap-1">
        <span class="font-medium text-blue-900">{{ c.numberLabel }}</span>
        <input #number data-testid="remittance-number" class="w-40 rounded border border-slate-300 px-2 py-1 uppercase" />
      </label>
      <app-button [label]="c.searchLabel" (pressed)="search(number.value)"></app-button>
      <span *ngIf="found() as r" class="self-center" data-testid="remittance-found">{{ r.number }} · {{ c.statuses[r.status] ?? r.status }}</span>
      <app-button *ngIf="canUnmark()" [label]="c.unmarkLabel" [disabled]="unmarking()" (pressed)="confirming.set(true)"></app-button>
      <span *ngIf="message() as m" class="self-center text-red-600">{{ m }}</span>
      <app-confirm-dialog
        *ngIf="confirming()"
        [message]="fill(c.confirm, found()?.number ?? '')"
        [confirmLabel]="c.yesLabel"
        [cancelLabel]="c.noLabel"
        [safeFocus]="true"
        (confirmed)="unmark()"
        (cancelled)="confirming.set(false)"
      ></app-confirm-dialog>
    </form>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RemittanceUnmarkComponent {
  private readonly service = inject(RemittanceService);

  readonly content = toSignal(inject(ContentService).loadJson<RemittanceUnmarkContent>('remittance-unmark'));
  readonly found = signal<Remittance | null>(null);
  readonly message = signal<string | null>(null);
  readonly confirming = signal(false);
  readonly unmarking = signal(false);
  readonly canUnmark = computed(() => this.found()?.status === 'VIGENTE' && !this.found()?.fromRemission);
  readonly fill = fill;

  search(number: string): void {
    const c = this.content();
    this.found.set(null);
    this.message.set(null);
    if (!c) {
      return;
    }
    if (!number.trim()) {
      this.message.set(c.messages.missingNumber);
      return;
    }
    this.service.findByNumber(number.trim()).subscribe({
      next: (list) => {
        const r = list[0] ?? null;
        this.found.set(r);
        this.message.set(!r ? c.messages.notFound : r.fromRemission ? c.messages.fromRemission : null);
      },
      error: (err) => this.message.set(serverError(err, c.messages.error)),
    });
  }

  unmark(): void {
    const r = this.found();
    const c = this.content();
    this.confirming.set(false);
    if (!r || !c) {
      return;
    }
    saveOnce(this.unmarking, () => this.service.unmark(r.id), {
      next: (unmarked) => {
        this.found.set(unmarked);
        this.message.set(fill(c.messages.unmarked, unmarked.number));
      },
      error: (err) => this.message.set(serverError(err, c.messages.error)),
    });
  }
}
