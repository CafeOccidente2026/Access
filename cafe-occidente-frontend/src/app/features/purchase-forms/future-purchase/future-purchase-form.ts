import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, ElementRef, HostListener, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';

import { FormFieldDefinition, PurchaseFormContent } from '../../../core/models';
import { AuthService } from '../../../core/services/auth.service';
import { ContentService } from '../../../core/services/content.service';
import { GrowerService } from '../../../core/services/grower.service';
import { PurchaseFormViewComponent } from '../../../shared/ui';

type FormModel = Record<string, string>;

const EDITABLE: string[] = ['idPart1', 'special', 'qualityIncrement', 'deliveryDate', 'kilos'];
const REQUIRED: string[] = ['idPart1', 'special', 'kilos'];
const FOCUS_ORDER: string[] = ['idPart1', 'special', 'qualityIncrement', 'deliveryDate', 'kilos'];

/**
 * "Ingresar Compras a Futuro" (COMPRAS A FUTURO): Form_COMPRAS A FUTURO.bas no tiene NINGUNA
 * cascada de precio (sin Vr_Kilo/Vr_Bruto/Neto_a_Pagar) - es un anuncio/compromiso de venta futura,
 * no una compra liquidada. Cedula busca al caficultor; Especial asigna Anuncio/Fecha_Anuncio/
 * precios (Pr Alm Sana/Defec/Bonificacion/Costos/Pr carga perg s) via "Abrir Ultimo Anuncio X Para
 * Compras" (macro que copia el ULTIMO registro de una tabla por Especial, SIN Fondo - a diferencia
 * de Cafe Seco, este flujo no tiene Cuadro_combinado37/Fondo en ningun lado del formulario real);
 * Kilos_LostFocus simplemente copia SaldoKilos = Kilos.
 *
 * PENDIENTE (sin implementar, ver resumen): el autocompletado de Especial->Anuncio/precios no esta
 * wireado todavia. DryCoffeePurchaseService.specialInfo() exige fundId (AgencyAnnouncementNumber
 * esta indexado por agencia+fondo+especial) y este formulario no tiene Fondo - la fuente real del
 * VBA es una tabla distinta ("ULTIMO ANUN NESS/RAIN/EXPON... PARA COMPRAS") que no fue migrada.
 * Falta decidir si se migra esa tabla o si se relaja specialInfo() a buscar sin fondo.
 */
