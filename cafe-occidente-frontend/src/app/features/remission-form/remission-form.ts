import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';

import { InventoryMovementResponse, RemissionResponse } from '../../core/models/inventory.model';
import { Grower, GrowerCreateRequest } from '../../core/models/grower.model';
import { ContentService } from '../../core/services/content.service';
import { GrowerService } from '../../core/services/grower.service';
import { InventoryService } from '../../core/services/inventory.service';
import { AccessWindowComponent, FormFlowDirective } from '../../shared/ui';
import { formatDisplayNumber, parseDisplayNumber } from '../../shared/utils/number-format';
import { saveOnce } from '../../shared/utils/save-once';
import { serverError } from '../../shared/utils/server-error';
import { loadLogoDataUrl } from '../purchase-forms/dry-coffee/dry-coffee-invoice';
import { buildRemissionDocDefinition } from './remission-invoice';
import { openPdf } from '../../shared/utils/pdf-preview';
import { localToday } from '../../shared/utils/local-date';

/** Textos de la pantalla (public/assets/data/remission-form.json). */
export interface RemissionFormContent {
  readonly windowTitle: string;
  readonly dateLabel: string;
  readonly destinationLabel: string;
  readonly conductorLabel: string;
  readonly fundLabel: string;
  readonly funds: string[];
  readonly conductor: Record<string, string>;
  readonly columns: Record<string, string>;
  readonly pickLabel: string;
  readonly empty: string;
  readonly totalsLabel: string;
  readonly saveLabel: string;
  readonly result: Record<string, string>;
  readonly messages: { readonly saveError: string; readonly pdfError: string };
}

/** Lo que sale de una entrada marcada: por defecto todo su saldo, con los sacos y kilos de la compra. */
type PickField = 'quantity' | 'sacos' | 'grossKg';
type Pick = Record<PickField, string>;

/**
 * "Registrar Salidas" (Form_EXITS.bas). C7 (prueba del 11-nov, punto 7): como Genera Remesa, una casilla por
 * entrada de inventario con saldo del fondo elegido. Al marcarla sale todo su saldo, con los sacos y kilos
 * brutos de la compra; los tres se pueden editar para una salida parcial. El servidor valida que no se pase
 * del saldo y muestra su motivo.
 */
