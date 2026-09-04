import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';

import { ContentService } from '../../core/services/content.service';
import { AccessWindowComponent, MenuButtonGridComponent } from '../../shared/ui';
import { InventoryMenuContent } from './inventory-menu.model';

/** Panel "INVENTARIOS". */
@Component({
  selector: 'app-inventory-menu',
  standalone: true,
  imports: [CommonModule, AccessWindowComponent, MenuButtonGridComponent],
  templateUrl: './inventory-menu.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InventoryMenuComponent {
  private readonly content = inject(ContentService);

  readonly data = toSignal(this.content.loadJson<InventoryMenuContent>('inventory-menu'));
}
