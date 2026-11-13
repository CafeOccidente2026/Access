import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, ElementRef, HostListener, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';

import { FormFieldDefinition, PurchaseFormContent, PurchasePayment } from '../../../core/models';
import {
  GreenAnnouncementInfo,
  GreenCoffeePurchaseCalculation,
  GreenCoffeePurchaseRequest,
  GreenNextInvoiceNumber,
} from '../../../core/models/green-coffee-purchase.model';
import { AuthService } from '../../../core/services/auth.service';
import { ContentService } from '../../../core/services/content.service';
import { GreenCoffeePurchaseService } from '../../../core/services/green-coffee-purchase.service';
import { GrowerService } from '../../../core/services/grower.service';
import { ConfirmDialogComponent, PurchaseFormViewComponent } from '../../../shared/ui';
import { parseDisplayNumber, stripAnnouncementPrefix } from '../../../shared/utils/number-format';
import { isSequentialFieldEnabled } from '../../../shared/utils/sequential-gate';
import { buildGreenCoffeeInvoiceDocDefinition } from './green-coffee-invoice';
import { hasRequired, saveAndPrint } from '../purchase-print';
import { lookupSeller } from '../seller-lookup';
import { advanceFocus, focusField } from '../form-focus';
import { askCellphoneOr } from '../seller-cellphone';
import { UnsavedCloseForm } from '../unsaved-close';
import { serverError } from '../../../shared/utils/server-error';
import { reserveInvoice } from '../invoice-reservation';
import { FEDERATION_KEYS, loadFederation } from '../federation';
import { localToday } from '../../../shared/utils/local-date';

/** Valores digitados, indexados por la `key` del campo en purchase-form-green.json. */
type FormModel = Record<string, string>;

interface GreenCoffeeMessages {
  readonly acceptLabel: string;
  readonly noAnnouncement: string;
  readonly deceasedBlocked: string;
  readonly sampleMissing: string;
  readonly idNumberNotFound: string;
  readonly saveError: string;
  readonly savedNotice: string;
  readonly invoiceError: string;
  readonly closeWarning: string;
  readonly closeConfirm: string;
  readonly closeCancel: string;
}

type GreenCoffeeContent = PurchaseFormContent & { readonly messages: GreenCoffeeMessages };

/**
 * Especial ("CV") y Fondo ("RP") son fijos en VERDES (sin AfterUpdate/combo real - ver
 * Form_VERDES.bas y VERDES.txt), a diferencia de Cafe Seco. El bloque Informacion Federacion sale de
 * NESS (C3) y es de solo lectura.
 */
const EDITABLE: string[] = [
  'idPart1', 'firstName', 'lastName', 'idType', 'address', 'cellphone',
  'bags', 'grossKg', 'tare', 'compKgPrice', 'penalty', 'shrinkageDiscount', 'otherDiscounts',
];

/** Requeridos para habilitar "Imprimir" (penalty/descuentos pueden quedar en blanco = 0). */
// El servidor no calcula sin el celular del vendedor (prueba del 10-nov): sin el no se pide el calculo.
const REQUIRED: string[] = ['idPart1', 'cellphone', 'bags', 'grossKg', 'tare', 'compKgPrice'];

/** Si se confirman en blanco quedan en 0 (no bloquean, pero tampoco se ven vacios). */
const ZERO_IF_EMPTY: string[] = ['penalty', 'shrinkageDiscount', 'otherDiscounts'];

const READONLY: string[] = [
  'agency', 'date', 'announcement', 'announcementDate', 'productCode', 'fund', 'invoicePrefix',
  'invoiceNumber', 'basePriceLoad', 'special', 'healthyStoredPrice', 'defectStoredPrice',
  'bonus', 'costs', 'greenKg', 'netKg', 'kgPrice', 'withholding',
  // C3: el bloque Informacion Federacion lo llena NESS (lookupProgram).
  ...FEDERATION_KEYS,
  // HU-23 CA-2: muestra fija del Registro de Control.
  'totalStoredWeight', 'wastePercentage', 'totalHuskWeight', 'huskPercentage', 'healthyStoredWeight', 'factor',
];

