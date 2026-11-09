import { CommonModule, Location } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';

import { AnnouncementQuotaResponse } from '../../core/models/announcement-quota.model';
import { FuturePurchaseRestoreRow, PendingAnnouncementRow } from '../../core/models/future-purchase.model';
import { AnnouncementQuotaService } from '../../core/services/announcement-quota.service';
import { ContentService } from '../../core/services/content.service';
import { FuturePurchaseService } from '../../core/services/future-purchase.service';
import { AccessWindowComponent, FormFlowDirective } from '../../shared/ui';
import { formatDisplayNumber } from '../../shared/utils/number-format';
import { saveOnce } from '../../shared/utils/save-once';

/**
 * 'future' = Restaurar Compras Anunciadas; 'quota' = Restaurar Anuncios con Cupos (solo consulta, P5);
 * 'pending' = Anunciadas Hoy (solo consulta, HU-31 con la respuesta P2).
 */
type View = 'future' | 'quota' | 'pending';

export interface FutureRestoreContent {
  readonly idNumberLabel: string;
  readonly searchButton: string;
  readonly restoreButton: string;
  readonly backLabel: string;
  readonly views: Record<View, { windowTitle: string; heading: string; note: string; columns: string[] }>;
  readonly messages: { empty: string; error: string; restored: string; unchanged: string };
}

const kg = (v: number) => formatDisplayNumber(v, 'currency');

export function futureCells(r: FuturePurchaseRestoreRow): string[] {
  return [r.announcementNumber, r.announcementDate, r.specialType, kg(r.announcedKg), kg(r.deliveredKg),
    kg(r.currentBalance), kg(r.recalculatedBalance)];
}

export function quotaCells(q: AnnouncementQuotaResponse): string[] {
  return [String(q.announcementNumber), q.announcementDate, q.specialType, kg(q.assignedQuota), kg(q.deliveredKg),
    kg(q.balance)];
}

/** Anunciadas Hoy: Kl_Entregados = kilos anunciados menos el saldo. */
export function pendingCells(p: PendingAnnouncementRow): string[] {
  return [p.announcementNumber, p.announcementDate, p.specialType, kg(p.healthyUnitPrice), String(p.growers),
    kg(p.announcedKg), kg(p.announcedKg - p.remainingKg), kg(p.remainingKg)];
}

/**
 * Tablas de Compras a Futuro: Anunciadas Hoy (Administrador y Usuario) y los dos "Restaurar" (solo el Administrador, Q5). Restaurar Compras Anunciadas recalcula desde
 * cero el saldo de los compromisos de una cédula (P6): primero muestra el saldo guardado y el recalculado, y Restaurar
 * lo aplica. Restaurar Anuncios con Cupos solo muestra el saldo de cada cupo, que ya se calcula al vuelo (P5).
 */
@Component({
  selector: 'app-future-restore',
  standalone: true,
  imports: [CommonModule, AccessWindowComponent, FormFlowDirective],
  templateUrl: './future-restore.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FutureRestoreComponent {
  private readonly futureService = inject(FuturePurchaseService);
  private readonly quotaService = inject(AnnouncementQuotaService);
  private readonly location = inject(Location);

  readonly viewKey: View = inject(ActivatedRoute).snapshot.data['view'];
  readonly content = toSignal(inject(ContentService).loadJson<FutureRestoreContent>('future-restore'));
  readonly view = computed(() => this.content()?.views[this.viewKey]);

  readonly idNumber = signal('');
  readonly futureRows = signal<FuturePurchaseRestoreRow[] | null>(null);
  readonly saving = signal(false);
  readonly message = signal<string | null>(null);
  readonly error = signal(false);
  private readonly quotas = rxResource({
    params: () => (this.viewKey === 'quota' ? true : undefined),
    stream: () => this.quotaService.list(),
  });
  private readonly pending = rxResource({
    params: () => (this.viewKey === 'pending' ? true : undefined),
    stream: () => this.futureService.pending(),
  });

  readonly rows = computed<string[][] | null>(() => {
    if (this.viewKey === 'quota') {
      return this.quotas.hasValue() ? this.quotas.value().map(quotaCells) : null;
    }
    if (this.viewKey === 'pending') {
      return this.pending.hasValue() ? this.pending.value().map(pendingCells) : null;
    }
    return this.futureRows()?.map(futureCells) ?? null;
  });
  readonly failed = computed(() =>
    this.error() || this.quotas.status() === 'error' || this.pending.status() === 'error');
  /** Hay algo que restaurar: algún saldo guardado no es el recalculado. */
  readonly canRestore = computed(() =>
    (this.futureRows() ?? []).some((r) => r.currentBalance !== r.recalculatedBalance));

  search(event: Event, idNumber: string): void {
    event.preventDefault();
    this.idNumber.set(idNumber.replace(/\D/g, ''));
    this.message.set(null);
    this.error.set(false);
    if (!this.idNumber()) {
      return;
    }
    this.futureService.restorePreview(this.idNumber()).subscribe({
      next: (rows) => this.futureRows.set(rows),
      error: () => this.error.set(true),
    });
  }

  restore(): void {
    if (!this.canRestore()) {
      return;
    }
    const changed = this.futureRows()!.filter((r) => r.currentBalance !== r.recalculatedBalance).length;
    saveOnce(this.saving, () => this.futureService.restore(this.idNumber()), {
      next: (rows) => {
        // Lo que quedó guardado es el saldo recalculado.
        this.futureRows.set(rows.map((r) => ({ ...r, currentBalance: r.recalculatedBalance })));
        this.message.set(this.content()?.messages.restored.replace('{n}', String(changed)) ?? null);
      },
      error: () => this.error.set(true),
    });
  }

  back(): void {
    this.location.back();
  }
}
