import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, ElementRef, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';

import { AnnouncementType, Fund } from '../../core/models/dry-coffee-purchase.model';
import { AnnouncementService } from '../../core/services/announcement.service';
import { ContentService } from '../../core/services/content.service';
import { AccessWindowComponent, AppButtonComponent, SUCCESS_MESSAGE_DURATION_MS, SuccessOverlayComponent } from '../../shared/ui';
import { formatThousands, validateWholeNumberField } from '../../shared/utils/number-format';
import { AnnouncementUpdateContent, TypeRow } from './announcement-update.model';

type Field = 'fundId' | 'basePriceLoad';
type RowField = 'basePriceLoad' | 'specialSurcharge' | 'defectiveUnitPrice';

/** RN-08.1: Fondo primero; Enter pasa al siguiente solo si el actual esta diligenciado. */
const FOCUS_ORDER: readonly Field[] = ['fundId', 'basePriceLoad'];

/** "123.456" -> 123456; vacio -> null. */
function amount(text: string): number | null {
  const digits = text.replace(/\D/g, '');
  return digits === '' ? null : Number(digits);
}

function shown(value: number | null): string {
  return value == null ? '' : formatThousands(String(Math.trunc(value)));
}

/**
 * "Actualizar Anuncio con Factor" (RF-08, HU-10 y HU-11): un solo precio del dia para todos los tipos de
 * cafe del fondo (RN-08.3), y el listado "Actualizar" para cambiar el precio, la bonificacion o el precio de
 * pasilla de un solo tipo (RN-08.5, RN-08.6). Cada guardado muestra "Anuncio actualizado" 3 s (RN-08.7).
 */
@Component({
  selector: 'app-announcement-update',
  standalone: true,
  imports: [CommonModule, FormsModule, AccessWindowComponent, AppButtonComponent, SuccessOverlayComponent],
  templateUrl: './announcement-update.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AnnouncementUpdateComponent {
  private readonly announcementService = inject(AnnouncementService);
  private readonly elementRef = inject(ElementRef);

  readonly page = toSignal(inject(ContentService).loadJson<AnnouncementUpdateContent>('announcement-update'));
  readonly funds = toSignal(this.announcementService.funds(), { initialValue: [] as Fund[] });
  readonly rows = signal<TypeRow[] | null>(null);
  readonly message = signal<string | null>(null);
  readonly error = signal<string | null>(null);

  fundId: number | null = null;
  basePriceLoad = '';
  priceError: string | null = null;
  private messageTimeout?: ReturnType<typeof setTimeout>;

  constructor() {
    effect(() => {
      if (this.page()) {
        this.focusField(FOCUS_ORDER[0]);
      }
    });
  }

  onFundChange(): void {
    this.rows.set(null);
  }

  onPriceInput(value: string): void {
    this.priceError = validateWholeNumberField(value);
    this.basePriceLoad = this.priceError ? value : formatThousands(value.replace(/\D/g, ''));
  }

  /** RN-08.1: con Enter pasa al siguiente campo si el actual esta diligenciado; en el ultimo, guarda. */
  onEnter(field: Field): void {
    if (!this.isFilled(field)) {
      this.focusField(field);
      return;
    }
    const idx = FOCUS_ORDER.indexOf(field);
    if (idx === FOCUS_ORDER.length - 1) {
      this.save();
      return;
    }
    this.focusField(FOCUS_ORDER[idx + 1]);
  }

  /** RN-08.1: no se salta con el mouse a un campo si uno anterior esta vacio. */
  onFocus(field: Field): void {
    const firstEmpty = FOCUS_ORDER.slice(0, FOCUS_ORDER.indexOf(field)).find((f) => !this.isFilled(f));
    if (firstEmpty) {
      this.focusField(firstEmpty);
    }
  }

  /** RN-08.3: el precio del dia para todos los tipos de cafe del fondo. */
  save(): void {
    const price = this.priceError ? null : amount(this.basePriceLoad);
    if (this.fundId == null || price == null) {
      this.focusField(this.fundId == null ? 'fundId' : 'basePriceLoad');
      return;
    }
    this.error.set(null);
    this.announcementService.publishAll({ fundId: this.fundId, basePriceLoad: price }).subscribe({
      next: () => {
        this.done();
        if (this.rows()) {
          this.openList();
        }
      },
      error: (err) => this.fail(err),
    });
  }

  /** RN-08.5: el listado de los tipos de cafe del fondo con su anuncio vigente. */
  openList(): void {
    if (this.fundId == null) {
      this.focusField('fundId');
      return;
    }
    this.announcementService.types(this.fundId).subscribe({
      next: (types) => this.rows.set(types.map((type) => this.toRow(type))),
      error: (err) => this.fail(err),
    });
  }

  onRowInput(row: TypeRow, field: RowField, value: string): void {
    row[field] = validateWholeNumberField(value) ? value : formatThousands(value.replace(/\D/g, ''));
  }

  /**
   * RN-08.6: guarda solo ese tipo, con el precio modificado o, si no lo cambio, con el precio del dia
   * digitado arriba (o el vigente del tipo, que resuelve el servidor, si arriba no hay precio).
   */
  saveRow(row: TypeRow): void {
    const surcharge = amount(row.specialSurcharge);
    const pasilla = amount(row.defectiveUnitPrice);
    if (this.fundId == null || surcharge == null || pasilla == null) {
      return;
    }
    const price = row.basePriceLoad === row.originalBasePriceLoad ? amount(this.basePriceLoad) : amount(row.basePriceLoad);
    this.error.set(null);
    this.announcementService
      .publishType({
        fundId: this.fundId,
        specialType: row.specialType,
        basePriceLoad: price,
        specialSurcharge: surcharge,
        defectiveUnitPrice: pasilla,
      })
      .subscribe({
        next: () => {
          this.done();
          this.openList();
        },
        error: (err) => this.fail(err),
      });
  }

  private isFilled(field: Field): boolean {
    return field === 'fundId' ? this.fundId != null : !this.priceError && amount(this.basePriceLoad) != null;
  }

  private toRow(type: AnnouncementType): TypeRow {
    const price = shown(type.basePriceLoad);
    return {
      specialType: type.specialType,
      basePriceLoad: price,
      specialSurcharge: shown(type.specialSurcharge ?? 0),
      defectiveUnitPrice: shown(type.defectiveUnitPrice ?? 0),
      originalBasePriceLoad: price,
    };
  }

  /** RN-08.7: "Anuncio actualizado" durante 3 segundos. */
  private done(): void {
    clearTimeout(this.messageTimeout);
    this.message.set(this.page()?.successMessage ?? null);
    this.messageTimeout = setTimeout(() => this.message.set(null), SUCCESS_MESSAGE_DURATION_MS);
  }

  private fail(err: unknown): void {
    const message = (err as { error?: { message?: string } })?.error?.message;
    this.error.set(message ?? this.page()?.errorMessage ?? null);
  }

  private focusField(key: Field): void {
    setTimeout(() => {
      const el = this.elementRef.nativeElement.querySelector(`[data-field-key="${key}"]`) as HTMLElement | null;
      el?.focus();
    });
  }
}
