import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';

import { ContentService } from '../../core/services/content.service';
import { AccessWindowComponent, MenuButtonGridComponent } from '../../shared/ui';
import { FuturePurchasesMenuContent } from './future-purchases-menu.model';

/** Panel "MENUS COMPRAS A FUTURO". */
@Component({
  selector: 'app-future-purchases-menu',
  standalone: true,
  imports: [CommonModule, AccessWindowComponent, MenuButtonGridComponent],
  templateUrl: './future-purchases-menu.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FuturePurchasesMenuComponent {
  private readonly content = inject(ContentService);

  readonly data = toSignal(
    this.content.loadJson<FuturePurchasesMenuContent>('future-purchases-menu'),
  );
}
