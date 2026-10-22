import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { of } from 'rxjs';

import { AgencyService } from '../../core/services/agency.service';
import { AuthService } from '../../core/services/auth.service';
import { ContentService } from '../../core/services/content.service';
import { AccessWindowComponent, MenuButtonGridComponent } from '../../shared/ui';
import { MainMenuContent } from './main-menu.model';

/**
 * Panel "MENU PRINCIPAL": puerta de entrada a los modulos del aplicativo. Las opciones se filtran por
 * rol en la rejilla (RF-05, RN-05.1). El boton Agencias (RF-05.1) solo aparece para el Administrador.
 */
@Component({
  selector: 'app-main-menu',
  standalone: true,
  imports: [CommonModule, AccessWindowComponent, MenuButtonGridComponent],
  templateUrl: './main-menu.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MainMenuComponent {
  private readonly content = inject(ContentService);
  readonly auth = inject(AuthService);

  readonly data = toSignal(this.content.loadJson<MainMenuContent>('main-menu'));
  readonly isAdmin = this.auth.isAdmin();
  /** RN-05.1.1: lista desplegable con todas las agencias. */
  readonly agencies = toSignal(this.isAdmin ? inject(AgencyService).list() : of([]), { initialValue: [] });
  readonly agencyError = signal<string | null>(null);

  /** RN-05.1.2 y RN-05.1.5: el servidor la guarda y la aplica en todas las pantallas. */
  onAgencyChange(value: string): void {
    const agencyId = Number(value);
    if (!agencyId) {
      return;
    }
    this.agencyError.set(null);
    this.auth.chooseWorkingAgency(agencyId).subscribe({
      error: () => this.agencyError.set(this.data()?.agencies.errorMessage ?? null),
    });
  }
}
