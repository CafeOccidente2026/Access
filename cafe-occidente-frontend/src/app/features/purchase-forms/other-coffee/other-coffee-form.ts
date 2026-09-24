import { CommonModule, Location } from '@angular/common';
import { ChangeDetectionStrategy, Component, ElementRef, HostListener, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import pdfMake from 'pdfmake/build/pdfmake';
import pdfFonts from 'pdfmake/build/vfs_fonts';

import { FormFieldDefinition, PurchaseFormContent } from '../../../core/models';
import { Fund } from '../../../core/models/dry-coffee-purchase.model';
import {
  OtherCoffeeNextInvoiceNumber,
  OtherCoffeePurchaseCalculation,
  OtherCoffeePurchaseRequest,
  OtherCoffeePurchaseResponse,
  OtherCoffeeQualityPercentages,
  OtherCoffeeSpecialInfo,
} from '../../../core/models/other-coffee-purchase.model';
import { AuthService } from '../../../core/services/auth.service';
import { ContentService } from '../../../core/services/content.service';
import { GrowerService } from '../../../core/services/grower.service';
import { OtherCoffeePurchaseService } from '../../../core/services/other-coffee-purchase.service';
import { ConfirmDialogComponent, PurchaseFormViewComponent } from '../../../shared/ui';
import { formatDisplayNumber, parseDisplayNumber, stripAnnouncementPrefix } from '../../../shared/utils/number-format';
import { isSequentialFieldEnabled } from '../../../shared/utils/sequential-gate';
import { buildOtherCoffeeInvoiceDocDefinition, loadLogoDataUrl } from './other-coffee-invoice';

/** Valores digitados, indexados por la `key` del campo en purchase-form-other.json. */
type FormModel = Record<string, string>;

interface OtherCoffeeMessages {
  readonly acceptLabel: string;
  readonly noAnnouncement: string;
  readonly deceasedBlocked: string;
  readonly idNumberNotFound: string;
  readonly saveError: string;
  readonly savedNotice: string;
  readonly closeWarning: string;
  readonly closeConfirm: string;
  readonly closeCancel: string;
}

type OtherCoffeeContent = PurchaseFormContent & { readonly messages: OtherCoffeeMessages };

/** Campos editables en el orden de captura del formulario Access (COMPRASESP). "agency" no entra:
 *  la autocompleta la sesión y nunca la toca el usuario (paso 1). */
const EDITABLE: string[] = [
  'fund', 'idPart1', 'fullName', 'idType', 'address', 'cellphone', 'program', 'quota',
  'special',
  'totalStoredWeight', 'totalHuskWeight', 'healthyStoredWeight',
  'bags', 'grossKg', 'tare', 'penalty', 'shrinkageDiscount', 'otherDiscounts',
];

/** Requeridos para habilitar "Imprimir" (bonus/penalty/descuentos pueden quedar en blanco = 0). */
const REQUIRED: string[] = [
  'fund', 'idPart1', 'fullName', 'idType', 'address', 'cellphone',
  'special', 'totalStoredWeight', 'totalHuskWeight', 'healthyStoredWeight', 'bags', 'grossKg', 'tare',
];

/** Si se confirman en blanco quedan en 0 (no bloquean, pero tampoco se ven vacios). */
const ZERO_IF_EMPTY: string[] = ['penalty', 'shrinkageDiscount', 'otherDiscounts'];

/** Campos siempre de solo lectura: los calcula el servidor o los deriva la sesión actual. */
const READONLY: string[] = [
  'agency', 'date', 'announcement', 'announcementDate', 'associated', 'invoicePrefix', 'invoiceNumber',
  'productCode', 'basePriceLoad', 'huskPrice', 'sustentationPrice', 'bonus', 'costs', 'idNumber',
  'wastePercentage', 'huskPercentage', 'factor', 'netKg', 'kgPrice', 'grossValue', 'contribution', 'netToPay',
];

/** Orden en que el foco salta de un campo al siguiente que le toca llenar al usuario. */
const FOCUS_ORDER: string[] = [
  'fund', 'idPart1', 'special',
  'totalStoredWeight', 'totalHuskWeight', 'healthyStoredWeight',
  'bags', 'grossKg', 'tare', 'penalty', 'shrinkageDiscount', 'otherDiscounts',
];

const num = parseDisplayNumber;

/**
 * Compras Cafés Otros (COMPRASESP). Mismo patrón que Compras Café Seco: agencia fija por sesión,
 * factura reservada (mismo rango DIAN compartido con Seco/Verde/Pasilla/Fertifuturo), búsqueda de
 * caficultor, autollenado del anuncio, bloqueo secuencial, Escape para reiniciar, botón Imprimir
 * condicional y guardado únicamente al imprimir. Sin Pr_AlmDefec/var4: la cascada de calidad
 * (Porc Merma/Porc Kg Pas/Factor) sí corre igual que en Seco, pero el precio unitario no usa el
 * defectivo del anuncio (ver OtherCoffeePurchaseCalculator, backend).
 */
@Component({
  selector: 'app-other-coffee-form',
  standalone: true,
  imports: [CommonModule, PurchaseFormViewComponent, ConfirmDialogComponent],
  templateUrl: './other-coffee-form.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OtherCoffeeFormComponent {
  private readonly content = inject(ContentService);
  private readonly service = inject(OtherCoffeePurchaseService);
  private readonly growerService = inject(GrowerService);
  private readonly authService = inject(AuthService);
  private readonly location = inject(Location);
  private readonly elementRef = inject(ElementRef);

  private readonly base = toSignal(this.content.loadJson<OtherCoffeeContent>('purchase-form-other'));
  readonly messages = computed<OtherCoffeeMessages | undefined>(() => this.base()?.messages);
  readonly funds = signal<Fund[]>([]);
  readonly specialInfo = signal<OtherCoffeeSpecialInfo | null>(null);
  readonly invoiceReservation = signal<OtherCoffeeNextInvoiceNumber | null>(null);
  readonly qualityCalc = signal<OtherCoffeeQualityPercentages>({
    wastePercentage: null,
    defectivePercentage: null,
    healthyPercentage: null,
  });
  readonly calc = signal<OtherCoffeePurchaseCalculation | null>(null);
  readonly result = signal<OtherCoffeePurchaseResponse | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly blockedByDeceased = signal(false);
  readonly savedNoticeOpen = signal(false);
  /** Se incrementa cuando hay que reconstruir el contenido (commit, respuesta async, guardado, reset). */
  private readonly tick = signal(0);

  model: FormModel = {};
  private readonly locked = new Set<string>();
  private readonly today = new Date().toISOString().slice(0, 10);
  private readonly fieldCache = new Map<string, FormFieldDefinition>();
  private fundOptions: string[] = [];
  private readonly fundIdByName = new Map<string, number>();
  private logoDataUrlPromise!: Promise<string | null>;

  pendingClose = false;
  private closeResolver: ((value: boolean) => void) | null = null;

  readonly viewContent = computed<PurchaseFormContent | null>(() => {
    const base = this.base();
    this.tick();
    if (!base) {
      return null;
    }
    return this.buildContent(base);
  });

  constructor() {
    this.prefillAgency();
    this.service.funds().subscribe((list) => {
      this.funds.set(list);
      this.fundOptions = list.map((f) => f.code);
      list.forEach((f) => this.fundIdByName.set(f.code, f.id));
      this.tick.update((n) => n + 1);
    });
    this.logoDataUrlPromise = loadLogoDataUrl('assets/images/cafe-occidente-logo.png');
  }

  /** Paso 1: la agencia la trae la sesión autenticada, nunca la elige el usuario. */
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
    // idPart1 se bloquea (locked) recien cuando lookupGrower confirma que la cedula existe - hasta
    // entonces sigue editable para poder corregirla (ver mensaje "no existe" en lookupGrower).
    if (key !== 'idPart1') {
      this.locked.add(key);
    }
    this.runSideEffects(key);
    this.tick.update((n) => n + 1);
    if (key !== 'idPart1') {
      this.advanceFocus(key);
    }
  }

  /** Mueve el foco de teclado al siguiente campo que le toca llenar al usuario, sin que tenga que buscarlo. */
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
      case 'idPart1':
        this.lookupGrower();
        break;
      case 'special':
        this.loadSpecialInfo();
        this.lookupProgram();
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

  /** Siguiente factura del rango DIAN autorizado (compartido con los otros 4 módulos), al confirmar Fondo. */
  private reserveInvoiceNumber(): void {
    this.service.nextInvoiceNumber().subscribe({
      next: (inv) => {
        this.invoiceReservation.set(inv);
        if (inv.warning) {
          this.errorMessage.set(inv.warning);
        }
        this.tick.update((n) => n + 1);
      },
      error: () => {
        this.errorMessage.set(this.messages()?.saveError ?? null);
        this.tick.update((n) => n + 1);
      },
    });
  }

  /** Busca el caficultor por cédula; bloquea el formulario si está fallecido. */
  private lookupGrower(): void {
    const idNumber = (this.model['idPart1'] ?? '').trim();
    if (!idNumber) {
      return;
    }
    this.growerService.findByIdNumber(idNumber).subscribe({
      next: (grower) => {
        if (grower.deceased) {
          this.blockedByDeceased.set(true);
          this.errorMessage.set(this.messages()?.deceasedBlocked ?? null);
          this.tick.update((n) => n + 1);
          return;
        }
        const fullName = [grower.firstName, grower.secondName, grower.lastName, grower.secondLastName]
          .filter(Boolean)
          .join(' ');
        this.model['fullName'] = fullName;
        this.model['idType'] = grower.growerType;
        this.model['address'] = grower.address;
        this.model['cellphone'] = grower.phone;
        this.locked.add('idPart1');
        // Mismo fix aplicado hoy en Seco/Verde/Pasilla/Cupos: si el dato migrado viene vacio (p.ej.
        // caficultores historicos sin celular registrado), no se bloquea - si no, el campo queda en
        // blanco y bloqueado para siempre, y como esta en REQUIRED la cascada de calculo nunca
        // llegaba a dispararse.
        ['fullName', 'idType', 'address', 'cellphone'].forEach((k) => {
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
        this.errorMessage.set(this.messages()?.idNumberNotFound ?? 'Este número de cédula no existe.');
        this.tick.update((n) => n + 1);
      },
    });
  }

  /** Programa/Cupo (staging_legacy_ness) - puramente informativo, nunca bloquea. */
  private lookupProgram(): void {
    const idNumber = (this.model['idPart1'] ?? '').trim();
    if (!idNumber) {
      return;
    }
    this.growerService.findProgram(idNumber, this.model['special']).subscribe((program) => {
      if (program) {
        this.model['program'] = program.programa;
        this.model['quota'] = program.cupo;
        this.locked.add('program');
        this.locked.add('quota');
      } else {
        this.model['program'] = '';
        this.model['quota'] = '';
        this.locked.delete('program');
        this.locked.delete('quota');
      }
      this.tick.update((n) => n + 1);
    });
  }

  /** Cod Prod + datos del anuncio vigente para Agencia+Fondo+Especial. */
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
        this.errorMessage.set(this.messages()?.noAnnouncement ?? null);
        this.tick.update((n) => n + 1);
      },
    });
  }

  /** Cada peso calcula su propio porcentaje, independiente de los otros dos. */
  private loadQualityPercentage(key: 'totalStoredWeight' | 'totalHuskWeight' | 'healthyStoredWeight'): void {
    const value = num(this.model[key]);
    const args: [number?, number?, number?] =
      key === 'totalStoredWeight'
        ? [value, undefined, undefined]
        : key === 'totalHuskWeight'
          ? [undefined, value, undefined]
          : [undefined, undefined, value];
    this.service.qualityPercentages(...args).subscribe({
      next: (q) => {
        this.qualityCalc.update((prev) => ({
          wastePercentage: q.wastePercentage ?? prev.wastePercentage,
          defectivePercentage: q.defectivePercentage ?? prev.defectivePercentage,
          healthyPercentage: q.healthyPercentage ?? prev.healthyPercentage,
        }));
        this.tick.update((n) => n + 1);
      },
      error: () => {
        this.qualityCalc.update((prev) => ({
          ...prev,
          [key === 'totalStoredWeight'
            ? 'wastePercentage'
            : key === 'totalHuskWeight'
              ? 'defectivePercentage'
              : 'healthyPercentage']: null,
        }));
        this.errorMessage.set(this.messages()?.saveError ?? null);
        this.tick.update((n) => n + 1);
      },
    });
  }

  /** Extrae el mensaje real del backend (BusinessRuleException: cupo excedido, valor negativo,
   *  fallecido) en vez de taparlo con un mensaje generico - esas validaciones viven solo en el
   *  backend, el frontend no las repite, pero sí le debe mostrar al cajero por qué se rechazó. */
  private backendErrorMessage(err: unknown): string | null {
    const message = (err as { error?: { message?: string } })?.error?.message;
    return message ?? this.messages()?.saveError ?? null;
  }

  /** Recalcula la cascada completa (Castigo / Descuento Fro / Otros Desctos). */
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
        this.errorMessage.set(this.backendErrorMessage(err));
        this.tick.update((n) => n + 1);
      },
    });
  }

  private buildRequest(): OtherCoffeePurchaseRequest | null {
    const agencyId = this.authService.agencyId();
    const fundId = this.fundIdByName.get(this.model['fund'] ?? '');
    const invoiceNumber = this.invoiceReservation()?.invoiceNumber;
    const hasRequiredCascadeInputs = REQUIRED.every((k) => (this.model[k] ?? '').trim() !== '');
    if (!agencyId || !fundId || !invoiceNumber || !hasRequiredCascadeInputs) {
      return null;
    }
    const [firstName, lastName] = this.splitName(this.model['fullName'] ?? '');
    return {
      agencyId,
      fundId,
      invoiceNumber,
      specialType: this.model['special'],
      idNumber: this.model['idPart1'],
      firstName,
      lastName,
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

  print(): void {
    if (!this.canPrint()) {
      return;
    }
    const request = this.buildRequest();
    if (!request) {
      return;
    }
    this.errorMessage.set(null);
    this.service.create(request).subscribe({
      next: (res) => {
        this.result.set(res);
        this.savedNoticeOpen.set(true);
        this.printInvoice(res);
        this.tick.update((n) => n + 1);
      },
      error: (err) => {
        console.error('Error al registrar compra Cafés Otros:', err?.error ?? err);
        this.errorMessage.set(this.backendErrorMessage(err));
        this.tick.update((n) => n + 1);
      },
    });
  }

  /** Cierra el mensaje de error; si bloqueaba por caficultor fallecido, reinicia todo el formulario. */
  dismissError(): void {
    const wasBlocked = this.blockedByDeceased();
    this.errorMessage.set(null);
    this.blockedByDeceased.set(false);
    if (wasBlocked) {
      this.reset();
    }
  }

  /** Escape reinicia todo el formulario en blanco y editable (única forma de "volver atrás"). */
  @HostListener('document:keydown.escape')
  reset(): void {
    this.model = {};
    this.locked.clear();
    this.specialInfo.set(null);
    this.invoiceReservation.set(null);
    this.qualityCalc.set({ wastePercentage: null, defectivePercentage: null, healthyPercentage: null });
    this.calc.set(null);
    this.result.set(null);
    this.errorMessage.set(null);
    this.blockedByDeceased.set(false);
    this.savedNoticeOpen.set(false);
    this.fieldCache.clear();
    this.prefillAgency();
    this.tick.update((n) => n + 1);
  }

  private hasUnsavedInput(): boolean {
    return !this.result() && EDITABLE.some((k) => (this.model[k] ?? '').trim() !== '');
  }

  /** La X del formulario: si hay datos sin imprimir pide confirmación; si no, cierra. */
  onClose(): void {
    if (this.hasUnsavedInput()) {
      this.pendingClose = true;
    } else {
      this.location.back();
    }
  }

  /** Guard CanDeactivate: cubre las salidas que no pasan por la X (menú, back, etc.). */
  canDeactivate(): boolean | Promise<boolean> {
    if (!this.hasUnsavedInput()) {
      return true;
    }
    this.pendingClose = true;
    return new Promise<boolean>((resolve) => (this.closeResolver = resolve));
  }

  confirmClose(): void {
    this.pendingClose = false;
    if (this.closeResolver) {
      this.closeResolver(true);
      this.closeResolver = null;
      return;
    }
    this.model = {};
    this.location.back();
  }

  cancelClose(): void {
    this.pendingClose = false;
    this.closeResolver?.(false);
    this.closeResolver = null;
  }

  private canPrint(): boolean {
    return (
      !this.result() &&
      !!this.authService.agencyId() &&
      !!this.specialInfo() &&
      !!this.invoiceReservation() &&
      !!this.calc() &&
      this.locked.has('otherDiscounts') &&
      REQUIRED.every((k) => (this.model[k] ?? '').trim() !== '')
    );
  }

  private splitName(full: string): [string, string] {
    const parts = full.trim().split(/\s+/).filter(Boolean);
    if (parts.length === 0) {
      return ['', ''];
    }
    if (parts.length === 1) {
      return [parts[0], parts[0]];
    }
    const mid = Math.ceil(parts.length / 2);
    return [parts.slice(0, mid).join(' '), parts.slice(mid).join(' ')];
  }

  private buildContent(base: OtherCoffeeContent): PurchaseFormContent {
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
    collect(base.additionalDiscountFields);
    if (base.discountField) {
      bmap.set(base.discountField.key, base.discountField);
    }
    if (base.netToPayField) {
      bmap.set(base.netToPayField.key, base.netToPayField);
    }

    const info = this.specialInfo();
    const inv = this.invoiceReservation();
    const q = this.qualityCalc();
    const c = this.calc();
    const growerType = (this.model['idType'] ?? '').trim().toUpperCase();
    const netKgLocal =
      this.model['grossKg'] && this.model['tare']
        ? num(this.model['grossKg']) - num(this.model['tare'])
        : '';

    const computedValues: Record<string, string | number> = {
      agency: this.authService.agencyName() ?? '',
      date: this.today,
      announcement: info?.announcementNumber ? stripAnnouncementPrefix(info.announcementNumber) : '',
      announcementDate: info?.announcementDate ?? '',
      associated: growerType === 'S' ? 'ASOCIADO' : growerType === 'C' ? 'NO ASOCIADO' : '',
      invoicePrefix: inv?.prefix ?? '',
      invoiceNumber: inv?.invoiceNumber ?? '',
      productCode: info?.productCode ?? '',
      basePriceLoad: info?.basePriceLoad ?? '',
      huskPrice: info?.defectiveUnitPrice ?? '',
      sustentationPrice: info?.healthyUnitPrice ?? '',
      bonus: info?.bonus ?? '',
      costs: info?.costs ?? '',
      idNumber: this.model['idPart1'] ?? '',
      wastePercentage: q.wastePercentage ?? '',
      huskPercentage: q.defectivePercentage ?? '',
      factor: q.healthyPercentage ?? '',
      netKg: c ? c.netKg : netKgLocal,
      kgPrice: c?.unitPrice ?? '',
      grossValue: c?.grossValue ?? '',
      contribution: c ? (growerType === 'C' ? c.cooperativeDiscount : c.associateContribution) : '',
      netToPay: c?.netToPay ?? '',
    };

    const field = (key: string): FormFieldDefinition => {
      const b = bmap.get(key)!;
      const patch: { options?: string[]; readonly?: boolean; value?: string | number; label?: string } = {};
      if (key === 'fund') {
        patch.options = this.fundOptions;
      }
      if (key === 'contribution') {
        patch.label = growerType === 'C' ? 'Descuento Coop' : 'Aporte Socio';
      }
      if (READONLY.includes(key)) {
        patch.readonly = true;
        patch.value = computedValues[key];
      } else if (this.locked.has(key)) {
        patch.readonly = true;
        patch.value = this.model[key] ?? '';
      } else if (!isSequentialFieldEnabled(key, FOCUS_ORDER, this.locked)) {
        patch.readonly = true;
      }
      const next: FormFieldDefinition = { ...b, ...patch };
      const prev = this.fieldCache.get(key);
      if (
        prev &&
        prev.value === next.value &&
        prev.readonly === next.readonly &&
        prev.options === next.options &&
        prev.label === next.label
      ) {
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
      settlementFields: row(base.settlementFields)!,
      additionalDiscountFields: row(base.additionalDiscountFields),
      netToPayField: base.netToPayField ? field(base.netToPayField.key) : undefined,
      discountField: base.discountField ? field(base.discountField.key) : undefined,
      paymentPanel: base.paymentPanel
        ? {
            ...base.paymentPanel,
            methods: base.paymentPanel.methods.map((m, i) =>
              i === 0 ? { ...m, value: c ? formatDisplayNumber(c.netToPay, 'currency') : '' } : m,
            ),
            totalValue: c ? formatDisplayNumber(c.netToPay, 'currency') : '',
          }
        : undefined,
      reprintButtonLabel: this.canPrint() ? 'Imprimir' : undefined,
    };
  }

  /** Genera el Documento Soporte en PDF (mismo layout que Café Seco, título/Especial propios de Otros). */
  private printInvoice(purchase: OtherCoffeePurchaseResponse): void {
    this.logoDataUrlPromise.then((logoDataUrl) => {
      const docDefinition = buildOtherCoffeeInvoiceDocDefinition(purchase, logoDataUrl);
      pdfMake.vfs = pdfFonts;
      pdfMake.createPdf(docDefinition).open();
    });
  }
}
