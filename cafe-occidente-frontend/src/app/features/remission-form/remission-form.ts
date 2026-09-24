import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';

import { InventoryMovementResponse, RemissionLineRequest, RemissionResponse } from '../../core/models/inventory.model';
import { Grower } from '../../core/models/grower.model';
import { AuthService } from '../../core/services/auth.service';
import { GrowerService } from '../../core/services/grower.service';
import { InventoryService } from '../../core/services/inventory.service';
import { AccessWindowComponent } from '../../shared/ui';
import { formatDisplayNumber, parseDisplayNumber } from '../../shared/utils/number-format';

interface DraftLine {
  readonly inventoryMovementId: number;
  readonly label: string;
  readonly quantity: number;
}

/**
 * "Registrar Salidas" (Form_EXITS.bas): arma una Remisión con una o más entradas de inventario
 * origen + cantidad. El backend valida que cada cantidad no supere el saldo disponible de su
 * movimiento origen ("La salida no puede ser superior al saldo, vuelva a intentarlo") - esa
 * validación no se repite acá, se muestra el mensaje real si se dispara al guardar.
 */
@Component({
  selector: 'app-remission-form',
  standalone: true,
  imports: [CommonModule, AccessWindowComponent],
  templateUrl: './remission-form.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RemissionFormComponent {
  private readonly authService = inject(AuthService);
  private readonly growerService = inject(GrowerService);
  private readonly inventoryService = inject(InventoryService);

  readonly movements = signal<InventoryMovementResponse[]>([]);
  readonly availableMovements = computed(() => this.movements().filter((m) => m.remainingKg > 0));

  readonly remissionDate = signal(new Date().toISOString().slice(0, 10));
  readonly destination = signal('');
  readonly conductorIdNumber = signal('');
  readonly conductor = signal<Grower | null>(null);
  readonly conductorNotFound = signal(false);

  readonly selectedMovementId = signal<number | null>(null);
  readonly draftQuantity = signal('');
  readonly lines = signal<DraftLine[]>([]);

  readonly result = signal<RemissionResponse | null>(null);
  readonly errorMessage = signal<string | null>(null);

  constructor() {
    const agencyId = this.authService.agencyId();
    if (agencyId) {
      this.inventoryService.listMovements(agencyId).subscribe((list) => this.movements.set(list));
    }
  }

  kg(value: number): string {
    return formatDisplayNumber(value, 'count');
  }

  onConductorBlur(): void {
    const idNumber = this.conductorIdNumber().trim();
    this.conductor.set(null);
    this.conductorNotFound.set(false);
    if (!idNumber) {
      return;
    }
    this.growerService.findByIdNumber(idNumber).subscribe({
      next: (grower) => this.conductor.set(grower),
      error: () => this.conductorNotFound.set(true),
    });
  }

  addLine(): void {
    const movementId = this.selectedMovementId();
    const quantity = parseDisplayNumber(this.draftQuantity());
    if (!movementId || quantity <= 0) {
      return;
    }
    const movement = this.movements().find((m) => m.id === movementId);
    if (!movement) {
      return;
    }
    this.lines.update((list) => [
      ...list,
      { inventoryMovementId: movementId, label: `Factura ${movement.invoiceNumber} - ${movement.specialType}`, quantity },
    ]);
    this.selectedMovementId.set(null);
    this.draftQuantity.set('');
  }

  removeLine(index: number): void {
    this.lines.update((list) => list.filter((_, i) => i !== index));
  }

  canSave(): boolean {
    return !this.result() && this.lines().length > 0;
  }

  save(): void {
    const agencyId = this.authService.agencyId();
    if (!agencyId || !this.canSave()) {
      return;
    }
    this.errorMessage.set(null);
    const lineRequests: RemissionLineRequest[] = this.lines().map((l) => ({
      inventoryMovementId: l.inventoryMovementId,
      quantity: l.quantity,
    }));
    this.inventoryService
      .createRemission({
        agencyId,
        remissionDate: this.remissionDate(),
        destination: this.destination() || null,
        conductorIdNumber: this.conductorIdNumber() || null,
        lines: lineRequests,
      })
      .subscribe({
        next: (res) => this.result.set(res),
        error: (err) => {
          const message = (err as { error?: { message?: string } })?.error?.message;
          this.errorMessage.set(message ?? 'No se pudo registrar la remisión. Verifique los datos.');
        },
      });
  }

  reset(): void {
    this.remissionDate.set(new Date().toISOString().slice(0, 10));
    this.destination.set('');
    this.conductorIdNumber.set('');
    this.conductor.set(null);
    this.conductorNotFound.set(false);
    this.lines.set([]);
    this.result.set(null);
    this.errorMessage.set(null);
    const agencyId = this.authService.agencyId();
    if (agencyId) {
      this.inventoryService.listMovements(agencyId).subscribe((list) => this.movements.set(list));
    }
  }
}