@Component({
  selector: 'app-remission-form',
  standalone: true,
  imports: [CommonModule, AccessWindowComponent, FormFlowDirective],
  templateUrl: './remission-form.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RemissionFormComponent {
  private readonly growerService = inject(GrowerService);
  private readonly inventoryService = inject(InventoryService);

  readonly content = toSignal(inject(ContentService).loadJson<RemissionFormContent>('remission-form'));

  readonly movements = signal<InventoryMovementResponse[]>([]);
  readonly fund = signal('RP');
  /** Las entradas con saldo del fondo elegido: una remision es de un solo fondo (su consecutivo es por fondo). */
  readonly rows = computed(() => this.movements().filter((m) => m.remainingKg > 0 && m.fundCode === this.fund()));
  readonly picks = signal<ReadonlyMap<number, Pick>>(new Map());
  readonly totals = computed(() => {
    const sum = (field: PickField) => [...this.picks().values()].reduce((total, p) => total + parseDisplayNumber(p[field]), 0);
    return { quantity: sum('quantity'), sacos: sum('sacos'), grossKg: sum('grossKg') };
  });

  readonly remissionDate = signal(localToday());
  readonly destination = signal('');
  readonly conductorIdNumber = signal('');
  readonly conductor = signal<Grower | null>(null);
  readonly conductorNotFound = signal(false);

  readonly showNewConductorForm = signal(false);
  readonly newConductorFirstName = signal('');
  readonly newConductorLastName = signal('');
  readonly newConductorTransportCompany = signal('');
  readonly newConductorVehiclePlate = signal('');
  readonly newConductorError = signal<string | null>(null);

  readonly saving = signal(false);
  readonly result = signal<RemissionResponse | null>(null);
  readonly errorMessage = signal<string | null>(null);

  private readonly logoDataUrlPromise = loadLogoDataUrl('assets/images/cafe-occidente-logo.png');

  constructor() {
    this.loadMovements();
  }

  private loadMovements(): void {
    this.inventoryService.listMovements().subscribe((list) => this.movements.set(list));
  }

  setFund(code: string): void {
    this.fund.set(code);
    this.picks.set(new Map());
  }

  isPicked(movement: InventoryMovementResponse): boolean {
    return this.picks().has(movement.id);
  }

  toggle(movement: InventoryMovementResponse): void {
    const next = new Map(this.picks());
    if (next.has(movement.id)) {
      next.delete(movement.id);
    } else {
      // Sin separador de miles: parseDisplayNumber lee el punto como decimal.
      next.set(movement.id, { quantity: String(movement.remainingKg), sacos: String(movement.sacos), grossKg: String(movement.grossKg) });
    }
    this.picks.set(next);
  }

  pickValue(movement: InventoryMovementResponse, field: PickField): string {
    return this.picks().get(movement.id)?.[field] ?? '';
  }

  setPick(movement: InventoryMovementResponse, field: PickField, value: string): void {
    const current = this.picks().get(movement.id);
    if (!current) {
      return;
    }
    this.picks.set(new Map(this.picks()).set(movement.id, { ...current, [field]: value }));
  }

  kg(value: number): string {
    return formatDisplayNumber(value, 'count');
  }

  date(value: string): string {
    return value.split('-').reverse().join('/');
  }

  savedText(remission: RemissionResponse): string {
    return (this.content()?.result['saved'] ?? '').replace('{number}', remission.displayNumber);
  }

  print(): void {
    const remission = this.result();
    if (!remission) {
      return;
    }
    openPdf(
      this.logoDataUrlPromise.then((logo) => buildRemissionDocDefinition(remission, logo)),
      () => this.errorMessage.set(this.content()?.messages.pdfError ?? null),
    );
  }

  onConductorBlur(): void {
    const idNumber = this.conductorIdNumber().trim();
    this.conductor.set(null);
    this.conductorNotFound.set(false);
    this.showNewConductorForm.set(false);
    if (!idNumber) {
      return;
    }
    this.growerService.findByIdNumber(idNumber).subscribe({
      next: (grower) => this.conductor.set(grower),
      error: () => this.conductorNotFound.set(true),
    });
  }

  openNewConductorForm(): void {
    this.newConductorFirstName.set('');
    this.newConductorLastName.set('');
    this.newConductorTransportCompany.set('');
    this.newConductorVehiclePlate.set('');
    this.newConductorError.set(null);
    this.showNewConductorForm.set(true);
  }

  /** "Ingresar Conductores" (Form_Conductores.bas), a la mano desde acá: la cédula ya está capturada
   *  arriba, solo falta el resto de datos para dar de alta al conductor y seguir con la remisión. */
  saveNewConductor(): void {
    const idNumber = this.conductorIdNumber().trim();
    if (!idNumber || !this.newConductorFirstName().trim() || !this.newConductorLastName().trim()) {
      return;
    }
    this.newConductorError.set(null);
    const request: GrowerCreateRequest = {
      idNumber,
      firstName: this.newConductorFirstName().trim(),
      secondName: null,
      lastName: this.newConductorLastName().trim(),
      secondLastName: null,
      transportCompany: this.newConductorTransportCompany().trim() || null,
      vehiclePlate: this.newConductorVehiclePlate().trim() || null,
    };
    this.growerService.createConductor(request).subscribe({
      next: (grower) => {
        this.conductor.set(grower);
        this.conductorNotFound.set(false);
        this.showNewConductorForm.set(false);
      },
      error: (err) => this.newConductorError.set(serverError(err, this.content()?.conductor['saveError'])),
    });
  }

  canSave(): boolean {
    return !this.result() && this.picks().size > 0;
  }

  save(): void {
    if (!this.canSave()) {
      return;
    }
    this.errorMessage.set(null);
    const lines = [...this.picks()].map(([inventoryMovementId, p]) => ({
      inventoryMovementId,
      quantity: parseDisplayNumber(p.quantity),
      sacos: parseDisplayNumber(p.sacos),
      grossKg: parseDisplayNumber(p.grossKg),
    }));
    saveOnce(this.saving, () => this.inventoryService.createRemission({
      remissionDate: this.remissionDate(),
      destination: this.destination() || null,
      conductorIdNumber: this.conductorIdNumber() || null,
      lines,
    }), {
      next: (res) => this.result.set(res),
      error: (err) => this.errorMessage.set(serverError(err, this.content()?.messages.saveError)),
    });
  }

  reset(): void {
    this.remissionDate.set(localToday());
    this.destination.set('');
    this.conductorIdNumber.set('');
    this.conductor.set(null);
    this.conductorNotFound.set(false);
    this.picks.set(new Map());
    this.result.set(null);
    this.errorMessage.set(null);
    this.loadMovements();
  }
}
