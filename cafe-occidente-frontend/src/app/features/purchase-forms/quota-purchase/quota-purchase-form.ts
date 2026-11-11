import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, ElementRef, HostListener, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { map } from 'rxjs';

import { FormFieldDefinition, PurchaseFormContent, PurchasePayment } from '../../../core/models';
import {
  DryCoffeePurchaseCalculation,
  DryCoffeePurchaseRequest,
  Fund,
  NextInvoiceNumber,
  SpecialInfo,
} from '../../../core/models/dry-coffee-purchase.model';
import { QuotaBalance } from '../../../core/models/announcement-quota.model';
import { FuturePurchaseResponse } from '../../../core/models/future-purchase.model';
import { AnnouncementQuotaService } from '../../../core/services/announcement-quota.service';
import { AuthService } from '../../../core/services/auth.service';
import { ContentService } from '../../../core/services/content.service';
import { DryCoffeePurchaseService } from '../../../core/services/dry-coffee-purchase.service';
import { FuturePurchaseService } from '../../../core/services/future-purchase.service';
import { GrowerService } from '../../../core/services/grower.service';
import { ConfirmDialogComponent, PurchaseFormViewComponent } from '../../../shared/ui';
import { parseDisplayNumber, stripAnnouncementPrefix } from '../../../shared/utils/number-format';
import { isSequentialFieldEnabled } from '../../../shared/utils/sequential-gate';
import { buildDryCoffeeInvoiceDocDefinition } from '../dry-coffee/dry-coffee-invoice';
import { hasRequired, saveAndPrint } from '../purchase-print';
import { SampleWeightKey, clearSample, isOverThreshold, loadSampleQuality } from '../sample-quality';
import { UnsavedCloseForm } from '../unsaved-close';
import { QuotaPick, QuotaPickerComponent, QuotaPickerContent } from './quota-picker';

type FormModel = Record<string, string>;

interface QuotaPurchaseMessages {
  readonly cooperativeDiscountLabel?: string;
  readonly acceptLabel: string;
  readonly noQuota: string;
  readonly idNumberNotFound: string;
  readonly saveError: string;
  readonly savedNotice: string;
  readonly invoiceError: string;
  readonly closeWarning: string;
  readonly closeConfirm: string;
  readonly closeCancel: string;
}

type QuotaPurchaseContent = PurchaseFormContent & {
  readonly messages: QuotaPurchaseMessages;
  readonly quotaPicker: QuotaPickerContent;
};

/** 'quota' = Facturar Anuncios con Cupos (RF-21.2); 'future' = Facturar Compras Anunciadas (RF-21.5). */
type Mode = 'quota' | 'future';

const WEIGHTS_AND_DISCOUNTS: string[] = [
  'totalStoredWeight', 'totalHuskWeight',
  'bags', 'grossKg', 'tare', 'penalty', 'shrinkageDiscount', 'otherDiscounts',
];

/** En anunciadas el Especial no se elige: viene de la compra anunciada. */
const EDITABLE: Record<Mode, string[]> = {
  quota: ['fund', 'special', 'idPart1', ...WEIGHTS_AND_DISCOUNTS],
  future: ['fund', 'idPart1', ...WEIGHTS_AND_DISCOUNTS],
};

const REQUIRED: string[] = [
  'fund', 'special', 'idPart1', 'totalStoredWeight', 'totalHuskWeight', 'bags', 'grossKg', 'tare',
];

const ZERO_IF_EMPTY: string[] = ['penalty', 'shrinkageDiscount', 'otherDiscounts'];

const READONLY: string[] = [
  // Q2: Peso Alm Sana = Peso Tot Alm - Peso Pasilla, lo calcula el servidor.
  'healthyStoredWeight',
  'agency', 'date', 'announcement', 'announcementDate', 'invoice', 'productCode', 'basePriceLoad', 'huskPrice',
  'healthyUnitPrice', 'bonus', 'costs', 'deliveryKg',
  'wastePercentage', 'huskPercentage', 'factor', 'netKg', 'kgPrice', 'grossValue', 'contribution', 'netToPay',
  // RN-15.6: la Retefuente la calcula el servidor (acumulado del dia).
  'withholding',
];

