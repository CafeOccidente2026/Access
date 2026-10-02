import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';

import { NessQuotaBalanceRow, NessQuotaRow } from '../../core/models/vendor.model';
import { AuthService } from '../../core/services/auth.service';
import { ContentService } from '../../core/services/content.service';
import { NavigationService } from '../../core/services/navigation.service';
import { VendorService } from '../../core/services/vendor.service';
import { AccessWindowComponent, AgencyPickerComponent, FormFlowDirective } from '../../shared/ui';

type View = 'quotas' | 'balances';

export interface NessQuotasContent {
  readonly backLabel: string;
  readonly backRoute: string;
  readonly searchLabel: string;
  readonly searchButton: string;
  readonly agencyLabel: string;
  readonly acceptLabel: string;
  readonly allAgenciesHint: string;
  readonly recordLabel: string;
  readonly previousLabel: string;
  readonly nextLabel: string;
  readonly messages: Record<'empty' | 'error', string>;
  readonly views: Record<View, { windowTitle: string; heading: string; columns: string[]; note?: string }>;
}

/** CUPO/FACTURADOS/SALDO con Format Standard (2 decimales); CEDULA General Number (sin miles). */
export function quotaCells(row: NessQuotaRow | NessQuotaBalanceRow): string[] {
  const standard = (v: number) => v.toLocaleString('es-CO', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  const base = [row.idNumber, row.names, row.program, standard(row.quota)];
  return 'balance' in row ? [...base, standard(row.invoicedKg), standard(row.balance)] : base;
}

/**
 * "Consulta Cupos Ness" (formulario CuposNess, hoja de datos de la tabla NESS) y "Consulta Saldos
 * Cupos Ness" (macro CalCuposSaldoNess -> NESSYRAINSALDO, mas Cupo y Saldo). Solo lectura.
 */
@Component({
  selector: 'app-ness-quotas',
  standalone: true,
  imports: [CommonModule, AccessWindowComponent, AgencyPickerComponent, FormFlowDirective],
  templateUrl: './ness-quotas.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NessQuotasComponent {
  private readonly vendorService = inject(VendorService);
  private readonly navigation = inject(NavigationService);

  readonly viewKey: View = inject(ActivatedRoute).snapshot.data['view'];
  readonly admin = inject(AuthService).isAdmin();
  readonly content = toSignal(inject(ContentService).loadJson<NessQuotasContent>('ness-quotas'));
  readonly view = computed(() => this.content()?.views[this.viewKey]);

  readonly agencyId = signal<number | null>(inject(AuthService).agencyId());
  readonly rows = signal<string[][]>([]);
  readonly total = signal(0);
  readonly page = signal(0);
  readonly loaded = signal(false);
  readonly error = signal(false);
  private idNumber = '';
  readonly pageSize = 50;

  constructor() {
    this.load();
  }

  search(event: Event, idNumber: string): void {
    event.preventDefault();
    this.idNumber = idNumber.replace(/\D/g, '');
    this.page.set(0);
    this.load();
  }

  goPage(page: number): void {
    this.page.set(page);
    this.load();
  }

  load(): void {
    this.error.set(false);
    const done = { error: () => this.error.set(true) };
    if (this.viewKey === 'quotas') {
      this.vendorService.nessQuotas(this.idNumber, this.page()).subscribe({
        next: (p) => this.show(p.rows.map(quotaCells), p.total),
        ...done,
      });
    } else {
      this.vendorService.nessQuotaBalances(this.agencyId()).subscribe({
        next: (rows) => this.show(rows.map(quotaCells), rows.length),
        ...done,
      });
    }
  }

  back(): void {
    this.navigation.goTo(this.content()?.backRoute);
  }

  private show(rows: string[][], total: number): void {
    this.rows.set(rows);
    this.total.set(total);
    this.loaded.set(true);
  }
}
