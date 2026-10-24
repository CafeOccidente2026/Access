import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';

import { AuthService } from '../../core/services/auth.service';
import { ContentService } from '../../core/services/content.service';
import { NavigationService } from '../../core/services/navigation.service';
import { AccessWindowComponent, AppButtonComponent, MenuButtonGridComponent } from '../../shared/ui';
import { MainAccessContent } from './main-access.model';

/** Panel "Acceso Principal": bienvenida con el usuario de la sesion y navegacion a los modulos raiz.
 *  Usuarios, Acceso a Tablas, Menu Habilitacion, Permisos e Historial son solo del Administrador (RN-01.1);
 *  Acceso a Tablas e Historial tambien para quien los tenga concedidos (RF-02). */
@Component({
  selector: 'app-main-access',
  standalone: true,
  imports: [CommonModule, AccessWindowComponent, MenuButtonGridComponent, AppButtonComponent],
  templateUrl: './main-access.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MainAccessComponent {
  private readonly content = inject(ContentService);
  private readonly navigation = inject(NavigationService);
  readonly auth = inject(AuthService);

  readonly data = toSignal(this.content.loadJson<MainAccessContent>('main-access'));
  /** Al abrir Acceso Principal se releen los permisos (RF-02): Acceso a Tablas o Historial concedidos. */
  readonly session = toSignal(this.auth.refreshSession());

  onExit(): void {
    this.auth.logout();
    this.navigation.goTo('/login');
  }
}
