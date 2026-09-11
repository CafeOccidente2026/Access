import { CommonModule, Location } from '@angular/common';
import { ChangeDetectionStrategy, Component, HostListener, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';

import { FormFieldDefinition, PurchaseFormContent } from '../../../core/models';
import {
  DryCoffeePurchaseCalculation,
  DryCoffeePurchaseRequest,
  DryCoffeePurchaseResponse,
  Fund,
  NextInvoiceNumber,
  QualityPercentages,
  SpecialInfo,
} from '../../../core/models/dry-coffee-purchase.model';
import { AuthService } from '../../../core/services/auth.service';
import { ContentService } from '../../../core/services/content.service';
import { DryCoffeePurchaseService } from '../../../core/services/dry-coffee-purchase.service';
import { GrowerService } from '../../../core/services/grower.service';
import { ConfirmDialogComponent, PurchaseFormViewComponent } from '../../../shared/ui';

/** Valores digitados, indexados por la `key` del campo en purchase-form-dry.json. */
type FormModel = Record<string, string>;

interface DryCoffeeMessages {
  readonly acceptLabel: string;
  readonly noAnnouncement: string;
  readonly deceasedBlocked: string;
  readonly saveError: string;
  readonly savedNotice: string;
  readonly closeWarning: string;
  readonly closeConfirm: string;
  readonly closeCancel: string;
}

type DryCoffeeContent = PurchaseFormContent & { readonly messages: DryCoffeeMessages };

/** Campos editables en el orden de captura del formulario Access. "agency" no entra: la
 *  autocompleta la sesión y nunca la toca el usuario (paso 1). */
const EDITABLE: string[] = [
  'fund', 'idNumber', 'fullName', 'idType', 'address', 'cellphone', 'program', 'quota',
  'special',
  'totalStoredWeight', 'totalHuskWeight', 'healthyStoredWeight',
  'bags', 'grossKg', 'tare', 'penalty', 'shrinkageDiscount', 'otherDiscounts',
];

/** Requeridos para habilitar "Imprimir" (bonus/penalty/descuentos pueden quedar en blanco = 0). */
const REQUIRED: string[] = [
  'fund', 'idNumber', 'fullName', 'idType', 'address', 'cellphone',
  'special', 'totalStoredWeight', 'totalHuskWeight', 'healthyStoredWeight', 'bags', 'grossKg', 'tare',
];

/** Campos siempre de solo lectura: los calcula el servidor o los deriva la sesión actual. */
const READONLY: string[] = [
  'agency', 'date', 'announcement', 'announcementDate', 'invoice', 'productCode', 'basePriceLoad',
  'huskPrice', 'sustentationPrice', 'bonus', 'costs', 'idPart1', 'wastePercentage', 'huskPercentage',
  'factor', 'netKg', 'kgPrice', 'grossValue', 'contribution', 'netToPay',
];

const num = (v: string | undefined | null): number => {
  const s = (v ?? '').trim();
  return s === '' ? 0 : Number(s);
};

/**
 * Compras Café Seco. Reutiliza el diseño existente (PurchaseFormViewComponent + purchase-form-dry.json)
 * sin tocarlo; solo agrega la lógica: agencia fija por sesión, factura reservada, búsqueda de
 * caficultor, autollenado del anuncio, bloqueo secuencial de campos, Escape para reiniciar,
 * botón Imprimir condicional y guardado únicamente al imprimir.
 * La cascada de cálculo corre en el servidor; aquí se muestran los valores que devuelve.
 */
@Component({
  selector: 'app-dry-coffee-form',
  standalone: true,
  imports: [CommonModule, PurchaseFormViewComponent, ConfirmDialogComponent],
  templateUrl: './dry-coffee-form.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DryCoffeeFormComponent {
  private readonly content = inject(ContentService);
  private readonly service = inject(DryCoffeePurchaseService);
  private readonly growerService = inject(GrowerService);
  private readonly authService = inject(AuthService);
  private readonly location = inject(Location);

  private readonly base = toSignal(this.content.loadJson<DryCoffeeContent>('purchase-form-dry'));
  readonly messages = computed<DryCoffeeMessages | undefined>(() => this.base()?.messages);
  readonly funds = signal<Fund[]>([]);
  readonly specialInfo = signal<SpecialInfo | null>(null);
  readonly invoiceReservation = signal<NextInvoiceNumber | null>(null);
  readonly qualityCalc = signal<QualityPercentages>({
    wastePercentage: null,
    defectivePercentage: null,
    healthyPercentage: null,
  });
  readonly calc = signal<DryCoffeePurchaseCalculation | null>(null);
  readonly result = signal<DryCoffeePurchaseResponse | null>(null);
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
      this.fundOptions = list.map((f) => `${f.code} - ${f.name}`);
      list.forEach((f) => this.fundIdByName.set(`${f.code} - ${f.name}`, f.id));
      this.tick.update((n) => n + 1);
    });
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
    this.locked.add(key);
    this.runSideEffects(key);
    this.tick.update((n) => n + 1);
  }

  private runSideEffects(key: string): void {
    switch (key) {
      case 'fund':
        this.reserveInvoiceNumber();
        break;
      case 'idNumber':
        this.lookupGrower();
        break;
      case 'special':
        this.loadSpecialInfo();
        break;
      case 'totalStoredWeight':
      case 'totalHuskWeight':
      case 'healthyStoredWeight':
        this.loadQualityPercentage(key);
        break;
      case 'penalty':
      case 'shrinkageDiscount':
      case 'otherDiscounts':
        this.recalculate();
        break;
    }
  }

  /** Paso 3: siguiente factura del rango DIAN autorizado, alone al confirmar Fondo. */
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

  /** Paso 4: busca el caficultor por cédula; bloquea el formulario si está fallecido. */
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
        ['fullName', 'idType', 'address', 'cellphone'].forEach((k) => this.locked.add(k));
        this.tick.update((n) => n + 1);
      },
      // No encontrado: se deja en blanco y editable para captura manual.
      error: () => undefined,
    });
  }

  /** Paso 5: Cod Prod + datos del anuncio vigente para Agencia+Fondo+Especial. */
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
        this.errorMessage.set(this.messages()?.noAnnouncement ?? null);
        this.tick.update((n) => n + 1);
      },
    });
  }

  /** Pasos 6-8: cada peso calcula su propio porcentaje, independiente de los otros dos. */
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
        this.errorMessage.set(this.messages()?.saveError ?? null);
        this.tick.update((n) => n + 1);
      },
    });
  }

  /** Pasos 12-14: recalcula la cascada completa (Castigo / Descuento Fro / Otros Desctos). */
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

  private buildRequest(): DryCoffeePurchaseRequest | null {
    const agencyId = this.authService.agencyId();
    const fundId = this.fundIdByName.get(this.model['fund'] ?? '');
    const invoiceNumber = this.invoiceReservation()?.invoiceNumber;
    if (!agencyId || !fundId || !invoiceNumber) {
      return null;
    }
    const [firstName, lastName] = this.splitName(this.model['fullName'] ?? '');
    return {
      agencyId,
      fundId,
      invoiceNumber,
      specialType: this.model['special'],
      idNumber: this.model['idNumber'],
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
        // TODO: generación real del documento soporte / factura PDF queda pendiente (prompt futuro).
        this.tick.update((n) => n + 1);
      },
      error: () => {
        this.errorMessage.set(this.messages()?.saveError ?? null);
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

  private buildContent(base: DryCoffeeContent): PurchaseFormContent {
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
    if (base.discountField) {
      bmap.set(base.discountField.key, base.discountField);
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
      announcement: info?.announcementNumber ?? '',
      announcementDate: info?.announcementDate ?? '',
      invoice: inv ? `${inv.prefix}${inv.invoiceNumber}` : '',
      productCode: info?.productCode ?? '',
      basePriceLoad: info?.basePriceLoad ?? '',
      huskPrice: info?.defectiveUnitPrice ?? '',
      sustentationPrice: info?.healthyUnitPrice ?? '',
      bonus: info?.bonus ?? '',
      costs: info?.costs ?? '',
      idPart1: this.model['idNumber'] ?? '',
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
