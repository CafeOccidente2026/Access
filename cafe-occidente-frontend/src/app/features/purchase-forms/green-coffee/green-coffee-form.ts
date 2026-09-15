import { CommonModule, Location } from '@angular/common';
import { ChangeDetectionStrategy, Component, ElementRef, HostListener, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';

import { FormFieldDefinition, PurchaseFormContent } from '../../../core/models';
import {
  GreenAnnouncementInfo,
  GreenCoffeePurchaseCalculation,
  GreenCoffeePurchaseRequest,
  GreenCoffeePurchaseResponse,
  GreenNextInvoiceNumber,
} from '../../../core/models/green-coffee-purchase.model';
import { AuthService } from '../../../core/services/auth.service';
import { ContentService } from '../../../core/services/content.service';
import { GreenCoffeePurchaseService } from '../../../core/services/green-coffee-purchase.service';
import { GrowerService } from '../../../core/services/grower.service';
import { ConfirmDialogComponent, PurchaseFormViewComponent } from '../../../shared/ui';

/** Valores digitados, indexados por la `key` del campo en purchase-form-green.json. */
type FormModel = Record<string, string>;

interface GreenCoffeeMessages {
  readonly acceptLabel: string;
  readonly noAnnouncement: string;
  readonly deceasedBlocked: string;
  readonly saveError: string;
  readonly savedNotice: string;
  readonly closeWarning: string;
  readonly closeConfirm: string;
  readonly closeCancel: string;
}

type GreenCoffeeContent = PurchaseFormContent & { readonly messages: GreenCoffeeMessages };

/**
 * Especial ("CV") y Fondo ("RP") son fijos en VERDES (sin AfterUpdate/combo real - ver
 * Form_VERDES.bas y VERDES.txt), a diferencia de Cafe Seco. "program" (Programa Federacion) se deja
 * siempre editable sin autocompletado: no hay tabla de programa migrada para VERDES (a diferencia de
 * staging_legacy_ness en Cafe Seco).
 */
const EDITABLE: string[] = [
  'idNumber', 'fullName', 'program',
  'bags', 'grossKg', 'tare', 'compKgPrice', 'penalty', 'shrinkageDiscount', 'otherDiscounts',
];

/** Requeridos para habilitar "Imprimir" (penalty/descuentos pueden quedar en blanco = 0). */
const REQUIRED: string[] = ['idNumber', 'fullName', 'bags', 'grossKg', 'tare', 'compKgPrice'];

const READONLY: string[] = [
  'agency', 'date', 'announcement', 'announcementDate', 'productCode', 'fund', 'invoice',
  'basePriceLoad', 'special', 'idPart1', 'healthyStoredPrice', 'defectStoredPrice', 'bonus', 'costs',
  'greenKg', 'netKg', 'kgPrice', 'withholding',
];

/** Orden en que el foco salta de un campo al siguiente que le toca llenar al usuario. */
const FOCUS_ORDER: string[] = [
  'idNumber', 'bags', 'grossKg', 'tare', 'compKgPrice', 'penalty', 'shrinkageDiscount', 'otherDiscounts',
];

const num = (v: string | undefined | null): number => {
  const s = (v ?? '').trim();
  return s === '' ? 0 : Number(s);
};

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
  templateUrl: './green-coffee-form.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GreenCoffeeFormComponent {
  private readonly content = inject(ContentService);
  private readonly service = inject(GreenCoffeePurchaseService);
  private readonly growerService = inject(GrowerService);
  private readonly authService = inject(AuthService);
  private readonly location = inject(Location);
  private readonly elementRef = inject(ElementRef);

  private readonly base = toSignal(this.content.loadJson<GreenCoffeeContent>('purchase-form-green'));
  readonly messages = computed<GreenCoffeeMessages | undefined>(() => this.base()?.messages);
  readonly announcementInfo = signal<GreenAnnouncementInfo | null>(null);
  readonly invoiceReservation = signal<GreenNextInvoiceNumber | null>(null);
  readonly calc = signal<GreenCoffeePurchaseCalculation | null>(null);
  readonly result = signal<GreenCoffeePurchaseResponse | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly blockedByDeceased = signal(false);
  readonly savedNoticeOpen = signal(false);
  private readonly tick = signal(0);

  model: FormModel = {};
  private readonly locked = new Set<string>();
  private readonly today = new Date().toISOString().slice(0, 10);
  private readonly fieldCache = new Map<string, FormFieldDefinition>();

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
    this.reserveInvoiceNumber();
    this.loadAnnouncementInfo();
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
    if (key !== 'idNumber') {
      this.advanceFocus(key);
    }
  }

  private focusField(key: string): void {
    setTimeout(() => {
      const el = this.elementRef.nativeElement.querySelector(`[data-field-key="${key}"]`) as HTMLElement | null;
      el?.focus();
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
      case 'idNumber':
        this.lookupGrower();
        break;
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

  /** "Asignar numero anuncio verdes": Cod Prod + anuncio vigente (Especial "CV" / Fondo "RP" fijos). */
  private loadAnnouncementInfo(): void {
    this.service.announcementInfo().subscribe({
      next: (info) => {
        this.announcementInfo.set(info);
        this.tick.update((n) => n + 1);
      },
      error: () => {
        this.errorMessage.set(this.messages()?.noAnnouncement ?? null);
        this.tick.update((n) => n + 1);
      },
    });
  }

  /** Cedula_AfterUpdate: busca el caficultor; bloquea el formulario si esta fallecido. */
  private lookupGrower(): void {
    const idNumber = (this.model['idNumber'] ?? '').trim();
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
        this.locked.add('fullName');
        this.tick.update((n) => n + 1);
        this.advanceFocus('idNumber');
      },
      // No encontrado: se deja en blanco y editable para captura manual.
      error: () => this.advanceFocus('idNumber'),
    });
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
      error: () => {
        this.errorMessage.set(this.messages()?.saveError ?? null);
        this.tick.update((n) => n + 1);
      },
    });
  }

  private buildRequest(): GreenCoffeePurchaseRequest | null {
    const agencyId = this.authService.agencyId();
    const info = this.announcementInfo();
    const invoiceNumber = this.invoiceReservation()?.invoiceNumber;
    if (!agencyId || !invoiceNumber || !info) {
      return null;
    }
    const [firstName, lastName] = this.splitName(this.model['fullName'] ?? '');
    return {
      agencyId,
      fundId: info.fundId,
      invoiceNumber,
      idNumber: this.model['idNumber'],
      firstName,
      lastName,
      growerType: (this.model['idType'] ?? '').trim().toUpperCase(),
      address: this.model['address'] ?? '',
      cellphone: this.model['cellphone'] ?? '',
      bagsCount: num(this.model['bags']),
      grossKg: num(this.model['grossKg']),
      tareKg: num(this.model['tare']),
      healthyUnitPrice: info.healthyUnitPrice,
      bonus: info.bonus,
      costs: info.costs,
      penalty: num(this.model['penalty']),
      compKgPrice: num(this.model['compKgPrice']),
      withholdingExempt: false,
      shrinkageDiscount: num(this.model['shrinkageDiscount']),
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
        this.tick.update((n) => n + 1);
      },
      error: () => {
        this.errorMessage.set(this.messages()?.saveError ?? null);
        this.tick.update((n) => n + 1);
      },
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
    this.model = {};
    this.locked.clear();
    this.invoiceReservation.set(null);
    this.calc.set(null);
    this.result.set(null);
    this.errorMessage.set(null);
    this.blockedByDeceased.set(false);
    this.savedNoticeOpen.set(false);
    this.fieldCache.clear();
    this.prefillAgency();
    this.reserveInvoiceNumber();
    this.loadAnnouncementInfo();
    this.tick.update((n) => n + 1);
  }

  private hasUnsavedInput(): boolean {
    return !this.result() && EDITABLE.some((k) => (this.model[k] ?? '').trim() !== '');
  }

  onClose(): void {
    if (this.hasUnsavedInput()) {
      this.pendingClose = true;
    } else {
      this.location.back();
    }
  }

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
    } else {
      this.location.back();
    }
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
      !!this.announcementInfo() &&
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
    collect(base.settlementSecondaryFields);
    if (base.discountField) {
      bmap.set(base.discountField.key, base.discountField);
    }

    const info = this.announcementInfo();
    const inv = this.invoiceReservation();
    const c = this.calc();

    const computedValues: Record<string, string | number> = {
      agency: this.authService.agencyName() ?? '',
      date: this.today,
      announcement: info?.announcementNumber ?? '',
      announcementDate: info?.announcementDate ?? '',
      productCode: info?.productCode ?? '',
      fund: 'RP',
      invoice: inv ? `${inv.prefix}${inv.invoiceNumber}` : '',
      basePriceLoad: c ? c.basePriceLoad : (info?.basePriceLoad ?? ''),
      special: 'CV',
      healthyStoredPrice: info?.healthyUnitPrice ?? '',
      defectStoredPrice: info?.defectiveUnitPrice ?? '',
      bonus: info?.bonus ?? '',
      costs: info?.costs ?? '',
      idPart1: this.model['idNumber'] ?? '',
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
        patch.value = computedValues[key];
      } else if (this.locked.has(key)) {
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
      identificationFields: row(base.identificationFields)!,
      federationFields: row(base.federationFields),
      contactFields: row(base.contactFields),
      qualityFields: row(base.qualityFields),
      weightFields: base.weightFields,
      netWeightFields: row(base.netWeightFields),
      priceFields: row(base.priceFields),
      settlementFields: row(base.settlementFields)!,
      settlementSecondaryFields: row(base.settlementSecondaryFields),
      discountField: base.discountField ? field(base.discountField.key) : undefined,
      paymentPanel: base.paymentPanel
        ? {
            ...base.paymentPanel,
            methods: base.paymentPanel.methods.map((m, i) => (i === 0 ? { ...m, value: c?.netToPay ?? '' } : m)),
            totalValue: c?.netToPay ?? '',
          }
        : undefined,
      reprintButtonLabel: this.canPrint() ? 'Imprimir' : undefined,
    };
  }
}
