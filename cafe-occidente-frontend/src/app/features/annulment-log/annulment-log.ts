import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { BehaviorSubject, catchError, firstValueFrom, map, of, switchMap } from 'rxjs';

import { Agency } from '../../core/models/agency.model';
import { AnnulmentLogEntry, AnnulmentLogFilter, AnnulmentLogPage } from '../../core/models/annulment-log.model';
import { User } from '../../core/models/user.model';
import { AgencyService } from '../../core/services/agency.service';
import { AnnulmentLogService } from '../../core/services/annulment-log.service';
import { ContentService } from '../../core/services/content.service';
import { UserService } from '../../core/services/user.service';
import { AccessWindowComponent, AppButtonComponent, FormFlowDirective } from '../../shared/ui';
import { ExportTable, TableExporter } from '../../shared/utils/export-table';
import { formatKilos, formatMoney } from '../../shared/utils/number-format';
import { serverError } from '../../shared/utils/server-error';

export interface AnnulmentLogContent {
  readonly windowTitle: string;
  readonly heading: string;
  readonly agencyLabel: string;
  readonly typeLabel: string;
  readonly userLabel: string;
  readonly fromLabel: string;
  readonly toLabel: string;
  readonly searchButton: string;
  readonly exportButton: string;
  readonly previousButton: string;
  readonly nextButton: string;
  readonly all: string;
  readonly emptyMessage: string;
  readonly errorMessage: string;
  readonly totalLabel: string;
  readonly title: string;
  readonly fileName: string;
  readonly columns: string[];
  readonly types: Record<string, string>;
  readonly origins: Record<string, string>;
  readonly values: Record<string, string>;
  readonly money: string[];
  readonly kilos: string[];
}

/** Filas por pagina en pantalla; la exportacion pide hasta EXPORT_SIZE de una vez. */
export const PAGE_SIZE = 50;
export const EXPORT_SIZE = 5000;

interface Result {
  readonly page: AnnulmentLogPage | null;
  readonly error: string | null;
}

/**
 * Consultar anulaciones (Acceso Principal, solo Administrador, 26-nov): quien anulo, cuando, por que y los valores del
 * documento al anularlo, de las compras y remisiones de la web y de las compras anuladas que vienen de Access.
 */
@Component({
  selector: 'app-annulment-log',
  standalone: true,
  imports: [CommonModule, FormsModule, AccessWindowComponent, AppButtonComponent, FormFlowDirective],
  templateUrl: './annulment-log.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AnnulmentLogComponent {
  private readonly service = inject(AnnulmentLogService);
  private readonly exporter = inject(TableExporter);

  readonly content = toSignal(inject(ContentService).loadJson<AnnulmentLogContent>('annulment-log'));
  readonly agencies = toSignal(inject(AgencyService).list(), { initialValue: [] as Agency[] });
  readonly users = toSignal(inject(UserService).list(), { initialValue: [] as User[] });

  agencyId: number | null = null;
  documentType = '';
  userId: number | null = null;
  from = '';
  to = '';

  private readonly query = new BehaviorSubject<AnnulmentLogFilter>(this.filter(0, PAGE_SIZE));
  private readonly result = toSignal(this.query.pipe(switchMap((q) => this.service.search(q).pipe(
    map((page): Result => ({ page, error: null })),
    catchError((err) => of<Result>({ page: null, error: serverError(err, this.content()?.errorMessage) })),
  ))), { initialValue: { page: null, error: null } as Result });

  readonly page = computed(() => this.result().page);
  readonly error = computed(() => this.result().error);
  readonly pageIndex = signal(0);
  readonly lastPage = computed(() => Math.max(0, Math.ceil((this.page()?.total ?? 0) / PAGE_SIZE) - 1));

  search(pageIndex = 0): void {
    this.pageIndex.set(pageIndex);
    this.query.next(this.filter(pageIndex, PAGE_SIZE));
  }

  /** Excel con todas las anulaciones del filtro (hasta EXPORT_SIZE), con sus valores al anular. */
  async export(): Promise<void> {
    const c = this.content();
    if (!c) {
      return;
    }
    const all = await firstValueFrom(this.service.search(this.filter(0, EXPORT_SIZE)));
    const table: ExportTable = {
      title: c.title,
      columns: c.columns,
      rows: all.items.map((e) => ({
        cells: [this.when(e), c.origins[e.origin] ?? e.origin, e.agencyName, c.types[e.documentType] ?? e.documentType,
          e.documentNumber, e.username, e.reason, this.valuesText(e), e.note],
      })),
    };
    await this.exporter.xlsx(table, c.fileName);
  }

  /** "Kilos netos: 1.200,50; Neto a pagar: 9.267.436; ..." con los nombres y el formato de la pantalla. */
  valuesText(entry: AnnulmentLogEntry): string {
    const c = this.content();
    return Object.entries(entry.values).map(([key, value]) => `${c?.values[key] ?? key}: ${this.formatted(key, value)}`)
      .join('; ');
  }

  when(entry: AnnulmentLogEntry): string {
    if (!entry.annulledAt) {
      return '';
    }
    return new Date(entry.annulledAt).toLocaleString('es-CO', { timeZone: 'America/Bogota', dateStyle: 'short', timeStyle: 'short' });
  }

  private formatted(key: string, value: string): string {
    const c = this.content();
    const number = Number(value);
    if (c?.money.includes(key) && !Number.isNaN(number)) {
      return formatMoney(number);
    }
    return c?.kilos.includes(key) && !Number.isNaN(number) ? formatKilos(number) : value;
  }

  private filter(page: number, size: number): AnnulmentLogFilter {
    return { agencyId: this.agencyId, documentType: this.documentType, userId: this.userId, from: this.from, to: this.to,
      page, size };
  }
}
