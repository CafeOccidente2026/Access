import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, linkedSignal, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';

import { InventoryMovementResponse, RemissionResponse } from '../../core/models/inventory.model';
import { Grower, GrowerCreateRequest } from '../../core/models/grower.model';
import { ContentService } from '../../core/services/content.service';
import { GrowerService } from '../../core/services/grower.service';
import { InventoryService } from '../../core/services/inventory.service';
import { AccessWindowComponent, FormFlowDirective } from '../../shared/ui';
import { formatDate, formatKilos, formatMoney } from '../../shared/utils/number-format';
import { saveOnce } from '../../shared/utils/save-once';
import { serverError } from '../../shared/utils/server-error';
import { loadLogoDataUrl } from '../purchase-forms/dry-coffee/dry-coffee-invoice';
import { buildRemissionDocDefinition } from './remission-invoice';
import { printPdf } from '../../shared/utils/pdf-preview';
import { localToday } from '../../shared/utils/local-date';
import { ConfirmDialogComponent } from '../../shared/ui/confirm-dialog/confirm-dialog';
import { RemittanceFilesComponent } from '../remittance/remittance-files-panel';

/** Textos de la pantalla (public/assets/data/remission-form.json). */
export interface RemissionFormContent {
  readonly windowTitle: string;
  readonly dateLabel: string;
  readonly destinationLabel: string;
  readonly conductorLabel: string;
  readonly fundLabel: string;
  readonly funds: string[];
  readonly specialLabel: string;
  readonly chooseSpecial: string;
  readonly pickSpecial: string;
  readonly conductor: Record<string, string>;
  readonly columns: Record<string, string>;
  readonly pickLabel: string;
  readonly empty: string;
  readonly totalsLabel: string;
  readonly packagingQuantityLabel: string;
  readonly packagingTypeLabel: string;
  readonly packagingTypes: string[];
  readonly saveLabel: string;
  readonly result: Record<string, string>;
  readonly messages: { readonly saveError: string; readonly pdfError: string };
}

/**
 * "Registrar Salidas" (Form_EXITS.bas). Una casilla por factura con saldo del fondo y tipo elegidos que no este ya en
 * una remesa. Respuestas del area (23-nov): la factura sale completa (sin salidas parciales), con sus sacos y kilos
 * brutos; los empaques se registran aqui (sugeridos = suma de sacos, editables) y al guardar se genera la remesa con
 * el mismo numero y sus 4 archivos.
 */
@Component({
  selector: 'app-remission-form',
  standalone: true,
  imports: [ConfirmDialogComponent, CommonModule, AccessWindowComponent, FormFlowDirective, RemittanceFilesComponent],
  templateUrl: './remission-form.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RemissionFormComponent {
  private readonly growerService = inject(GrowerService);
  private readonly inventoryService = inject(InventoryService);

  readonly content = toSignal(inject(ContentService).loadJson<RemissionFormContent>('remission-form'));

  readonly movements = signal<InventoryMovementResponse[]>([]);
  readonly fund = signal('RP');
  /** Demo del 23-nov: el Tipo de cafe (Especial) que se va a despachar; vacio = todavia no se eligio. */
  readonly special = signal('');
  /** Las facturas con saldo del fondo elegido que no estan en una remesa: una remision es de un solo fondo. */
  private readonly withBalance = computed(() =>
    this.movements().filter((m) => m.remainingKg > 0 && !m.remitted && m.fundCode === this.fund()));
  /** Los tipos de cafe con saldo en el fondo, para elegir. */
  readonly specials = computed(() => [...new Set(this.withBalance().map((m) => m.specialType))].sort((a, b) => a.localeCompare(b)));
  /** Solo las facturas del tipo elegido, por fecha y factura; sin tipo no hay lista. */
  readonly rows = computed(() => this.withBalance()
    .filter((m) => m.specialType === this.special())
    .sort((a, b) => a.purchaseDate.localeCompare(b.purchaseDate) || a.invoiceNumber - b.invoiceNumber));
  readonly picks = signal<ReadonlySet<number>>(new Set());
  readonly totals = computed(() => this.movements().filter((m) => this.picks().has(m.id)).reduce(
    (t, m) => ({ quantity: t.quantity + m.remainingKg, sacos: t.sacos + m.sacos, grossKg: t.grossKg + m.grossKg }),
    { quantity: 0, sacos: 0, grossKg: 0 }));
  /** Empaques de la remision: la suma de sacos de lo marcado, que se puede cambiar. */
  readonly packagingQuantity = linkedSignal(() => String(this.totals().sacos));
  readonly packagingType = signal('');

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
    this.special.set('');
    this.picks.set(new Set());
  }

  /** Cambiar el tipo de cafe vuelve a armar la lista y limpia lo marcado. */
  setSpecial(special: string): void {
    this.special.set(special);
    this.picks.set(new Set());
  }

  isPicked(movement: InventoryMovementResponse): boolean {
    return this.picks().has(movement.id);
  }

  toggle(movement: InventoryMovementResponse): void {
    const next = new Set(this.picks());
    if (!next.delete(movement.id)) {
      next.add(movement.id);
    }
    this.picks.set(next);
  }

  kg(value: number): string {
    return formatKilos(value);
  }

  money(value: number): string {
    return formatMoney(value);
  }

  date(value: string): string {
    return formatDate(value);
  }

  savedText(remission: RemissionResponse): string {
    return (this.content()?.result['saved'] ?? '').replace('{number}', remission.displayNumber);
  }

  print(): void {
    const remission = this.result();
    if (!remission) {
      return;
    }
    printPdf(
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
    return !this.result() && this.picks().size > 0 && Number(this.packagingQuantity()) > 0 && this.packagingType() !== '';
  }

  save(): void {
    if (!this.canSave()) {
      return;
    }
    this.errorMessage.set(null);
    saveOnce(this.saving, () => this.inventoryService.createRemission({
      remissionDate: this.remissionDate(),
      destination: this.destination() || null,
      conductorIdNumber: this.conductorIdNumber() || null,
      lines: [...this.picks()].map((inventoryMovementId) => ({ inventoryMovementId })),
      packagingQuantity: Number(this.packagingQuantity()),
      packagingType: this.packagingType(),
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
    this.picks.set(new Set());
    this.packagingType.set('');
    this.result.set(null);
    this.errorMessage.set(null);
    this.loadMovements();
  }
}