/** Orden en que el foco salta de un campo al siguiente que le toca llenar al usuario. */
const FOCUS_ORDER: string[] = [
  'idPart1', 'bags', 'grossKg', 'tare', 'compKgPrice', 'penalty', 'shrinkageDiscount', 'otherDiscounts', 'payCash',
];

const num = parseDisplayNumber;

/**
 * Compras Cafe Verde (VERDES). Mismo patron que Compras Cafe Seco (dry-coffee-form): agencia fija
 * por sesion, factura/anuncio/Cod_Prod se resuelven al iniciar (Especial/Fondo fijos - ver arriba),
 * busqueda de caficultor, bloqueo secuencial, Escape para reiniciar, guardado solo al imprimir.
 * La cascada de calculo corre en el servidor (GreenCoffeePurchaseCalculator).
 */
@Component({
  selector: 'app-green-coffee-form',
  standalone: true,
  imports: [CommonModule, PurchaseFormViewComponent, ConfirmDialogComponent],
  templateUrl: '../purchase-form.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GreenCoffeeFormComponent extends UnsavedCloseForm {
  private readonly content = inject(ContentService);
  private readonly service = inject(GreenCoffeePurchaseService);
  private readonly growerService = inject(GrowerService);
  private readonly authService = inject(AuthService);
  private readonly elementRef = inject(ElementRef);

  private readonly base = toSignal(this.content.loadJson<GreenCoffeeContent>('purchase-form-green'));
  readonly messages = computed<GreenCoffeeMessages | undefined>(() => this.base()?.messages);
  readonly announcementInfo = signal<GreenAnnouncementInfo | null>(null);
  /** true mientras se guarda: un segundo Imprimir no vuelve a enviar (una sola factura). */
  readonly saving = signal(false);
  readonly invoiceReservation = signal<GreenNextInvoiceNumber | null>(null);
  readonly calc = signal<GreenCoffeePurchaseCalculation | null>(null);
  /** FORMAS DE PAGO cuadrado (cascada FPef/FPch/FPtx/FPdat); null mientras no cuadra. */
  readonly payment = signal<PurchasePayment | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly blockedByDeceased = signal(false);
  /** Q23: el Registro de Control no tiene los pesos de verde; la pantalla no deja digitar. */
  readonly sampleMissing = signal(false);
  readonly savedNoticeOpen = signal(false);
  private readonly tick = signal(0);

  override model: FormModel = {};
  private readonly locked = new Set<string>();
  private readonly today = localToday();
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
    super();
    this.prefillAgency();
    this.reserveInvoiceNumber();
    this.loadAnnouncementInfo();
    // Foco en el primer campo apenas carga el contenido - ver mismo fix en dry-coffee-form.ts.
    effect(() => {
      if (this.base()) {
        this.focusField(FOCUS_ORDER[0]);
      }
    });
  }

  private prefillAgency(): void {
    const agencyName = this.authService.agencyName();
    if (agencyName) {
      this.model['agency'] = agencyName;
      this.locked.add('agency');
    }
  }

  onFieldValueChange(event: { key: string; value: string | number }): void {
    this.model[event.key] = String(event.value);
  }

  onFieldCommitted(key: string): void {
    if (this.sampleMissing() || !EDITABLE.includes(key)) {
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



  private runSideEffects(key: string): void {
    switch (key) {
      case 'idPart1':
        this.lookupGrower();
        break;
      case 'cellphone':
      case 'grossKg':
      case 'tare':
      case 'compKgPrice':
      case 'penalty':
      case 'shrinkageDiscount':
      case 'otherDiscounts':
        this.recalculate();
        break;
    }
  }

  /** "Para asignar # factura verdes": siguiente factura propia de VERDES. */
  private reserveInvoiceNumber(): void {
    reserveInvoice(this, this.service.nextInvoiceNumber(), () => this.tick.update((n) => n + 1));
  }

  /** "Asignar numero anuncio verdes": Cod Prod + anuncio vigente (Especial "CV" / Fondo "RP" fijos). */
  private loadAnnouncementInfo(): void {
    this.service.announcementInfo().subscribe({
      next: (info) => {
        // HU-23 CA-2 / Q23: sin los pesos de verde no hay cálculo ni factura (sin info no hay preview
        // ni Imprimir); se avisa apenas abre la pantalla.
        this.announcementInfo.set(info.sampleReady ? info : null);
        this.sampleMissing.set(!info.sampleReady);
        if (!info.sampleReady) {
          this.errorMessage.set(this.messages()?.sampleMissing ?? null);
        }
        this.tick.update((n) => n + 1);
      },
      error: (err) => {
        this.announcementInfo.set(null);
        this.errorMessage.set(serverError(err, this.messages()?.noAnnouncement));
        this.tick.update((n) => n + 1);
      },
    });
  }

  /** Cedula_AfterUpdate: busca el caficultor; bloquea el formulario si esta fallecido. */
  private lookupGrower(): void {
    lookupSeller(this, this.growerService, this.locked, () => this.tick.update((n) => n + 1), () => {
      this.lookupProgram();
      askCellphoneOr(this.elementRef.nativeElement, this, () => this.advanceFocus('idPart1'));
    });
  }

  /** C3: el bloque Informacion Federacion sale de NESS (especial CV); sin NESS queda vacio. */
  private lookupProgram(): void {
    loadFederation(this.growerService, this.model['idPart1'], 'CV', this.model, () => this.tick.update((n) => n + 1));
  }

  /** Destare_LostFocus / Castigo_lostFocus / Descuento_Fro_LostFocus / OtrosDescuentos: recalcula la cascada. */
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
      error: (err) => {
        this.calc.set(null);
        this.errorMessage.set(serverError(err, this.messages()?.saveError));
        this.tick.update((n) => n + 1);
      },
    });
  }

  private buildRequest(): GreenCoffeePurchaseRequest | null {
    const info = this.announcementInfo();
    const invoiceNumber = this.invoiceReservation()?.invoiceNumber;
    if (!invoiceNumber || !info || !hasRequired(this.model, REQUIRED)) {
      return null;
    }
    return {
      fundId: info.fundId,
      idNumber: this.model['idPart1'],
      address: this.model['address'] ?? '',
      cellphone: this.model['cellphone'] ?? '',
      bagsCount: num(this.model['bags']),
      grossKg: num(this.model['grossKg']),
      tareKg: num(this.model['tare']),
      penalty: num(this.model['penalty']),
      compKgPrice: num(this.model['compKgPrice']),
      shrinkageDiscount: num(this.model['shrinkageDiscount']),
      otherDiscounts: num(this.model['otherDiscounts']),
      payment: this.payment(),
    };
  }

  print(): void {
    if (!this.canPrint()) {
      return;
    }
    const request = this.buildRequest();
    if (!request) {
      return;
    }
    saveAndPrint(this, {
      name: 'Cafe Verde',
      save: () => this.service.create(request),
      invoice: buildGreenCoffeeInvoiceDocDefinition,
      messages: () => this.messages(),
      refresh: () => this.tick.update((n) => n + 1),
    });
  }

  dismissError(): void {
    const wasBlocked = this.blockedByDeceased();
    this.errorMessage.set(null);
    this.blockedByDeceased.set(false);
    if (wasBlocked) {
      this.reset();
    }
  }

  @HostListener('document:keydown.escape')
  reset(): void {
    this.payment.set(null);
    this.model = {};
    this.locked.clear();
    this.invoiceReservation.set(null);
    this.calc.set(null);
    this.errorMessage.set(null);
    this.blockedByDeceased.set(false);
    this.savedNoticeOpen.set(false);
    this.fieldCache.clear();
    this.prefillAgency();
    this.reserveInvoiceNumber();
    this.loadAnnouncementInfo();
    this.tick.update((n) => n + 1);
    this.focusField(FOCUS_ORDER[0]);
  }

  protected override hasUnsavedInput(): boolean {
    return EDITABLE.some((k) => (this.model[k] ?? '').trim() !== '');
  }


  private canPrint(): boolean {
    return (
      !!this.payment() &&
      !!this.announcementInfo() &&
      !!this.invoiceReservation() &&
      !!this.calc() &&
      this.locked.has('otherDiscounts') &&
      REQUIRED.every((k) => (this.model[k] ?? '').trim() !== '')
    );
  }

  private buildContent(base: GreenCoffeeContent): PurchaseFormContent {
    const bmap = new Map<string, FormFieldDefinition>();
    const collect = (fields?: FormFieldDefinition[]) => fields?.forEach((f) => bmap.set(f.key, f));
    collect(base.topFields);
    collect(base.identificationFields);
    collect(base.federationFields);
    collect(base.contactFields);
    collect(base.qualityFields);
    collect(base.weightFields);
    collect(base.netWeightFields);
    collect(base.priceFields);
    collect(base.settlementFields);
    if (base.discountField) {
      bmap.set(base.discountField.key, base.discountField);
    }

    const info = this.announcementInfo();
    const inv = this.invoiceReservation();
    const c = this.calc();

    const computedValues: Record<string, string | number> = {
      agency: this.authService.agencyName() ?? '',
      date: this.today,
      announcement: info?.announcementNumber ? stripAnnouncementPrefix(info.announcementNumber) : '',
      announcementDate: info?.announcementDate ?? '',
      productCode: info?.productCode ?? '',
      fund: 'RP',
      invoicePrefix: inv?.prefix ?? '',
      invoiceNumber: inv?.invoiceNumber ?? '',
      basePriceLoad: c ? c.basePriceLoad : (info?.basePriceLoad ?? ''),
      special: 'CV',
      healthyStoredPrice: info?.healthyUnitPrice ?? '',
      defectStoredPrice: info?.defectiveUnitPrice ?? '',
      bonus: info?.bonus ?? '',
      costs: info?.costs ?? '',
      totalStoredWeight: info?.sample?.totalStoredWeight ?? '',
      wastePercentage: info?.sample?.wastePercentage ?? '',
      totalHuskWeight: info?.sample?.huskWeight ?? '',
      huskPercentage: info?.sample?.huskPercentage ?? '',
      healthyStoredWeight: info?.sample?.healthyStoredWeight ?? '',
      factor: info?.sample?.factor ?? '',
      greenKg: c ? c.greenKg : '',
      netKg: c ? c.netKg : '',
      kgPrice: c?.unitPrice ?? '',
      withholding: c?.withholding ?? '',
    };

    const field = (key: string): FormFieldDefinition => {
      const b = bmap.get(key)!;
      const patch: { readonly?: boolean; value?: string | number } = {};
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
      identificationFields: row(base.identificationFields)!,
      federationFields: row(base.federationFields),
      contactFields: row(base.contactFields),
      qualityFields: row(base.qualityFields),
      weightFields: row(base.weightFields),
      netWeightFields: row(base.netWeightFields),
      priceFields: row(base.priceFields),
      settlementFields: row(base.settlementFields)!,
      discountField: base.discountField ? field(base.discountField.key) : undefined,
      paymentNetToPay: this.calc()?.netToPay ?? null,
      reprintButtonLabel: this.canPrint() ? 'Imprimir' : undefined,
    };
  }

  /** Salto de foco del formulario (form-focus): mismo orden de siempre. */
  private readonly focusField = (key: string) => focusField(this.elementRef.nativeElement, key);
  private readonly advanceFocus = (afterKey: string) => advanceFocus(this.elementRef.nativeElement, FOCUS_ORDER, afterKey);
}
