import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';

import { MenuOption } from '../../core/models';
import { ContentService } from '../../core/services/content.service';
import { AccessWindowComponent, MenuButtonGridComponent } from '../../shared/ui';

/** Columnas de botones en el orden de Access (Top/Left de cada CommandButton) y "Volver" abajo. */
export interface SuppliesMenuContent {
  readonly windowTitle: string;
  readonly columns: MenuOption[][];
  readonly back: MenuOption;
}

/** "MENUS SUMINISTROS" y "MENU CAJA MENOR": mismo panel, el JSON lo elige la ruta (data.content). */
@Component({
  selector: 'app-supplies-menu',
  standalone: true,
  imports: [CommonModule, AccessWindowComponent, MenuButtonGridComponent],
  templateUrl: './supplies-menu.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SuppliesMenuComponent {
  readonly data = toSignal(
    inject(ContentService).loadJson<SuppliesMenuContent>(inject(ActivatedRoute).snapshot.data['content']),
  );
}
