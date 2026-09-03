import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, Input, inject } from '@angular/core';
import { AppButtonComponent } from '../app-button/app-button';
import { MenuOption } from '../../../core/models';
import { NavigationService } from '../../../core/services/navigation.service';

/** Lista/rejilla de botones de navegacion, usada en todas las pantallas de menu. */
@Component({
  selector: 'app-menu-button-grid',
  standalone: true,
  imports: [CommonModule, AppButtonComponent],
  templateUrl: './menu-button-grid.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MenuButtonGridComponent {
  @Input() options: MenuOption[] = [];
  @Input() columns: 1 | 2 | 3 = 1;

  private readonly navigation = inject(NavigationService);

  onSelect(option: MenuOption): void {
    this.navigation.goTo(option.route);
  }
}
