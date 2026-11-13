import { CommonModule } from '@angular/common';
import { ScrollingModule, CdkVirtualScrollViewport } from '@angular/cdk/scrolling';
import { ChangeDetectionStrategy, Component, computed, inject, signal, viewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { of } from 'rxjs';

import { Agency } from '../../core/models/agency.model';
import { ColumnInfo, ColumnKind, TableInfo } from '../../core/models/table-access.model';
import { AgencyService } from '../../core/services/agency.service';
import { AuthService } from '../../core/services/auth.service';
import { ContentService } from '../../core/services/content.service';
import { NavigationService } from '../../core/services/navigation.service';
import { TableAccessService } from '../../core/services/table-access.service';
import { AccessWindowComponent, AppButtonComponent } from '../../shared/ui';
import { serverError } from '../../shared/utils/server-error';
import { TableAccessContent } from './table-access.model';

/** Filas por tramo: se piden mas a medida que se baja (el servidor acepta hasta 200). */
export const CHUNK = 100;
/** Al escribir un filtro o la busqueda se espera este rato antes de consultar, para pedir una sola vez. */
export const DEBOUNCE_MS = 300;
/** Alto de cada fila en la hoja (el desplazamiento virtual lo necesita fijo). */
export const ROW_PX = 26;
/** Se pide el siguiente tramo cuando faltan estas filas para el final de lo cargado. */
const PRELOAD = 30;
/** Filas que salta Re Pag / Av Pag. */
const PAGE_ROWS = 20;
const WIDTHS: Record<ColumnKind, number> = { TEXT: 180, NUMBER: 110, DATE: 150, BOOLEAN: 80 };

type Sort = { readonly column: string; readonly dir: 'asc' | 'desc' };

/**
 * "Acceso a Tablas" (Acceso Principal; RF-03, formulario Tablas de Access). C8 (prueba del 11-nov, punto 6): al abrir
 * una tabla se ve como su hoja de datos en Access: todas las columnas con su nombre, filas que se cargan al bajar y
 * "Registro X de N". Busqueda, filtros por columna y orden los hace el servidor. Solo lectura (RN-03.3).
 */
@Component({
  selector: 'app-table-access',
  standalone: true,
  imports: [CommonModule, ScrollingModule, AccessWindowComponent, AppButtonComponent],
  templateUrl: './table-access.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TableAccessComponent {
  private readonly service = inject(TableAccessService);
  private readonly navigation = inject(NavigationService);
  readonly auth = inject(AuthService);
  readonly rowPx = ROW_PX;

  readonly page = toSignal(inject(ContentService).loadJson<TableAccessContent>('table-access'));
  readonly tables = toSignal(this.service.list(), { initialValue: [] as TableInfo[] });
  /** Solo el Administrador elige agencia (respuesta del 12-nov); el Usuario ve siempre la suya. */
  readonly agencies = toSignal(this.auth.isAdmin() ? inject(AgencyService).list() : of([] as Agency[]), {
    initialValue: [] as Agency[],
  });

  readonly table = signal<TableInfo | null>(null);
  /** Agencia pedida por el Administrador: su id o "ALL" (Todas). */
  readonly agency = signal('');
  readonly search = signal('');
  readonly filters = signal<Readonly<Record<string, string>>>({});
  readonly sort = signal<Sort | null>(null);
  readonly rows = signal<(string | null)[][]>([]);
  readonly total = signal(0);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  /** El registro actual (base 0), como el de la hoja de datos de Access. */
  readonly current = signal(0);

  readonly showAgency = computed(() => this.auth.isAdmin() && !!this.table()?.byAgency);
  readonly width = computed(() => (this.table()?.columns ?? []).reduce((sum, c) => sum + this.columnWidth(c), 0));
  readonly counter = computed(() => {
    const total = this.total();
    const x = total === 0 ? 0 : this.current() + 1;
    return (this.page()?.counter ?? '').replace('{x}', String(x)).replace('{n}', String(total));
  });

  private readonly viewport = viewChild(CdkVirtualScrollViewport);
  private request = 0;
  private timer?: ReturnType<typeof setTimeout>;

  label(table: TableInfo): string {
    return this.page()?.tables[table.name] ?? table.name;
  }

  columnWidth(column: ColumnInfo): number {
    return WIDTHS[column.kind];
  }

  hasRange(column: ColumnInfo): boolean {
    return column.kind === 'NUMBER' || column.kind === 'DATE';
  }

  open(table: TableInfo): void {
    this.table.set(table);
    this.agency.set(this.auth.isAdmin() ? String(this.auth.agencyId() ?? '') : '');
    this.search.set('');
    this.filters.set({});
    this.sort.set(null);
    this.reload();
  }

  back(): void {
    clearTimeout(this.timer);
    this.request++;
    this.table.set(null);
    this.rows.set([]);
    this.total.set(0);
    this.error.set(null);
  }

  close(): void {
    this.navigation.goTo('/acceso-principal');
  }

  setAgency(value: string): void {
    this.agency.set(value);
    this.reload();
  }

  /** Como en Access: ascendente, luego descendente, luego sin orden (el de la tabla). */
  toggleSort(column: string): void {
    const sort = this.sort();
    if (sort?.column !== column) {
      this.sort.set({ column, dir: 'asc' });
    } else {
      this.sort.set(sort.dir === 'asc' ? { column, dir: 'desc' } : null);
    }
    this.reload();
  }

  sortMark(column: string): string {
    const sort = this.sort();
    if (sort?.column !== column) {
      return '';
    }
    return sort.dir === 'asc' ? '▲' : '▼';
  }

  setSearch(value: string): void {
    this.search.set(value);
    this.schedule();
  }

  /** key: la columna (contiene) o columna.from / columna.to (rango). */
  setFilter(key: string, value: string): void {
    this.filters.update((f) => ({ ...f, [key]: value }));
    this.schedule();
  }

  filterValue(key: string): string {
    return this.filters()[key] ?? '';
  }

  /** Al bajar: cuando el primer registro visible se acerca al final de lo cargado, pide el siguiente tramo. */
  onScrolled(firstVisible: number): void {
    const loaded = this.rows().length;
    if (!this.loading() && loaded < this.total() && firstVisible + PRELOAD >= loaded) {
      this.load(loaded);
    }
  }

  /** Flechas, Re Pag, Av Pag, Inicio y Fin mueven el registro actual; Esc vuelve a la lista de tablas. */
  onKey(event: KeyboardEvent): void {
    const last = this.rows().length - 1;
    const moves: Record<string, number> = {
      ArrowDown: this.current() + 1, ArrowUp: this.current() - 1, PageDown: this.current() + PAGE_ROWS,
      PageUp: this.current() - PAGE_ROWS, Home: 0, End: last,
    };
    if (event.key === 'Escape') {
      event.preventDefault();
      this.back();
      return;
    }
    if (!(event.key in moves) || last < 0) {
      return;
    }
    event.preventDefault();
    const next = Math.min(Math.max(moves[event.key], 0), last);
    this.current.set(next);
    this.viewport()?.scrollToIndex(next);
    this.onScrolled(next);
  }

  private schedule(): void {
    clearTimeout(this.timer);
    this.timer = setTimeout(() => this.reload(), DEBOUNCE_MS);
  }

  private reload(): void {
    clearTimeout(this.timer);
    this.rows.set([]);
    this.total.set(0);
    this.current.set(0);
    this.viewport()?.scrollToIndex(0);
    this.load(0);
  }

  private load(offset: number): void {
    const table = this.table();
    if (!table) {
      return;
    }
    const id = ++this.request;
    this.loading.set(true);
    this.error.set(null);
    this.service.rows(table.name, this.params(table, offset)).subscribe({
      next: (page) => {
        if (id !== this.request) {
          return;
        }
        this.rows.update((rows) => (offset === 0 ? page.rows : [...rows, ...page.rows]));
        this.total.set(page.total);
        this.loading.set(false);
      },
      error: (err) => {
        if (id !== this.request) {
          return;
        }
        this.loading.set(false);
        this.error.set(serverError(err, this.page()?.errorMessage));
      },
    });
  }

  private params(table: TableInfo, offset: number): Record<string, string> {
    const params: Record<string, string> = { offset: String(offset), limit: String(CHUNK) };
    const sort = this.sort();
    if (sort) {
      params['sort'] = sort.column;
      params['dir'] = sort.dir;
    }
    const search = this.search().trim();
    if (search) {
      params['q'] = search;
    }
    for (const [key, value] of Object.entries(this.filters())) {
      if (value.trim()) {
        params['f.' + key] = value.trim();
      }
    }
    if (this.showAgency() && this.agency() && table.byAgency) {
      params['agency'] = this.agency();
    }
    return params;
  }
}
