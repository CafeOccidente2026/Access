import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';

import { RemissionResponse } from '../../core/models/inventory.model';
import { AuthService } from '../../core/services/auth.service';
import { InventoryService } from '../../core/services/inventory.service';
import { AccessWindowComponent } from '../../shared/ui';
import { formatDisplayNumber } from '../../shared/utils/number-format';

/**
 * "Genera Remesa" / "Genera Remesa Otros" (Form_Genera Plano.bas / Form_Genera Plano CVRN.bas): el
 * VBA exportaba a Excel en D:\ y enviaba a Almacafe/SAP vía un DLL local - sin equivalente en la
 * web. Acá se reemplaza por: elegir remisiones sin exportar, descargar un CSV (se abre en Excel) y
 * marcarlas como exportadas. Sin distinción de datos entre "Remesa" y "Remesa Otros" en el modelo
 * migrado (Remission no separa por categoría), así que ambos botones del menú usan esta misma pantalla.
 */
@Component({
  selector: 'app-remesa-export',
  standalone: true,
  imports: [CommonModule, AccessWindowComponent],
  templateUrl: './remesa-export.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RemesaExportComponent {
  private readonly authService = inject(AuthService);
  private readonly inventoryService = inject(InventoryService);

  readonly remissions = signal<RemissionResponse[]>([]);
  readonly selectedIds = signal<Set<number>>(new Set());
  readonly exportedMessage = signal<string | null>(null);

  readonly allSelected = computed(
    () => this.remissions().length > 0 && this.selectedIds().size === this.remissions().length,
  );

  constructor() {
    this.reload();
  }

  private reload(): void {
    const agencyId = this.authService.agencyId();
    if (agencyId) {
      this.inventoryService.listPendingExportRemissions(agencyId).subscribe((list) => {
        this.remissions.set(list);
        this.selectedIds.set(new Set());
      });
    }
  }

  kg(value: number): string {
    return formatDisplayNumber(value, 'count');
  }

  isSelected(id: number): boolean {
    return this.selectedIds().has(id);
  }

  toggle(id: number): void {
    const next = new Set(this.selectedIds());
    if (next.has(id)) {
      next.delete(id);
    } else {
      next.add(id);
    }
    this.selectedIds.set(next);
  }

  toggleAll(): void {
    this.selectedIds.set(this.allSelected() ? new Set() : new Set(this.remissions().map((r) => r.id)));
  }

  /** Descarga CSV (se abre en Excel) y marca las remisiones elegidas como exportadas. */
  generateRemesa(): void {
    const ids = [...this.selectedIds()];
    if (ids.length === 0) {
      return;
    }
    const selected = this.remissions().filter((r) => ids.includes(r.id));
    this.downloadCsv(selected);
    this.inventoryService.markRemissionsExported(ids).subscribe(() => {
      this.exportedMessage.set(`${ids.length} remisión(es) marcadas como exportadas.`);
      this.reload();
    });
  }

  private downloadCsv(remissions: RemissionResponse[]): void {
    const header = [
      'Remision',
      'Fecha',
      'Destino',
      'Conductor',
      'Cedula Conductor',
      'Transportadora',
      'Placa',
      'Movimiento',
      'Cantidad Kg',
      'Vr Unitario',
      'Vr Salida',
    ];
    const rows = remissions.flatMap((r) =>
      r.lines.map((line) => [
        r.remissionNumber,
        r.remissionDate,
        r.destination ?? '',
        r.conductorName ?? '',
        r.conductorIdNumber ?? '',
        r.transportCompany ?? '',
        r.vehiclePlate ?? '',
        line.inventoryMovementId,
        this.kg(line.quantity),
        this.kg(line.unitValue),
        this.kg(line.outputValue),
      ]),
    );
    const csv = [header, ...rows].map((row) => row.map((cell) => `"${cell}"`).join(';')).join('\r\n');
    const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `Remesa_${new Date().toISOString().slice(0, 10)}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  }
}
