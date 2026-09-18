import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, ElementRef, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';

import { Fund } from '../../core/models/dry-coffee-purchase.model';
import { AnnouncementService } from '../../core/services/announcement.service';
import { ContentService } from '../../core/services/content.service';
import { AccessWindowComponent, AppButtonComponent } from '../../shared/ui';
import { AnnouncementUpdateContent } from './announcement-update.model';

type NumericField = 'basePriceLoad' | 'specialSurcharge' | 'defectiveUnitPrice';

/** Orden de captura (paso 1a) y de avance de foco al presionar Enter (paso 1b/1d). */
const FOCUS_ORDER: readonly string[] = ['basePriceLoad', 'specialSurcharge', 'defectiveUnitPrice', 'specialType'];

/** Tiempo que se muestra el mensaje de confirmacion antes de cerrarse solo. */
const SUCCESS_MESSAGE_DURATION_MS = 2000;

/** Separador de miles con punto, estilo colombiano (ej. "2500000" -> "2.500.000"). */
const formatThousands = (digits: string): string => digits.replace(/\B(?=(\d{3})+(?!\d))/g, '.');

/** Pantalla ADMIN: "Actualizar Anuncio con Factor" (ANUNCIOS CORRF) - siempre crea un anuncio nuevo. */
@Component({
  selector: 'app-announcement-update',
  standalone: true,
  imports: [CommonModule, FormsModule, AccessWindowComponent, AppButtonComponent],
  templateUrl: './announcement-update.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AnnouncementUpdateComponent {
  private readonly content = inject(ContentService);
  private readonly announcementService = inject(AnnouncementService);
  private readonly elementRef = inject(ElementRef);

  readonly page = toSignal(this.content.loadJson<AnnouncementUpdateContent>('announcement-update'));
  readonly funds = signal<Fund[]>([]);
  readonly message = signal<string | null>(null);
  readonly error = signal<string | null>(null);

  /** Valores formateados con puntos de miles, tal como se muestran en el input. */
  numeric: Record<NumericField, string> = { basePriceLoad: '', specialSurcharge: '', defectiveUnitPrice: '' };
  specialType = '';
  fundId: number | null = null;
  private successMessageTimeout?: ReturnType<typeof setTimeout>;

  constructor() {
    this.announcementService.funds().subscribe((list) => this.funds.set(list));
    // Paso 1c: foco en el primer campo apenas la pantalla termina de cargar su contenido.
    effect(() => {
      if (this.page()) {
        this.focusField(FOCUS_ORDER[0]);
      }
    });
  }

  /** Solo digitos, nunca letras ni signo negativo; formatea en vivo con puntos de miles. */
  onNumericInput(field: NumericField, value: string): void {
    const digits = value.replace(/\D/g, '');
    this.numeric[field] = formatThousands(digits);
  }

  /** Enter avanza al siguiente campo; en el ultimo (Especial) dispara la actualizacion. */
  onEnter(field: string): void {
    const idx = FOCUS_ORDER.indexOf(field);
    if (idx === -1) {
      return;
    }
    if (idx === FOCUS_ORDER.length - 1) {
      this.update();
      return;
    }
    this.focusField(FOCUS_ORDER[idx + 1]);
  }

  private focusField(key: string): void {
    setTimeout(() => {
      const el = this.elementRef.nativeElement.querySelector(`[data-field-key="${key}"]`) as HTMLElement | null;
      el?.focus();
      el?.classList.add('field-flash');
      setTimeout(() => el?.classList.remove('field-flash'), 1000);
    });
  }

  private numberValue(field: NumericField): number | null {
    const digits = this.numeric[field].replace(/\./g, '');
    return digits === '' ? null : Number(digits);
  }

  update(): void {
    this.message.set(null);
    this.error.set(null);
    clearTimeout(this.successMessageTimeout);
    const basePriceLoad = this.numberValue('basePriceLoad');
    const specialSurcharge = this.numberValue('specialSurcharge');
    const defectiveUnitPrice = this.numberValue('defectiveUnitPrice');
    if (
      basePriceLoad == null ||
      specialSurcharge == null ||
      defectiveUnitPrice == null ||
      !this.specialType ||
      this.fundId == null
    ) {
      return;
    }
    this.announcementService
      .create({
        basePriceLoad,
        defectiveUnitPrice,
        specialSurcharge,
        specialType: this.specialType,
        fundId: this.fundId,
      })
      .subscribe({
        next: (announcement) => {
          this.message.set(`${this.page()?.successMessage ?? ''} ${announcement.announcementNumber}`);
          this.successMessageTimeout = setTimeout(() => this.message.set(null), SUCCESS_MESSAGE_DURATION_MS);
        },
        error: () => this.error.set(this.page()?.errorMessage ?? null),
      });
  }
}