@Component({
  selector: 'app-future-purchase-form',
  standalone: true,
  imports: [CommonModule, PurchaseFormViewComponent],
  templateUrl: './future-purchase-form.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FuturePurchaseFormComponent {
  private readonly content = inject(ContentService);
  private readonly growerService = inject(GrowerService);
  private readonly authService = inject(AuthService);
  private readonly elementRef = inject(ElementRef);

  private readonly base = toSignal(this.content.loadJson<PurchaseFormContent>('purchase-form-future'));
  private readonly tick = signal(0);

  model: FormModel = {};
  private readonly locked = new Set<string>();
  private readonly fieldCache = new Map<string, FormFieldDefinition>();

  readonly viewContent = computed<PurchaseFormContent | null>(() => {
    const base = this.base();
    this.tick();
    if (!base) {
      return null;
    }
    return this.buildContent(base);
  });

  constructor() {
    this.model['agency'] = this.authService.agencyName() ?? '';
  }

  onFieldValueChange(event: { key: string; value: string | number }): void {
    this.model[event.key] = String(event.value);
  }

  onFieldCommitted(key: string): void {
    if (!EDITABLE.includes(key)) {
      return;
    }
    const value = (this.model[key] ?? '').trim();
    if (value === '' && REQUIRED.includes(key)) {
      return;
    }
    this.locked.add(key);
    this.runSideEffects(key);
    this.tick.update((n) => n + 1);
    this.advanceFocus(key);
  }

  private runSideEffects(key: string): void {
    if (key === 'idPart1') {
      this.lookupGrower();
    } else if (key === 'kilos') {
      this.model['balance'] = this.model['kilos'];
      this.locked.add('balance');
    }
  }

  private lookupGrower(): void {
    const idNumber = (this.model['idPart1'] ?? '').trim();
    if (!idNumber) {
      return;
    }
    this.growerService.findByIdNumber(idNumber).subscribe({
      next: (grower) => {
        const firstNames = [grower.firstName, grower.secondName].filter(Boolean).join(' ');
        const lastNames = [grower.lastName, grower.secondLastName].filter(Boolean).join(' ');
        this.model['firstNames'] = firstNames;
        this.model['lastNames'] = lastNames;
        this.model['idType'] = grower.growerType;
        this.model['address'] = grower.address;
        this.model['idNumber'] = idNumber;
        this.model['fullName'] = [firstNames, lastNames].filter(Boolean).join(' ');
        ['firstNames', 'lastNames', 'idType', 'address', 'idNumber', 'fullName'].forEach((k) => this.locked.add(k));
        this.lookupProgram();
        this.tick.update((n) => n + 1);
      },
      error: () => {},
    });
  }

  private lookupProgram(): void {
    const idNumber = (this.model['idPart1'] ?? '').trim();
    this.growerService.findProgram(idNumber, this.model['special']).subscribe((program) => {
      this.model['program'] = program ? program.programa : '';
      if (program) {
        this.locked.add('program');
      } else {
        this.locked.delete('program');
      }
      this.tick.update((n) => n + 1);
    });
  }

  private focusField(key: string): void {
    setTimeout(() => {
      const el = this.elementRef.nativeElement.querySelector(`[data-field-key="${key}"]`) as HTMLElement | null;
      el?.focus();
      el?.classList.add('field-flash');
      setTimeout(() => el?.classList.remove('field-flash'), 1000);
    });
  }

  private advanceFocus(afterKey: string): void {
    const idx = FOCUS_ORDER.indexOf(afterKey);
    if (idx === -1 || idx === FOCUS_ORDER.length - 1) {
      return;
    }
    this.focusField(FOCUS_ORDER[idx + 1]);
  }

  /** Comando36_Click en el VBA solo abre el reporte "Manifiesto" (sin persistir nada). Igual que el
   *  resto de las pantallas, la generacion real del documento queda pendiente (prompt futuro). */
  print(): void {}

  private canPrint(): boolean {
    return REQUIRED.every((k) => (this.model[k] ?? '').trim() !== '');
  }

  @HostListener('document:keydown.escape')
  reset(): void {
    this.model = { agency: this.authService.agencyName() ?? '' };
    this.locked.clear();
    this.fieldCache.clear();
    this.tick.update((n) => n + 1);
  }

  private buildContent(base: PurchaseFormContent): PurchaseFormContent {
    const bmap = new Map<string, FormFieldDefinition>();
    const collect = (fields?: FormFieldDefinition[]) => fields?.forEach((f) => bmap.set(f.key, f));
    collect(base.topFields);
    collect(base.federationFields);
    collect(base.identificationFields);
    collect(base.contactFields);
    collect(base.qualityFields);
    collect(base.weightFields);

    const field = (key: string): FormFieldDefinition => {
      const b = bmap.get(key)!;
      const patch: { readonly?: boolean; value?: string | number } = {};
      if (b.readonly || this.locked.has(key)) {
        patch.readonly = true;
        patch.value = this.model[key] ?? '';
      }
      const next: FormFieldDefinition = { ...b, ...patch };
      const prev = this.fieldCache.get(key);
      if (prev && prev.value === next.value && prev.readonly === next.readonly) {
        return prev;
      }
      this.fieldCache.set(key, next);
      return next;
    };

    const row = (fields?: FormFieldDefinition[]) => fields?.map((f) => field(f.key));

    return {
      ...base,
      topFields: row(base.topFields)!,
      federationFields: row(base.federationFields),
      identificationFields: row(base.identificationFields)!,
      contactFields: row(base.contactFields),
      qualityFields: row(base.qualityFields),
      weightFields: row(base.weightFields),
      reprintButtonLabel: this.canPrint() ? base.reprintButtonLabel : undefined,
    };
  }
}
