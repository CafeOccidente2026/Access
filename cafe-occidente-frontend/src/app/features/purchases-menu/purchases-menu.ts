import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';

import { ContentService } from '../../core/services/content.service';
import { AccessWindowComponent, MenuButtonGridComponent } from '../../shared/ui';
import { PurchasesMenuContent } from './purchases-menu.model';

/** Panel "COMPRAS": punto de entrada a los distintos tipos de compra. */
@Component({
  selector: 'app-purchases-menu',
  standalone: true,
  imports: [CommonModule, AccessWindowComponent, MenuButtonGridComponent],
  templateUrl: './purchases-menu.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PurchasesMenuComponent {
  private readonly content = inject(ContentService);

  readonly data = toSignal(this.content.loadJson<PurchasesMenuContent>('purchases-menu'));
}
