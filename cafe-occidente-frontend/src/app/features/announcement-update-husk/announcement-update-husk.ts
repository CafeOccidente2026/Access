import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, ElementRef, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';

import { AnnouncementService } from '../../core/services/announcement.service';
import { ContentService } from '../../core/services/content.service';
import { AccessWindowComponent, AppButtonComponent, SUCCESS_MESSAGE_DURATION_MS, SuccessOverlayComponent } from '../../shared/ui';
import { formatThousands, validateWholeNumberField } from '../../shared/utils/number-format';
import { AnnouncementUpdateHuskContent } from './announcement-update-husk.model';

type NumericField = 'basePriceLoad' | 'pointPrice';

/** RN-09.1: orden de captura; Enter pasa al siguiente campo solo si el actual esta diligenciado. */
const FOCUS_ORDER: readonly NumericField[] = ['basePriceLoad', 'pointPrice'];

/**
 * "Actualizar Anuncio Pasilla" (ANUNCIOS PASILLA, RF-09, HU-12) - siempre crea un anuncio nuevo, comun a todas
 * las agencias. Administrador, Operaciones o con el permiso Actualizar Anuncios.
 * A diferencia de "Actualizar Anuncio con Factor" (ver announcement-update), Form_ANUNCIOS
 * PASILLA.bas solo tiene dos campos reales (Pr_Base_CPS y Pr_AlmSana/"Pr Punto"); Fondo (RP) y
 * Especial (PASILLA) van fijos, no hay selector.
 */
@Component({
  selector: 'app-announcement-update-husk',
  standalone: true,
  imports: [CommonModule, FormsModule, AccessWindowComponent, AppButtonComponent, SuccessOverlayComponent],
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
  /** Mensaje de error puntual a mostrar cerca del campo, o null si lo que hay escrito es valido. */
  fieldErrors: Record<NumericField, string | null> = { basePriceLoad: null, pointPrice: null };
  private successMessageTimeout?: ReturnType<typeof setTimeout>;

  constructor() {
    // Paso 1c: foco en el primer campo apenas la pantalla termina de cargar su contenido.
    effect(() => {
      if (this.page()) {
        this.focusField(FOCUS_ORDER[0]);
      }
    });
  }

  /** Valida en vivo mientras se escribe; si hay error deja ver lo tipeado tal cual, si no formatea
   *  con puntos de miles como antes. */
  onNumericInput(field: NumericField, value: string): void {
    const error = validateWholeNumberField(value);
    this.fieldErrors[field] = error;
    if (error) {
      this.numeric[field] = value;
      return;
    }
    const digits = value.replace(/\D/g, '');
    this.numeric[field] = formatThousands(digits);
  }

  /** RN-09.1: Enter avanza al siguiente campo si el actual esta diligenciado; en el ultimo (Pr Por Punto)
   *  dispara la actualizacion. */
  onEnter(field: NumericField): void {
    if (this.numberValue(field) == null) {
      this.focusField(field);
      return;
    }
    const idx = FOCUS_ORDER.indexOf(field);
    if (idx === FOCUS_ORDER.length - 1) {
      this.update();
      return;
    }
    this.focusField(FOCUS_ORDER[idx + 1]);
  }

  /** RN-09.1: no se salta con el mouse a un campo si uno anterior esta vacio. */
  onFocus(field: NumericField): void {
    const firstEmpty = FOCUS_ORDER.slice(0, FOCUS_ORDER.indexOf(field)).find((f) => this.numberValue(f) == null);
    if (firstEmpty) {
      this.focusField(firstEmpty);
    }
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
    if (this.fieldErrors[field]) {
      return null;
    }
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
      // RN-09.2: "Anuncio actualizado" durante 3 segundos.
      next: () => {
        this.message.set(this.page()?.successMessage ?? null);
        this.successMessageTimeout = setTimeout(() => this.message.set(null), SUCCESS_MESSAGE_DURATION_MS);
      },
      error: (err) => {
        const message = (err as { error?: { message?: string } })?.error?.message;
        this.error.set(message ?? this.page()?.errorMessage ?? null);
      },
    });
  }
}
