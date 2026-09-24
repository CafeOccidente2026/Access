import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';

import { AuthService } from '../../core/services/auth.service';
import { MenuOption } from '../../core/models';
import { ContentService } from '../../core/services/content.service';
import { AccessWindowComponent, MenuButtonGridComponent } from '../../shared/ui';
import { MainMenuContent } from './main-menu.model';

/** Panel "MENU PRINCIPAL": puerta de entrada a los modulos del aplicativo. */
@Component({
  selector: 'app-main-menu',
  standalone: true,
  imports: [CommonModule, AccessWindowComponent, MenuButtonGridComponent],
  templateUrl: './main-menu.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MainMenuComponent {
  private readonly content = inject(ContentService);
  private readonly auth = inject(AuthService);

  readonly data = toSignal(this.content.loadJson<MainMenuContent>('main-menu'));

  /** El rol USER no ve las opciones exclusivas de ADMIN (Usuarios, Actualizar Anuncios). */
  readonly options = computed<MenuOption[]>(() => {
    const isAdmin = this.auth.isAdmin();
    return (this.data()?.options ?? []).filter((option) => isAdmin || !option.adminOnly);
  });
}
