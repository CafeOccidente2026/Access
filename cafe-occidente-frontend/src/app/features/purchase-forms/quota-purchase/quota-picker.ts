import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output, signal } from '@angular/core';

import { QuotaBalance } from '../../../core/models/announcement-quota.model';
import { AppButtonComponent } from '../../../shared/ui';
import { formatDisplayNumber, parseDisplayNumber } from '../../../shared/utils/number-format';

/** Textos de la ventana (JSON del formulario, "quotaPicker"). */
export interface QuotaPickerContent {
  readonly title: string;
  readonly columns: Record<string, string>;
  readonly deliveryLabel: string;
  readonly overBalance: string;
  readonly acceptLabel: string;
  readonly cancelLabel: string;
}

export interface QuotaPick {
  readonly row: QuotaBalance;
  readonly deliveryKg: number;
}

/** Columnas de RN-21.2.4, en ese orden. */
const COLUMNS: readonly string[] = [
  'announcement', 'agency', 'date', 'healthyUnitPrice', 'defectiveUnitPrice', 'bonus', 'costs', 'qualityIncrement',
  'basePriceLoad', 'specialType', 'assignedQuota', 'deliveredKg', 'balance',
];

/**
 * Ventana de Facturar Anuncios con Cupos (RF-21.2, Access "FUTURE ANOUNCIOUS"): los anuncios con cupo del especial,
 * solo lectura, y un único campo editable, "Esta Entrega". Enter con un valor que no supera el saldo la cierra
 * (CA-2); un valor mayor no se acepta (CA-3). El servidor vuelve a validar todo al guardar la compra.
 */
@Component({
  selector: 'app-quota-picker',
  standalone: true,
  imports: [CommonModule, AppButtonComponent],
  templateUrl: './quota-picker.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QuotaPickerComponent {
  @Input({ required: true }) content!: QuotaPickerContent;
  @Input({ required: true }) agency = '';
  @Input({ required: true }) set rows(value: readonly QuotaBalance[]) {
    this.list = value;
    this.selected.set(value[0] ?? null);
  }
  @Output() readonly picked = new EventEmitter<QuotaPick>();
  @Output() readonly cancelled = new EventEmitter<void>();

  readonly columns = COLUMNS;
  list: readonly QuotaBalance[] = [];
  readonly selected = signal<QuotaBalance | null>(null);
  readonly delivery = signal('');
  readonly error = signal<string | null>(null);

  cell(row: QuotaBalance, column: string): string {
    const a = row.announcement;
    const values: Record<string, string | number | undefined> = {
      announcement: a.announcementNumber,
      agency: this.agency,
      date: a.announcementDate,
      healthyUnitPrice: a.healthyUnitPrice,
      defectiveUnitPrice: a.defectiveUnitPrice,
      bonus: a.bonus,
      costs: a.costs,
      qualityIncrement: a.qualityIncrement ?? 0,
      basePriceLoad: a.basePriceLoad,
      specialType: a.specialType,
      assignedQuota: row.assignedQuota,
      deliveredKg: row.deliveredKg,
      balance: row.balance,
    };
    const value = values[column];
    return typeof value === 'number' ? formatDisplayNumber(value, 'currency') : (value ?? '');
  }

  select(row: QuotaBalance): void {
    this.selected.set(row);
    this.error.set(null);
  }

  accept(): void {
    const row = this.selected();
    const kg = parseDisplayNumber(this.delivery());
    if (!row || kg <= 0) {
      return;
    }
    if (kg > row.balance) {
      this.error.set(this.content.overBalance);
      this.delivery.set('');
      return;
    }
    this.picked.emit({ row, deliveryKg: kg });
  }
}
