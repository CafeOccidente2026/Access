import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, HostListener, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';

import {
  Agency,
  Announcement,
  DryCoffeePurchaseRequest,
  DryCoffeePurchaseResponse,
  Fund,
} from '../../../core/models/dry-coffee-purchase.model';
import { ContentService } from '../../../core/services/content.service';
import { DryCoffeePurchaseService } from '../../../core/services/dry-coffee-purchase.service';
import { AccessWindowComponent, AppButtonComponent, ConfirmDialogComponent } from '../../../shared/ui';
import { DryCoffeeFormContent } from './dry-coffee-form.model';

interface DryCoffeeFormModel {
  agencyId: number | null;
  fundId: number | null;
  specialType: string;
  idNumber: string;
  firstName: string;
  lastName: string;
  growerType: string;
  address: string;
  cellphone: string;
  bagsCount: number | null;
  grossKg: number | null;
  tareKg: number | null;
  totalStoredWeight: number | null;
  defectiveStoredWeight: number | null;
  healthyStoredWeight: number | null;
  healthyUnitPrice: number | null;
  defectiveUnitPrice: number | null;
  bonus: number | null;
  penalty: number | null;
  costs: number | null;
  withholdingExempt: boolean;
  freightDiscount: number | null;
  otherDiscounts: number | null;
  paymentMethod: string;
  checkNumber: string;
}

const BLANK: DryCoffeeFormModel = {
  agencyId: null,
  fundId: null,
  specialType: '',
  idNumber: '',
  firstName: '',
  lastName: '',
  growerType: '',
  address: '',
  cellphone: '',
  bagsCount: null,
  grossKg: null,
  tareKg: null,
  totalStoredWeight: null,
  defectiveStoredWeight: null,
  healthyStoredWeight: null,
  healthyUnitPrice: null,
  defectiveUnitPrice: null,
  bonus: 0,
  penalty: 0,
  costs: null,
  withholdingExempt: false,
  freightDiscount: 0,
  otherDiscounts: 0,
  paymentMethod: '',
  checkNumber: '',
};

/** Campos manuales en el orden de captura del formulario Access (bloqueo secuencial). */
const FIELD_ORDER: (keyof DryCoffeeFormModel)[] = [
  'agencyId', 'fundId', 'specialType', 'idNumber', 'firstName', 'lastName', 'growerType',
  'address', 'cellphone', 'bagsCount', 'grossKg', 'tareKg', 'totalStoredWeight',
  'defectiveStoredWeight', 'healthyStoredWeight', 'healthyUnitPrice', 'defectiveUnitPrice',
  'bonus', 'penalty', 'costs', 'withholdingExempt', 'freightDiscount', 'otherDiscounts',
  'paymentMethod', 'checkNumber',
];

const REQUIRED: (keyof DryCoffeeFormModel)[] = [
  'agencyId', 'fundId', 'specialType', 'idNumber', 'firstName', 'lastName', 'growerType',
  'address', 'cellphone', 'bagsCount', 'grossKg', 'tareKg', 'totalStoredWeight',
  'defectiveStoredWeight', 'healthyStoredWeight', 'healthyUnitPrice', 'defectiveUnitPrice',
  'costs', 'paymentMethod',
];

/**
 * Formulario "Compras Café Seco": migración del formulario VBA de Access.
 * La cascada de cálculo (Vr_Kilo, Retefuente, Neto a Pagar, etc.) se ejecuta en el servidor al
 * imprimir; aquí solo se capturan los campos manuales y se muestran los resultados devueltos.
 */