const FOCUS_ORDER: Record<Mode, string[]> = {
  quota: [...EDITABLE.quota, 'payCash'],
  future: [...EDITABLE.future, 'payCash'],
};

/** La compra anunciada en la forma de la ventana (RN-21.5.2: la misma de RF-21.2): Kilos, entregados y saldo. */
function futureRow(f: FuturePurchaseResponse): QuotaBalance {
  return {
    announcement: {
      id: f.id, announcementNumber: f.announcementNumber, announcementDate: f.announcementDate,
      basePriceLoad: f.basePriceLoad, defectiveUnitPrice: f.defectiveUnitPrice, healthyUnitPrice: f.healthyUnitPrice,
      bonus: f.bonus, costs: f.costs, agencyId: f.agencyId, fundId: 0, specialType: f.specialType,
      qualityIncrement: f.qualityIncrement,
    },
    assignedQuota: f.announcedKg,
    deliveredKg: f.announcedKg - f.remainingKg,
    balance: f.remainingKg,
  };
}

const num = parseDisplayNumber;

/**
 * "Facturar Anuncios con Cupos" (RF-21.2, Access "COMPRAS CUPOS"): una compra de café seco (mismas reglas y cálculo
 * del servidor) contra un anuncio con cupo. Al confirmar el Especial se abre la ventana con los anuncios con cupo de
 * ese fondo y especial; el elegido pone sus precios (no los del último anuncio) y "Esta Entrega" queda en el recuadro
 * Kilos Entregados. El saldo se descuenta solo al generar la factura, en la misma transacción de la compra; salir
 * antes no guarda nada (RN-21.2.8).
 * Con `data.mode = 'future'` en la ruta es "Facturar Compras Anunciadas" (RF-21.5, Access "ANUNCIADAS"): la cédula
 * abre la misma ventana con las compras anunciadas de esa persona; la elegida pone su especial y sus precios, y su
 * saldo se descuenta al facturar.
 */
