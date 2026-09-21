import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, ElementRef, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';

import { AnnouncementService } from '../../core/services/announcement.service';
import { ContentService } from '../../core/services/content.service';
import { AccessWindowComponent, AppButtonComponent } from '../../shared/ui';
import { formatThousands } from '../../shared/utils/number-format';
import { AnnouncementUpdateHuskContent } from './announcement-update-husk.model';

type NumericField = 'basePriceLoad' | 'pointPrice';

/** Orden de captura (paso 1a) y de avance de foco al presionar Enter (paso 1b/1d). */
const FOCUS_ORDER: readonly NumericField[] = ['basePriceLoad', 'pointPrice'];

/** Tiempo que se muestra el mensaje de confirmacion antes de cerrarse solo. */
const SUCCESS_MESSAGE_DURATION_MS = 2000;

/**
 * Pantalla ADMIN: "Actualizar Anuncio Pasilla" (ANUNCIOS PASILLA) - siempre crea un anuncio nuevo.
 * A diferencia de "Actualizar Anuncio con Factor" (ver announcement-update), Form_ANUNCIOS
 * PASILLA.bas solo tiene dos campos reales (Pr_Base_CPS y Pr_AlmSana/"Pr Punto"); Fondo (RP) y
 * Especial (PASILLA) van fijos, no hay selector.
 */
@Component({
  selector: 'app-announcement-update-husk',
  standalone: true,
  imports: [CommonModule, FormsModule, AccessWindowComponent, AppButtonComponent],
  templateUrl: './announcement-update-husk.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AnnouncementUpdateHuskComponent {
  private readonly content = inject(ContentService);
  private readonly announcementService = inject(AnnouncementService);
  private readonly elementRef = inject(ElementRef);

  readonly page = toSignal(this.content.loadJson<AnnouncementUpdateHuskContent>('announcement-update-husk'));
  readonly message = signal<string | null>(null);
  readonly error = signal<string | null>(null);

  /** Valores formateados con puntos de miles, tal como se muestran en el input. */
  numeric: Record<NumericField, string> = { basePriceLoad: '', pointPrice: '' };
  private successMessageTimeout?: ReturnType<typeof setTimeout>;

  constructor() {
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

  /** Enter avanza al siguiente campo; en el ultimo (Pr Por Punto) dispara la actualizacion. */
  onEnter(field: NumericField): void {
    const idx = FOCUS_ORDER.indexOf(field);
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
    const pointPrice = this.numberValue('pointPrice');
    if (basePriceLoad == null || pointPrice == null) {
      return;
    }
    this.announcementService.createHusk({ basePriceLoad, pointPrice }).subscribe({
      next: (announcement) => {
        this.message.set(`${this.page()?.successMessage ?? ''} ${announcement.announcementNumber}`);
        this.successMessageTimeout = setTimeout(() => this.message.set(null), SUCCESS_MESSAGE_DURATION_MS);
      },
      error: () => this.error.set(this.page()?.errorMessage ?? null),
    });
  }
}