@Component({
  selector: 'app-dry-coffee-form',
  standalone: true,
  imports: [CommonModule, FormsModule, AccessWindowComponent, AppButtonComponent, ConfirmDialogComponent],
  templateUrl: './dry-coffee-form.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DryCoffeeFormComponent {
  private readonly content = inject(ContentService);
  private readonly service = inject(DryCoffeePurchaseService);

  readonly page = toSignal(this.content.loadJson<DryCoffeeFormContent>('purchase-form-dry'));
  readonly agencies = signal<Agency[]>([]);
  readonly funds = signal<Fund[]>([]);
  readonly announcement = signal<Announcement | null>(null);
  readonly result = signal<DryCoffeePurchaseResponse | null>(null);
  readonly errorMessage = signal<string | null>(null);

  readonly today = new Date().toISOString().slice(0, 10);
  model: DryCoffeeFormModel = { ...BLANK };
  private locked = new Set<string>();
  pendingClose = false;
  private closeResolver: ((value: boolean) => void) | null = null;

  constructor() {
    this.service.agencies().subscribe((a) => this.agencies.set(a));
    this.service.funds().subscribe((f) => this.funds.set(f));
  }

  isLocked(key: keyof DryCoffeeFormModel): boolean {
    return this.locked.has(key);
  }

  lock(key: keyof DryCoffeeFormModel): void {
    const value = this.model[key];
    if (value === null || value === '') {
      return;
    }
    this.locked.add(key);
    if ((key === 'agencyId' || key === 'fundId') && this.model.agencyId && this.model.fundId) {
      this.loadAnnouncement();
    }
  }

  private loadAnnouncement(): void {
    this.errorMessage.set(null);
    this.service.latestAnnouncement(this.model.agencyId!, this.model.fundId!).subscribe({
      next: (a) => this.announcement.set(a),
      error: () => {
        this.announcement.set(null);
        this.errorMessage.set(this.page()?.errorMessage ?? null);
      },
    });
  }

  get canPrint(): boolean {
    if (this.result() || !this.announcement()) {
      return false;
    }
    const filled = REQUIRED.every((k) => this.model[k] !== null && this.model[k] !== '');
    const checkOk = this.model.paymentMethod !== 'CHEQUE' || this.model.checkNumber.trim() !== '';
    return filled && checkOk;
  }

  get isDirty(): boolean {
    return !this.result() && FIELD_ORDER.some((k) => this.model[k] !== BLANK[k]);
  }

  print(): void {
    if (!this.canPrint) {
      return;
    }
    this.errorMessage.set(null);
    const request: DryCoffeePurchaseRequest = {
      agencyId: this.model.agencyId!,
      fundId: this.model.fundId!,
      specialType: this.model.specialType,
      idNumber: this.model.idNumber,
      firstName: this.model.firstName,
      lastName: this.model.lastName,
      growerType: this.model.growerType,
      address: this.model.address,
      cellphone: this.model.cellphone,
      bagsCount: this.model.bagsCount!,
      grossKg: this.model.grossKg!,
      tareKg: this.model.tareKg!,
      totalStoredWeight: this.model.totalStoredWeight!,
      defectiveStoredWeight: this.model.defectiveStoredWeight!,
      healthyStoredWeight: this.model.healthyStoredWeight!,
      healthyUnitPrice: this.model.healthyUnitPrice!,
      defectiveUnitPrice: this.model.defectiveUnitPrice!,
      bonus: this.model.bonus ?? 0,
      penalty: this.model.penalty ?? 0,
      costs: this.model.costs!,
      withholdingExempt: this.model.withholdingExempt,
      freightDiscount: this.model.freightDiscount ?? 0,
      otherDiscounts: this.model.otherDiscounts ?? 0,
      paymentMethod: this.model.paymentMethod,
      checkNumber: this.model.paymentMethod === 'CHEQUE' ? this.model.checkNumber : null,
    };
    this.service.create(request).subscribe({
      next: (res) => this.result.set(res),
      // TODO: generación real del documento soporte / factura PDF queda pendiente (prompt futuro).
      error: () => this.errorMessage.set(this.page()?.errorMessage ?? null),
    });
  }

  /** Escape reinicia todo el formulario en blanco y editable (única forma de "volver atrás"). */
  @HostListener('document:keydown.escape')
  reset(): void {
    this.model = { ...BLANK };
    this.locked.clear();
    this.announcement.set(null);
    this.result.set(null);
    this.errorMessage.set(null);
  }

  /** Usado por el guard CanDeactivate: si hay datos sin imprimir, pide confirmación. */
  canDeactivate(): boolean | Promise<boolean> {
    if (!this.isDirty) {
      return true;
    }
    this.pendingClose = true;
    return new Promise<boolean>((resolve) => (this.closeResolver = resolve));
  }

  confirmClose(): void {
    this.pendingClose = false;
    this.closeResolver?.(true);
    this.closeResolver = null;
  }

  cancelClose(): void {
    this.pendingClose = false;
    this.closeResolver?.(false);
    this.closeResolver = null;
  }
}
