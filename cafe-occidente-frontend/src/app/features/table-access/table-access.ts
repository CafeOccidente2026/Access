import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';

import { QueryableTable, TablePage } from '../../core/models/table-access.model';
import { AuthService } from '../../core/services/auth.service';
import { ContentService } from '../../core/services/content.service';
import { NavigationService } from '../../core/services/navigation.service';
import { TableAccessService } from '../../core/services/table-access.service';
import { AccessWindowComponent, AppButtonComponent } from '../../shared/ui';
import { TableAccessContent } from './table-access.model';

/** Filas por pagina; el servidor acepta hasta 200. */
export const PAGE_SIZE = 100;

/** "numero_factura" -> "Numero factura": el encabezado de la hoja de datos sale del nombre de la columna. */
export function columnLabel(column: string): string {
  const text = column.replaceAll('_', ' ');
  return text.charAt(0).toUpperCase() + text.slice(1);
}

/**
 * Pantalla "Acceso a Tablas" (Acceso Principal; RF-03, formulario Tablas de Access): un boton por tabla
 * y su hoja de datos de la agencia de trabajo, de solo lectura (RN-03.3). Administrador o con el permiso.
 */
@Component({
  selector: 'app-table-access',
  standalone: true,
  imports: [CommonModule, AccessWindowComponent, AppButtonComponent],
  templateUrl: './table-access.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TableAccessComponent {
  private readonly service = inject(TableAccessService);
  private readonly navigation = inject(NavigationService);
  readonly auth = inject(AuthService);

  readonly page = toSignal(inject(ContentService).loadJson<TableAccessContent>('table-access'));
  readonly tables = toSignal(this.service.list(), { initialValue: [] as QueryableTable[] });
  readonly table = signal<QueryableTable | null>(null);
  readonly result = signal<TablePage | null>(null);
  readonly pageIndex = signal(0);
  readonly error = signal(false);
  readonly headers = computed(() => (this.result()?.columns ?? []).map(columnLabel));
  readonly lastPage = computed(() => Math.max(0, Math.ceil((this.result()?.total ?? 0) / PAGE_SIZE) - 1));

  open(table: QueryableTable, pageIndex = 0): void {
    this.table.set(table);
    this.pageIndex.set(pageIndex);
    this.error.set(false);
    this.service.page(table, pageIndex, PAGE_SIZE).subscribe({
      next: (result) => this.result.set(result),
      error: () => this.error.set(true),
    });
  }

  back(): void {
    this.table.set(null);
    this.result.set(null);
  }

  close(): void {
    this.navigation.goTo('/acceso-principal');
  }
}