@Component({
  selector: 'app-quota-purchase-form',
  standalone: true,
  imports: [CommonModule, PurchaseFormViewComponent, ConfirmDialogComponent, QuotaPickerComponent],
  templateUrl: './quota-purchase-form.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QuotaPurchaseFormComponent extends UnsavedCloseForm {
  private readonly content = inject(ContentService);
  private readonly service = inject(DryCoffeePurchaseService);
  private readonly growerService = inject(GrowerService);
  private readonly authService = inject(AuthService);
  private readonly quotaService = inject(AnnouncementQuotaService);
  private readonly futureService = inject(FuturePurchaseService);
  private readonly elementRef = inject(ElementRef);
  private readonly mode: Mode = inject(ActivatedRoute, { optional: true })?.snapshot.data['mode'] ?? 'quota';
  private readonly editable = EDITABLE[this.mode];
  private readonly focusOrder = FOCUS_ORDER[this.mode];
  private readonly readonlyKeys = this.mode === 'future' ? [...READONLY, 'special'] : READONLY;

  private readonly base = toSignal(this.content.loadJson<QuotaPurchaseContent>(
    this.mode === 'future' ? 'purchase-form-quota-billing' : 'purchase-form-quota-purchase',
  ));
  /** Prueba del 9-nov (punto 3): Especial ofrece la misma lista de Compras Cafe Seco, que es la que usan los anuncios
   *  (la propia de Cupos traia "COLOMBIA TIERRA DE DIVERSIDAD" sin RN y no encontraba el anuncio). */
  private readonly specialOptions = toSignal(
    this.content.loadJson<PurchaseFormContent>('purchase-form-dry').pipe(
      map((dry) => dry.qualityFields?.find((f) => f.key === 'special')?.options ?? []),
    ),
    { initialValue: [] as string[] },
  );
  readonly messages = computed(() => this.base()?.messages);
  readonly pickerContent = computed(() => this.base()?.quotaPicker);
  readonly agencyName = this.authService.agencyName() ?? '';
  /** Filas de la ventana; null = ventana cerrada. */
  readonly quotaRows = signal<QuotaBalance[] | null>(null);
  /** Anuncio con cupo elegido y Esta Entrega. */
  readonly quotaChoice = signal<QuotaPick | null>(null);
  /** true mientras se guarda: un segundo Imprimir no vuelve a enviar (una sola factura). */
  readonly saving = signal(false);
  readonly savedNoticeOpen = signal(false);
  readonly funds = signal<Fund[]>([]);
  readonly specialInfo = signal<SpecialInfo | null>(null);
  readonly invoiceReservation = signal<NextInvoiceNumber | null>(null);
  readonly calc = signal<DryCoffeePurchaseCalculation | null>(null);
  /** El aviso abierto es el del umbral de especiales: al Aceptar se vacia la muestra. */
  private sampleRetry = false;
  /** FORMAS DE PAGO cuadrado (cascada FPef/FPch/FPtx/FPdat); null mientras no cuadra. */
  readonly payment = signal<PurchasePayment | null>(null);
  readonly errorMessage = signal<string | null>(null);
  private readonly tick = signal(0);

  override model: FormModel = {};
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
    super();
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
        this.focusField(this.focusOrder[0]);
      }
    });
  }

  onFieldValueChange(event: { key: string; value: string | number }): void {
    this.model[event.key] = String(event.value);
  }

  onFieldCommitted(key: string): void {
    if (!this.editable.includes(key)) {
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
    const idx = this.focusOrder.indexOf(afterKey);
    if (idx === -1 || idx === this.focusOrder.length - 1) {
      return;
    }
    this.focusField(this.focusOrder[idx + 1]);
  }

  private runSideEffects(key: string): void {
    switch (key) {
      case 'fund':
        this.reserveInvoiceNumber();
        break;
      case 'special':
        this.openQuotaPicker();
        break;
      case 'idPart1':
        this.lookupGrower();
        break;
      case 'totalStoredWeight':
      case 'totalHuskWeight':
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

  /** RN-21.2.3: el Especial abre la ventana con los anuncios con cupo de ese fondo y especial. */
  private openQuotaPicker(): void {
    const fundId = this.fundIdByName.get(this.model['fund'] ?? '');
    const specialType = this.model['special'];
    if (!fundId || !specialType) {
      return;
    }
    this.quotaService.withBalance(fundId, specialType).subscribe({
      next: (rows) => {
        if (rows.length === 0) {
          this.clearSpecial();
          this.errorMessage.set(this.messages()?.noQuota ?? null);
          return;
        }
        this.quotaRows.set(rows);
        this.focusField('quotaDelivery');
      },
      error: () => {
        this.clearSpecial();
        this.errorMessage.set(this.messages()?.saveError ?? null);
      },
    });
  }

  /** RN-21.5.2: la cédula abre la ventana con las compras anunciadas de esa persona que tienen saldo. */
  private openFuturePicker(idNumber: string): void {
    this.futureService.openFor(idNumber).subscribe({
      next: (list) => {
        if (list.length === 0) {
          this.clearChoice();
          this.errorMessage.set(this.messages()?.noQuota ?? null);
          return;
        }
        this.quotaRows.set(list.map(futureRow));
        this.focusField('quotaDelivery');
      },
      error: () => {
        this.clearChoice();
        this.errorMessage.set(this.messages()?.saveError ?? null);
      },
    });
  }

  /** CA-2: Enter con una entrega válida cierra la ventana y trae los datos del anuncio o de la compra anunciada. */
  onQuotaPicked(pick: QuotaPick): void {
    this.quotaRows.set(null);
    this.quotaChoice.set(pick);
    const fundId = this.fundIdByName.get(this.model['fund'] ?? '')!;
    const a = pick.row.announcement;
    if (this.mode === 'future') {
      this.model['special'] = a.specialType;
      this.locked.add('special');
      this.lookupProgram();
    }
    const info$ = this.mode === 'future'
      ? this.service.specialInfo(fundId, a.specialType, undefined, a.id)
      : this.service.specialInfo(fundId, a.specialType, Number(stripAnnouncementPrefix(a.announcementNumber)));
    info$.subscribe({
      next: (info) => {
        this.specialInfo.set(info);
        this.tick.update((n) => n + 1);
        this.advanceFocus(this.mode === 'future' ? 'idPart1' : 'special');
      },
      error: () => {
        this.clearChoice();
        this.errorMessage.set(this.messages()?.saveError ?? null);
      },
    });
  }

  /** Cancelar la ventana deja libre el Especial (cupos) o la cédula (anunciadas) para elegir otro. */
  onQuotaCancelled(): void {
    this.quotaRows.set(null);
    this.clearChoice();
  }

  private clearChoice(): void {
    if (this.mode === 'quota') {
      this.clearSpecial();
      return;
    }
    this.model['idPart1'] = '';
    this.model['special'] = '';
    this.locked.delete('idPart1');
    this.locked.delete('special');
    this.quotaChoice.set(null);
    this.specialInfo.set(null);
    this.tick.update((n) => n + 1);
    this.focusField('idPart1');
  }

  private clearSpecial(): void {
    this.model['special'] = '';
    this.locked.delete('special');
    this.quotaChoice.set(null);
    this.specialInfo.set(null);
    this.tick.update((n) => n + 1);
    this.focusField('special');
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
        this.tick.update((n) => n + 1);
        if (this.mode === 'future') {
          this.openFuturePicker(idNumber);
          return;
        }
        this.lookupProgram();
        this.advanceFocus('idPart1');
      },
      // No encontrado: cedula no registrada - avisa y no deja avanzar hasta que se corrija.
      error: () => {
        this.errorMessage.set(this.messages()?.idNumberNotFound ?? null);
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

  /** Peso Tot Alm da Porc Merma; Peso Pasilla da Porc Kg Pas, Peso Alm Sana y Factor (Q2, servidor). */
  private loadQualityPercentage(key: SampleWeightKey): void {
    loadSampleQuality(this.service, this.model, this.locked, key, () => this.tick.update((n) => n + 1));
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
      // Con todos los datos, un rechazo es real (p. ej. kilos netos sobre Esta Entrega): se dice por qué.
      error: (err) => {
        this.calc.set(null);
        this.sampleRetry = isOverThreshold(err);
        this.errorMessage.set(err?.error?.message ?? this.messages()?.saveError ?? null);
        this.tick.update((n) => n + 1);
      },
    });
  }

  private buildRequest(): DryCoffeePurchaseRequest | null {
    const fundId = this.fundIdByName.get(this.model['fund'] ?? '');
    const invoiceNumber = this.invoiceReservation()?.invoiceNumber;
    const choice = this.quotaChoice();
    if (!fundId || !invoiceNumber || !choice || !hasRequired(this.model, REQUIRED)) {
      return null;
    }
    return {
      fundId,
      specialType: this.model['special'],
      idNumber: this.model['idPart1'],
      address: this.model['address'],
      cellphone: this.model['cellphone'],
      bagsCount: num(this.model['bags']),
      grossKg: num(this.model['grossKg']),
      tareKg: num(this.model['tare']),
      totalStoredWeight: num(this.model['totalStoredWeight']),
      defectiveStoredWeight: num(this.model['totalHuskWeight']),
      penalty: num(this.model['penalty']),
      freightDiscount: num(this.model['shrinkageDiscount']),
      otherDiscounts: num(this.model['otherDiscounts']),
      payment: this.payment(),
      ...(this.mode === 'future'
        ? { futurePurchaseId: choice.row.announcement.id }
        : { quotaAnnouncementNumber: Number(stripAnnouncementPrefix(choice.row.announcement.announcementNumber)) }),
      deliveryKg: choice.deliveryKg,
    };
  }

  /** RN-21.2.9: Imprimir guarda una sola vez; el servidor descuenta el saldo en la misma transacción. */
  print(): void {
    const request = this.canPrint() ? this.buildRequest() : null;
    if (!request) {
      return;
    }
    saveAndPrint(this, {
      name: this.mode === 'future' ? 'Compra anunciada' : 'Cafe Seco con cupo',
      save: () => this.service.create(request),
      invoice: buildDryCoffeeInvoiceDocDefinition,
      messages: () => this.messages(),
      refresh: () => this.tick.update((n) => n + 1),
      // El rechazo del cupo (saldo, Esta Entrega) se le muestra al cajero tal como lo explica el servidor.
      saveError: (err) => {
        this.sampleRetry = isOverThreshold(err);
        this.errorMessage.set(err?.error?.message ?? this.messages()?.saveError ?? null);
        this.tick.update((n) => n + 1);
      },
    });
  }

  /** Cierra el aviso. Q15 (prueba del 9-nov): si era el umbral de especiales, vacia la muestra y vuelve a
   *  Peso Tot Alm; no se guarda nada. */
  dismissError(): void {
    this.errorMessage.set(null);
    if (this.sampleRetry) {
      this.sampleRetry = false;
      clearSample(this.model, this.locked);
      this.calc.set(null);
      this.tick.update((n) => n + 1);
      this.focusField('totalStoredWeight');
    }
  }

  private canPrint(): boolean {
    return (
      !!this.payment() && !!this.specialInfo() && !!this.quotaChoice() &&
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
    this.payment.set(null);
    this.quotaRows.set(null);
    this.quotaChoice.set(null);
    this.savedNoticeOpen.set(false);
    this.specialInfo.set(null);
    this.invoiceReservation.set(null);
    this.calc.set(null);
    this.errorMessage.set(null);
    this.fieldCache.clear();
    this.tick.update((n) => n + 1);
  }

  protected override hasUnsavedInput(): boolean {
    return this.editable.some((k) => (this.model[k] ?? '').trim() !== '');
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
      deliveryKg: this.quotaChoice()?.deliveryKg ?? '',
      netKg: c ? c.netKg : netKgLocal,
      kgPrice: c?.unitPrice ?? '',
      grossValue: c?.grossValue ?? '',
      contribution: c ? (growerType === 'C' ? c.cooperativeDiscount : c.associateContribution) : '',
      withholding: c?.withholding ?? '',
      netToPay: c?.netToPay ?? '',
    };

    const field = (key: string): FormFieldDefinition => {
      const b = bmap.get(key)!;
      const patch: { options?: string[]; readonly?: boolean; value?: string | number; label?: string } = {};
      // Prueba del 10-nov: como Café Seco, el no asociado (tipo C) paga Descuento Coop.
      if (key === 'contribution' && growerType === 'C') {
        patch.label = this.messages()?.cooperativeDiscountLabel ?? b.label;
      }
      if (key === 'fund') {
        patch.options = this.fundOptions;
      }
      if (key === 'special' && this.mode === 'quota') {
        patch.options = this.specialOptions();
      }
      if (this.readonlyKeys.includes(key)) {
        patch.readonly = true;
        patch.value = computedValues[key] ?? this.model[key] ?? '';
      } else if (this.locked.has(key)) {
        patch.readonly = true;
        patch.value = this.model[key] ?? '';
      } else if (!isSequentialFieldEnabled(key, this.focusOrder, this.locked)) {
        patch.readonly = true;
      }
      const next: FormFieldDefinition = { ...b, ...patch };
      const prev = this.fieldCache.get(key);
      if (prev && prev.value === next.value && prev.readonly === next.readonly && prev.options === next.options && prev.label === next.label) {
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
      paymentNetToPay: this.calc()?.netToPay ?? null,
      paymentLocked: false,
      reprintButtonLabel: this.canPrint() ? 'Imprimir' : undefined,
    };
  }
}
