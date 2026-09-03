import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';

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

  readonly data = toSignal(this.content.loadJson<MainMenuContent>('main-menu'));
}
