import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';

import { InventoryMovementResponse } from '../../core/models/inventory.model';
import { AuthService } from '../../core/services/auth.service';
import { InventoryService } from '../../core/services/inventory.service';
import { AccessWindowComponent } from '../../shared/ui';
import { formatDisplayNumber } from '../../shared/utils/number-format';

/** "Reporte Inventario": de solo lectura - las filas se crean solas desde cada compra. */
@Component({
  selector: 'app-inventory-consulta',
  standalone: true,
  imports: [CommonModule, AccessWindowComponent],
  templateUrl: './inventory-consulta.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InventoryConsultaComponent {
  private readonly authService = inject(AuthService);
  private readonly inventoryService = inject(InventoryService);

  readonly movements = signal<InventoryMovementResponse[]>([]);
  readonly onlyWithBalance = signal(false);
  readonly codeFilter = signal('');

  readonly visibleMovements = computed(() => {
    let list = this.onlyWithBalance() ? this.movements().filter((m) => m.remainingKg > 0) : this.movements();
    const code = this.codeFilter().trim().toLowerCase();
    if (code) {
      list = list.filter((m) => m.productCode.toLowerCase().includes(code));
    }
    return list;
  });

  constructor() {
    const agencyId = this.authService.agencyId();
    if (agencyId) {
      this.inventoryService.listMovements(agencyId).subscribe((list) => this.movements.set(list));
    }
  }

  kg(value: number): string {
    return formatDisplayNumber(value, 'count');
  }

  money(value: number): string {
    return formatDisplayNumber(value, 'currency');
  }
}
