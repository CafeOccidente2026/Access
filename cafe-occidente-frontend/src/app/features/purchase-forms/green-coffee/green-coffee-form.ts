import { CommonModule, Location } from '@angular/common';
import { ChangeDetectionStrategy, Component, ElementRef, HostListener, computed, effect, inject, signal } from '@angular/core';
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
import { formatDisplayNumber, parseDisplayNumber, stripAnnouncementPrefix } from '../../../shared/utils/number-format';
import { isSequentialFieldEnabled } from '../../../shared/utils/sequential-gate';

/** Valores digitados, indexados por la `key` del campo en purchase-form-green.json. */
type FormModel = Record<string, string>;

interface GreenCoffeeMessages {
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

type GreenCoffeeContent = PurchaseFormContent & { readonly messages: GreenCoffeeMessages };

/**
 * Especial ("CV") y Fondo ("RP") son fijos en VERDES (sin AfterUpdate/combo real - ver
 * Form_VERDES.bas y VERDES.txt), a diferencia de Cafe Seco. "program" (Programa Federacion) se deja
 * siempre editable sin autocompletado: no hay tabla de programa migrada para VERDES (a diferencia de
 * staging_legacy_ness en Cafe Seco).
 */
const EDITABLE: string[] = [
  'idPart1', 'fullName', 'firstName', 'lastName', 'idType', 'address', 'cellphone', 'program',
  'bags', 'grossKg', 'tare', 'compKgPrice', 'penalty', 'shrinkageDiscount', 'otherDiscounts',
];

/** Requeridos para habilitar "Imprimir" (penalty/descuentos pueden quedar en blanco = 0). */
const REQUIRED: string[] = ['idPart1', 'fullName', 'bags', 'grossKg', 'tare', 'compKgPrice'];

/** Si se confirman en blanco quedan en 0 (no bloquean, pero tampoco se ven vacios). */
const ZERO_IF_EMPTY: string[] = ['penalty', 'shrinkageDiscount', 'otherDiscounts'];

const READONLY: string[] = [
  'agency', 'date', 'announcement', 'announcementDate', 'productCode', 'fund', 'invoicePrefix',
  'invoiceNumber', 'basePriceLoad', 'special', 'idNumber', 'healthyStoredPrice', 'defectStoredPrice',
  'bonus', 'costs', 'greenKg', 'netKg', 'kgPrice', 'withholding',
];

/** Orden en que el foco salta de un campo al siguiente que le toca llenar al usuario. */
const FOCUS_ORDER: string[] = [
  'idPart1', 'bags', 'grossKg', 'tare', 'compKgPrice', 'penalty', 'shrinkageDiscount', 'otherDiscounts',
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
      case 'idPart1':
        this.lookupGrower();
        break;
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
        this.announcementInfo.set(null);
        this.errorMessage.set(this.messages()?.noAnnouncement ?? null);
        this.tick.update((n) => n + 1);
      },
    });
  }

  /** Cedula_AfterUpdate: busca el caficultor; bloquea el formulario si esta fallecido. */
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
        this.model['firstName'] = [grower.firstName, grower.secondName].filter(Boolean).join(' ');
        this.model['lastName'] = [grower.lastName, grower.secondLastName].filter(Boolean).join(' ');
        this.model['idType'] = grower.growerType;
        this.model['address'] = grower.address;
        this.model['cellphone'] = grower.phone;
        this.locked.add('idPart1');
        // Si el dato migrado viene vacio (p.ej. caficultores historicos sin celular registrado), no se
        // bloquea: sin esto el campo quedaba en blanco y bloqueado para siempre, y como esta en REQUIRED
        // la cascada de calculo nunca llegaba a dispararse.
        ['fullName', 'firstName', 'lastName', 'idType', 'address', 'cellphone'].forEach((k) => {
          if ((this.model[k] ?? '').trim() !== '') {
            this.locked.add(k);
          }
        });
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
        this.calc.set(null);
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
    return {
      agencyId,
      fundId: info.fundId,
      invoiceNumber,
      idNumber: this.model['idPart1'],
      firstName: this.model['firstName'] ?? '',
      lastName: this.model['lastName'] ?? '',
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
      error: (err) => {
        console.error('Error al registrar compra Cafe Verde:', err?.error ?? err);
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
      return;
    }
    // Descarta los datos antes de navegar: si no, canDeactivate() vuelve a encontrar
    // input sin guardar y reabre este mismo dialogo, obligando a un segundo clic.
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
      idNumber: this.model['idPart1'] ?? '',
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
      weightFields: base.weightFields,
      netWeightFields: row(base.netWeightFields),
      priceFields: row(base.priceFields),
      settlementFields: row(base.settlementFields)!,
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
}
