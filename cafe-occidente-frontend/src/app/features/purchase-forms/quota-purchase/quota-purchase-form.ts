import { CommonModule, Location } from '@angular/common';
import { ChangeDetectionStrategy, Component, ElementRef, HostListener, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';

import { FormFieldDefinition, PurchaseFormContent } from '../../../core/models';
import {
  DryCoffeePurchaseCalculation,
  DryCoffeePurchaseRequest,
  Fund,
  NextInvoiceNumber,
  SpecialInfo,
} from '../../../core/models/dry-coffee-purchase.model';
import { AuthService } from '../../../core/services/auth.service';
import { ContentService } from '../../../core/services/content.service';
import { DryCoffeePurchaseService } from '../../../core/services/dry-coffee-purchase.service';
import { GrowerService } from '../../../core/services/grower.service';
import { ConfirmDialogComponent, PurchaseFormViewComponent } from '../../../shared/ui';
import { parseDisplayNumber, stripAnnouncementPrefix } from '../../../shared/utils/number-format';
import { isSequentialFieldEnabled } from '../../../shared/utils/sequential-gate';

type FormModel = Record<string, string>;

const EDITABLE: string[] = [
  'fund', 'special', 'idPart1',
  'totalStoredWeight', 'totalHuskWeight', 'healthyStoredWeight',
  'bags', 'grossKg', 'tare', 'penalty', 'shrinkageDiscount', 'otherDiscounts',
];

const REQUIRED: string[] = [
  'fund', 'special', 'idPart1', 'totalStoredWeight', 'totalHuskWeight', 'healthyStoredWeight', 'bags', 'grossKg', 'tare',
];

const ZERO_IF_EMPTY: string[] = ['penalty', 'shrinkageDiscount', 'otherDiscounts'];

const READONLY: string[] = [
  'agency', 'date', 'announcement', 'announcementDate', 'invoice', 'productCode', 'basePriceLoad', 'huskPrice',
  'healthyUnitPrice', 'bonus', 'costs',
  'wastePercentage', 'huskPercentage', 'factor', 'netKg', 'kgPrice', 'grossValue', 'contribution', 'netToPay',
];

const FOCUS_ORDER: string[] = [
  'fund', 'special', 'idPart1',
  'totalStoredWeight', 'totalHuskWeight', 'healthyStoredWeight',
  'bags', 'grossKg', 'tare', 'penalty', 'shrinkageDiscount', 'otherDiscounts',
];

const num = parseDisplayNumber;

/**
 * "Facturar Anuncios con Cupos" (COMPRAS CUPOS): Form_COMPRAS CUPOS.bas tiene la MISMA cascada de
 * calculo que Cafe Seco linea por linea (var1-var5, Vr_Kilo, Vr_Bruto, Aporte_Socio/Descuento_Coop,
 * Retefuente, Neto_a_Pagar) - reusa el mismo backend (DryCoffeePurchaseService). A diferencia de
 * "Facturar Compras Anunciadas" (ver quota-billing-form.ts), este SI tiene "Informacion Federacion"
 * (Texto108/110/111 via "abrir programa pcupos", igual patron que Cafe Seco).
 * SIN CONFIRMAR: el VBA no muestra si Factura se reserva sola (como Cafe Seco) o la tipea el cajero
 * (como Anunciadas) - se asume reserva automatica al confirmar Fondo, mismo patron que Cafe Seco,
 * por ser el flujo mas parecido; revisar con el negocio si no es el caso real.
 */
@Component({
  selector: 'app-quota-purchase-form',
  standalone: true,
  imports: [CommonModule, PurchaseFormViewComponent, ConfirmDialogComponent],
  templateUrl: './quota-purchase-form.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QuotaPurchaseFormComponent {
  private readonly content = inject(ContentService);
  private readonly service = inject(DryCoffeePurchaseService);
  private readonly growerService = inject(GrowerService);
  private readonly authService = inject(AuthService);
  private readonly location = inject(Location);
  private readonly elementRef = inject(ElementRef);

  private readonly base = toSignal(this.content.loadJson<PurchaseFormContent>('purchase-form-quota-purchase'));
  readonly funds = signal<Fund[]>([]);
  readonly specialInfo = signal<SpecialInfo | null>(null);
  readonly invoiceReservation = signal<NextInvoiceNumber | null>(null);
  readonly calc = signal<DryCoffeePurchaseCalculation | null>(null);
  readonly errorMessage = signal<string | null>(null);
  private readonly tick = signal(0);

  model: FormModel = {};
  private readonly locked = new Set<string>();
  private readonly today = new Date().toISOString().slice(0, 10);
  private readonly fieldCache = new Map<string, FormFieldDefinition>();
  private fundOptions: string[] = [];
  private readonly fundIdByName = new Map<string, number>();

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
    this.model['date'] = this.today;
    this.service.funds().subscribe((list) => {
      this.funds.set(list);
      this.fundOptions = list.map((f) => f.code);
      list.forEach((f) => this.fundIdByName.set(f.code, f.id));
      this.tick.update((n) => n + 1);
    });
    // Foco en el primer campo apenas carga el contenido - ver mismo fix en dry-coffee-form.ts.
    effect(() => {
      if (this.base()) {
        this.focusField(FOCUS_ORDER[0]);
      }
    });
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
    if (value === '' && ZERO_IF_EMPTY.includes(key)) {
      this.model[key] = '0';
    }
    if (key !== 'idPart1') {
      this.locked.add(key);
    }
    this.runSideEffects(key);
    this.tick.update((n) => n + 1);
    if (key !== 'idPart1') {
      this.advanceFocus(key);
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

  private advanceFocus(afterKey: string): void {
    const idx = FOCUS_ORDER.indexOf(afterKey);
    if (idx === -1 || idx === FOCUS_ORDER.length - 1) {
      return;
    }
    this.focusField(FOCUS_ORDER[idx + 1]);
  }

  private runSideEffects(key: string): void {
    switch (key) {
      case 'fund':
        this.reserveInvoiceNumber();
        break;
      case 'special':
        this.loadSpecialInfo();
        break;
      case 'idPart1':
        this.lookupGrower();
        break;
      case 'totalStoredWeight':
      case 'totalHuskWeight':
      case 'healthyStoredWeight':
        this.loadQualityPercentage(key);
        this.recalculate();
        break;
      case 'grossKg':
      case 'tare':
      case 'penalty':
      case 'shrinkageDiscount':
      case 'otherDiscounts':
        this.recalculate();
        break;
    }
  }

  private reserveInvoiceNumber(): void {
    this.service.nextInvoiceNumber().subscribe({
      next: (inv) => {
        this.invoiceReservation.set(inv);
        this.tick.update((n) => n + 1);
      },
      error: () => this.tick.update((n) => n + 1),
    });
  }

  private loadSpecialInfo(): void {
    const agencyId = this.authService.agencyId();
    const fundId = this.fundIdByName.get(this.model['fund'] ?? '');
    const specialType = this.model['special'];
    if (!agencyId || !fundId || !specialType) {
      return;
    }
    this.service.specialInfo(agencyId, fundId, specialType).subscribe({
      next: (info) => {
        this.specialInfo.set(info);
        this.tick.update((n) => n + 1);
      },
      error: () => {
        this.specialInfo.set(null);
        this.errorMessage.set('No hay un anuncio activo para esa agencia/fondo/especial.');
        this.tick.update((n) => n + 1);
      },
    });
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
        this.model['cellphone'] = grower.phone;
        this.model['idNumber'] = idNumber;
        this.model['fullName'] = [firstNames, lastNames].filter(Boolean).join(' ');
        this.locked.add('idPart1');
        this.locked.add('idNumber');
        // Si el dato migrado viene vacio (p.ej. caficultores historicos sin celular registrado), no se
        // bloquea: sin esto el campo quedaba en blanco y bloqueado para siempre, y como esta en REQUIRED
        // la cascada de calculo nunca llegaba a dispararse.
        ['firstNames', 'lastNames', 'idType', 'address', 'cellphone', 'fullName'].forEach((k) => {
          if ((this.model[k] ?? '').trim() !== '') {
            this.locked.add(k);
          }
        });
        this.lookupProgram();
        this.tick.update((n) => n + 1);
        this.advanceFocus('idPart1');
      },
      // No encontrado: cedula no registrada - avisa y no deja avanzar hasta que se corrija.
      error: () => {
        this.errorMessage.set('Este número de cédula no existe.');
        this.tick.update((n) => n + 1);
      },
    });
  }

  /** Mismo endpoint/mapeo que Cafe Seco (GrowerServiceImpl.findProgram): solo 3 Especiales tienen
   *  tabla de programa migrada, el resto queda vacio y editable, nunca bloquea. */
  private lookupProgram(): void {
    const idNumber = (this.model['idPart1'] ?? '').trim();
    if (!idNumber) {
      return;
    }
    this.growerService.findProgram(idNumber, this.model['special']).subscribe((program) => {
      if (program) {
        this.model['program'] = program.programa;
        this.locked.add('program');
      } else {
        this.model['program'] = '';
        this.locked.delete('program');
      }
      this.tick.update((n) => n + 1);
    });
  }

  private loadQualityPercentage(key: 'totalStoredWeight' | 'totalHuskWeight' | 'healthyStoredWeight'): void {
    const value = num(this.model[key]);
    const args: [number?, number?, number?] =
      key === 'totalStoredWeight'
        ? [value, undefined, undefined]
        : key === 'totalHuskWeight'
          ? [undefined, value, undefined]
          : [undefined, undefined, value];
    this.service.qualityPercentages(...args).subscribe((q) => {
      const target: Record<string, string> = {
        totalStoredWeight: 'wastePercentage',
        totalHuskWeight: 'huskPercentage',
        healthyStoredWeight: 'factor',
      };
      const value2 =
        key === 'totalStoredWeight' ? q.wastePercentage : key === 'totalHuskWeight' ? q.defectivePercentage : q.healthyPercentage;
      this.model[target[key]] = value2 != null ? String(value2) : '';
      this.locked.add(target[key]);
      this.tick.update((n) => n + 1);
    });
  }

  private recalculate(): void {
    const request = this.buildRequest();
    if (!request) {
      return;
    }
    this.service.preview(request).subscribe({
      next: (c) => {
        this.calc.set(c);
        this.tick.update((n) => n + 1);
      },
      error: () => {
        this.calc.set(null);
        this.tick.update((n) => n + 1);
      },
    });
  }

  private buildRequest(): DryCoffeePurchaseRequest | null {
    const agencyId = this.authService.agencyId();
    const fundId = this.fundIdByName.get(this.model['fund'] ?? '');
    const invoiceNumber = this.invoiceReservation()?.invoiceNumber;
    const hasRequiredCascadeInputs = REQUIRED.every((k) => (this.model[k] ?? '').trim() !== '');
    if (!agencyId || !fundId || !invoiceNumber || !hasRequiredCascadeInputs) {
      return null;
    }
    return {
      agencyId,
      fundId,
      invoiceNumber,
      specialType: this.model['special'],
      idNumber: this.model['idPart1'],
      firstName: this.model['firstNames'] ?? '',
      lastName: this.model['lastNames'] ?? '',
      growerType: (this.model['idType'] ?? '').trim().toUpperCase(),
      address: this.model['address'],
      cellphone: this.model['cellphone'],
      bagsCount: num(this.model['bags']),
      grossKg: num(this.model['grossKg']),
      tareKg: num(this.model['tare']),
      totalStoredWeight: num(this.model['totalStoredWeight']),
      defectiveStoredWeight: num(this.model['totalHuskWeight']),
      healthyStoredWeight: num(this.model['healthyStoredWeight']),
      healthyUnitPrice: this.specialInfo()?.healthyUnitPrice ?? 0,
      bonus: this.specialInfo()?.bonus ?? 0,
      penalty: num(this.model['penalty']),
      costs: this.specialInfo()?.costs ?? 0,
      withholdingExempt: false,
      freightDiscount: num(this.model['shrinkageDiscount']),
      otherDiscounts: num(this.model['otherDiscounts']),
      paymentMethod: 'EFECTIVO',
      checkNumber: null,
    };
  }

  /** Sin persistir todavia: no hay tabla propia para "compras con cupo" (ver resumen). */
  print(): void {}

  private canPrint(): boolean {
    return (
      !!this.specialInfo() &&
      !!this.invoiceReservation() &&
      !!this.calc() &&
      this.locked.has('otherDiscounts') &&
      REQUIRED.every((k) => (this.model[k] ?? '').trim() !== '')
    );
  }

  @HostListener('document:keydown.escape')
  reset(): void {
    this.model = { agency: this.authService.agencyName() ?? '', date: this.today };
    this.locked.clear();
    this.specialInfo.set(null);
    this.invoiceReservation.set(null);
    this.calc.set(null);
    this.errorMessage.set(null);
    this.fieldCache.clear();
    this.tick.update((n) => n + 1);
  }

  onClose(): void {
    this.location.back();
  }

  private buildContent(base: PurchaseFormContent): PurchaseFormContent {
    const bmap = new Map<string, FormFieldDefinition>();
    const collect = (fields?: FormFieldDefinition[]) => fields?.forEach((f) => bmap.set(f.key, f));
    collect(base.topFields);
    collect(base.identificationFields);
    collect(base.federationFields);
    collect(base.contactFields);
    collect(base.qualityFields);
    collect(base.weightFields);
    collect(base.netWeightFields);
    collect(base.settlementFields);
    collect(base.settlementSecondaryFields);
    if (base.discountField) {
      bmap.set(base.discountField.key, base.discountField);
    }
    if (base.additionalDiscountFields) {
      base.additionalDiscountFields.forEach((f) => bmap.set(f.key, f));
    }

    const info = this.specialInfo();
    const inv = this.invoiceReservation();
    const c = this.calc();
    const growerType = (this.model['idType'] ?? '').trim().toUpperCase();
    const netKgLocal =
      this.model['grossKg'] && this.model['tare'] ? num(this.model['grossKg']) - num(this.model['tare']) : '';

    const computedValues: Record<string, string | number> = {
      agency: this.authService.agencyName() ?? '',
      announcement: info?.announcementNumber ? stripAnnouncementPrefix(info.announcementNumber) : '',
      announcementDate: info?.announcementDate ?? '',
      invoice: inv?.invoiceNumber ?? '',
      productCode: info?.productCode ?? '',
      basePriceLoad: info?.basePriceLoad ?? '',
      huskPrice: info?.defectiveUnitPrice ?? '',
      healthyUnitPrice: info?.healthyUnitPrice ?? '',
      bonus: info?.bonus ?? '',
      costs: info?.costs ?? '',
      netKg: c ? c.netKg : netKgLocal,
      kgPrice: c?.unitPrice ?? '',
      grossValue: c?.grossValue ?? '',
      contribution: c ? (growerType === 'C' ? c.cooperativeDiscount : c.associateContribution) : '',
      netToPay: c?.netToPay ?? '',
    };

    const field = (key: string): FormFieldDefinition => {
      const b = bmap.get(key)!;
      const patch: { options?: string[]; readonly?: boolean; value?: string | number } = {};
      if (key === 'fund') {
        patch.options = this.fundOptions;
      }
      if (READONLY.includes(key)) {
        patch.readonly = true;
        patch.value = computedValues[key] ?? this.model[key] ?? '';
      } else if (this.locked.has(key)) {
        patch.readonly = true;
        patch.value = this.model[key] ?? '';
      } else if (!isSequentialFieldEnabled(key, FOCUS_ORDER, this.locked)) {
        patch.readonly = true;
      }
      const next: FormFieldDefinition = { ...b, ...patch };
      const prev = this.fieldCache.get(key);
      if (prev && prev.value === next.value && prev.readonly === next.readonly && prev.options === next.options) {
        return prev;
      }
      this.fieldCache.set(key, next);
      return next;
    };

    const row = (fields?: FormFieldDefinition[]) => fields?.map((f) => field(f.key));

    return {
      ...base,
      topFields: row(base.topFields)!,
      identificationFields: row(base.identificationFields)!,
      federationFields: row(base.federationFields),
      contactFields: row(base.contactFields),
      qualityFields: row(base.qualityFields),
      weightFields: row(base.weightFields),
      netWeightFields: row(base.netWeightFields),
      settlementFields: row(base.settlementFields),
      settlementSecondaryFields: row(base.settlementSecondaryFields),
      additionalDiscountFields: row(base.additionalDiscountFields),
      discountField: base.discountField ? field(base.discountField.key) : undefined,
      reprintButtonLabel: this.canPrint() ? 'Imprimir' : undefined,
    };
  }
}
