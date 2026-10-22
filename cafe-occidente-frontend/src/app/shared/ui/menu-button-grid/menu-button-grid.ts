import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, Input, computed, inject, input } from '@angular/core';
import { AppButtonComponent } from '../app-button/app-button';
import { MenuOption } from '../../../core/models';
import { AuthService } from '../../../core/services/auth.service';
import { NavigationService } from '../../../core/services/navigation.service';

/** Lista/rejilla de botones de navegacion, usada en todas las pantallas de menu. Solo muestra las
 *  opciones del rol de la sesion (RF-01, RN-05.1). */
@Component({
  selector: 'app-menu-button-grid',
  standalone: true,
  imports: [CommonModule, AppButtonComponent],
  templateUrl: './menu-button-grid.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MenuButtonGridComponent {
  readonly options = input<MenuOption[]>([]);
  @Input() columns: 1 | 2 | 3 = 1;

  private readonly navigation = inject(NavigationService);
  private readonly auth = inject(AuthService);

  readonly visibleOptions = computed(() => this.options().filter((option) => this.auth.hasRole(option.roles)));

  onSelect(option: MenuOption): void {
    this.navigation.goTo(option.route);
  }
}
