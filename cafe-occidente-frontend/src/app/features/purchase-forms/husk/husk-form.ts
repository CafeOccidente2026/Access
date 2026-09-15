import { CommonModule, Location } from '@angular/common';
import { ChangeDetectionStrategy, Component, ElementRef, HostListener, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';

import { FormFieldDefinition, PurchaseFormContent } from '../../../core/models';
import {
  HuskAnnouncementInfo,
  HuskNextInvoiceNumber,
  HuskPurchaseCalculation,
  HuskPurchaseRequest,
  HuskPurchaseResponse,
} from '../../../core/models/husk-purchase.model';
import { AuthService } from '../../../core/services/auth.service';
import { ContentService } from '../../../core/services/content.service';
import { GrowerService } from '../../../core/services/grower.service';
import { HuskPurchaseService } from '../../../core/services/husk-purchase.service';
import { ConfirmDialogComponent, PurchaseFormViewComponent } from '../../../shared/ui';

/** Valores digitados, indexados por la `key` del campo en purchase-form-husk.json. */
type FormModel = Record<string, string>;

interface HuskMessages {
  readonly acceptLabel: string;
  readonly noAnnouncement: string;
  readonly deceasedBlocked: string;
  readonly saveError: string;
  readonly savedNotice: string;
  readonly closeWarning: string;
  readonly closeConfirm: string;
  readonly closeCancel: string;
}

type HuskContent = PurchaseFormContent & { readonly messages: HuskMessages };

/**
 * Especial ("PASILLA") y Fondo ("RP") son fijos en la practica (RowSource de un solo valor en
 * PASILLA.txt), aunque Cuadro_combinado61_AfterUpdate si resuelve Cod_Prod via Especial+Fondo
 * (ver Form_PASILLA.bas) - a diferencia de VERDES. No hay Programa/Cupo en PASILLA (no aparece en
 * ningun sub de Form_PASILLA.bas).
 */
const EDITABLE: string[] = [
  'idNumber', 'fullName', 'almondWeight',
  'bags', 'grossKg', 'tare', 'shrinkageDiscount', 'otherDiscounts',
];

/** Requeridos para habilitar "Imprimir" (descuentos pueden quedar en blanco = 0). */
const REQUIRED: string[] = ['idNumber', 'fullName', 'almondWeight', 'bags', 'grossKg', 'tare'];

const READONLY: string[] = [
  'agency', 'fund', 'date', 'announcement', 'announcementDate', 'invoice', 'productCode',
  'basePriceDryLoad', 'idPart1', 'special', 'pointPrice', 'almondPercentage', 'netKg', 'kgPrice',
  'withholding',
];

/** Orden en que el foco salta de un campo al siguiente (sigue el orden visual del JSON: calidad
 *  antes que pesos - igual que W_AlmSana_AfterUpdate/Destare_LostFocus son independientes). */
const FOCUS_ORDER: string[] = [
  'idNumber', 'almondWeight', 'bags', 'grossKg', 'tare', 'shrinkageDiscount', 'otherDiscounts',
];

const num = (v: string | undefined | null): number => {
  const s = (v ?? '').trim();
  return s === '' ? 0 : Number(s);
};

/**
 * Compra Pasilla (PASILLA). Mismo patron que Compras Cafe Seco/VERDES: agencia fija por sesion,
 * factura/anuncio/Cod_Prod se resuelven al iniciar (Especial/Fondo fijos - ver arriba), busqueda de
 * caficultor, bloqueo secuencial, Escape para reiniciar, guardado solo al imprimir. La cascada corre
 * en el servidor (HuskPurchaseCalculator).
 */
@Component({
  selector: 'app-husk-form',
  standalone: true,
  imports: [CommonModule, PurchaseFormViewComponent, ConfirmDialogComponent],
  templateUrl: './husk-form.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HuskFormComponent {
  private readonly content = inject(ContentService);
  private readonly service = inject(HuskPurchaseService);
  private readonly growerService = inject(GrowerService);
  private readonly authService = inject(AuthService);
  private readonly location = inject(Location);
  private readonly elementRef = inject(ElementRef);

  private readonly base = toSignal(this.content.loadJson<HuskContent>('purchase-form-husk'));
  readonly messages = computed<HuskMessages | undefined>(() => this.base()?.messages);
  readonly announcementInfo = signal<HuskAnnouncementInfo | null>(null);
  readonly invoiceReservation = signal<HuskNextInvoiceNumber | null>(null);
  readonly calc = signal<HuskPurchaseCalculation | null>(null);
  readonly result = signal<HuskPurchaseResponse | null>(null);
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
      case 'almondWeight':
      case 'tare':
      case 'shrinkageDiscount':
      case 'otherDiscounts':
        this.recalculate();
        break;
    }
  }

  /** "Para asignar # factura pasilla": siguiente factura propia de PASILLA. */
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

  /** Cuadro_combinado61_AfterUpdate: Cod Prod + anuncio vigente (Especial "PASILLA" / Fondo "RP"). */
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

  /** Cedula_LostFocus: busca el caficultor; bloquea el formulario si esta fallecido. */
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

  /** W_AlmSana_AfterUpdate / Destare_LostFocus / Descuento_Fro_LostFocus / OtrosDescuentos. */
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

  private buildRequest(): HuskPurchaseRequest | null {
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
      almondWeight: num(this.model['almondWeight']),
      bagsCount: num(this.model['bags']),
      grossKg: num(this.model['grossKg']),
      tareKg: num(this.model['tare']),
      pointPrice: info.pointPrice,
      costs: info.costs,
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

  private buildContent(base: HuskContent): PurchaseFormContent {
    const bmap = new Map<string, FormFieldDefinition>();
    const collect = (fields?: FormFieldDefinition[]) => fields?.forEach((f) => bmap.set(f.key, f));
    collect(base.topFields);
    collect(base.identificationFields);
    collect(base.federationFields);
    collect(base.contactFields);
    collect(base.qualityFields);
    collect(base.netWeightFields);
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
      fund: 'RP',
      date: this.today,
      announcement: info?.announcementNumber ?? '',
      announcementDate: info?.announcementDate ?? '',
      invoice: inv ? `${inv.prefix}${inv.invoiceNumber}` : '',
      productCode: info?.productCode ?? '',
      basePriceDryLoad: info?.basePriceDryLoad ?? '',
      idPart1: this.model['idNumber'] ?? '',
      special: 'PASILLA',
      pointPrice: info?.pointPrice ?? '',
      almondPercentage: c?.almondPercentage ?? '',
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
      netWeightFields: row(base.netWeightFields),
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
