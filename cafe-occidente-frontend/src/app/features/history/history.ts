import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { of } from 'rxjs';

import { Agency } from '../../core/models/agency.model';
import { ChangeHistoryEntry, ChangeHistoryPage } from '../../core/models/history.model';
import { User } from '../../core/models/user.model';
import { AgencyService } from '../../core/services/agency.service';
import { AuthService } from '../../core/services/auth.service';
import { ContentService } from '../../core/services/content.service';
import { HistoryService } from '../../core/services/history.service';
import { UserService } from '../../core/services/user.service';
import { AccessWindowComponent, AppButtonComponent } from '../../shared/ui';
import { HistoryContent } from './history.model';

/** Filas por pagina; el servidor acepta hasta 200. */
export const PAGE_SIZE = 50;

/** Una linea del detalle: "Campo: antes → despues" (en una creacion solo el valor). */
export interface HistoryDetail {
  readonly field: string;
  readonly before: string | null;
  readonly after: string | null;
  readonly created: boolean;
}

/**
 * Pantalla Historial (Acceso Principal, solo Administrador): quien cambio el Registro de Control,
 * publico anuncios o creo, reasigno, cambio o desactivo usuarios y agencias, con el antes y el despues.
 */
@Component({
  selector: 'app-history',
  standalone: true,
  imports: [CommonModule, FormsModule, AccessWindowComponent, AppButtonComponent],
  templateUrl: './history.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HistoryComponent {
  private readonly content = inject(ContentService);
  private readonly historyService = inject(HistoryService);

  readonly page = toSignal(this.content.loadJson<HistoryContent>('history'));
  private readonly auth = inject(AuthService);
  /** Con el permiso de la pantalla (RF-02), quien no es Administrador solo ve los cambios de su agencia. */
  readonly isAdmin = this.auth.isAdmin();
  readonly agencies = toSignal(
    this.isAdmin ? inject(AgencyService).list() : of<Agency[]>([{ id: this.auth.agencyId() ?? 0, name: this.auth.agencyName() ?? '' }]),
    { initialValue: [] as Agency[] },
  );
  readonly users = toSignal(this.isAdmin ? inject(UserService).list() : of<User[]>([]), { initialValue: [] as User[] });
  readonly result = signal<ChangeHistoryPage | null>(null);
  readonly pageIndex = signal(0);
  readonly error = signal(false);
  readonly lastPage = computed(() => Math.max(0, Math.ceil((this.result()?.total ?? 0) / PAGE_SIZE) - 1));

  entity = '';
  agencyId: number | null = null;
  userId: number | null = null;
  from = '';
  to = '';

  constructor() {
    this.search();
  }

  search(pageIndex = 0): void {
    this.error.set(false);
    this.pageIndex.set(pageIndex);
    this.historyService
      .search({ entity: this.entity, agencyId: this.agencyId, userId: this.userId, from: this.from, to: this.to, page: pageIndex })
      .subscribe({ next: (page) => this.result.set(page), error: () => this.error.set(true) });
  }

  details(entry: ChangeHistoryEntry): HistoryDetail[] {
    return Object.entries(entry.after).map(([field, after]) => ({
      field: this.page()?.fields[field] ?? field,
      before: this.display(entry.before?.[field] ?? null),
      after: this.display(after),
      created: entry.before === null,
    }));
  }

  private display(value: string | null): string | null {
    if (value === 'true') {
      return this.page()?.yes ?? value;
    }
    return value === 'false' ? (this.page()?.no ?? value) : value;
  }
}
