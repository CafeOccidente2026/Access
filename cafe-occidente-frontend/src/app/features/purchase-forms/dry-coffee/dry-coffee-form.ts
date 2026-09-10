import { CommonModule, Location } from '@angular/common';
import { ChangeDetectionStrategy, Component, HostListener, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';

import { FormFieldDefinition, PurchaseFormContent } from '../../../core/models';
import {
  Agency,
  Announcement,
  DryCoffeePurchaseRequest,
  DryCoffeePurchaseResponse,
  Fund,
} from '../../../core/models/dry-coffee-purchase.model';
import { ContentService } from '../../../core/services/content.service';
import { DryCoffeePurchaseService } from '../../../core/services/dry-coffee-purchase.service';
import { ConfirmDialogComponent, PurchaseFormViewComponent } from '../../../shared/ui';

/** Valores digitados, indexados por la `key` del campo en purchase-form-dry.json. */
type FormModel = Record<string, string>;

interface DryCoffeeMessages {
  readonly acceptLabel: string;
  readonly noAnnouncement: string;
  readonly saveError: string;
  readonly savedNotice: string;
  readonly closeWarning: string;
  readonly closeConfirm: string;
  readonly closeCancel: string;
}

type DryCoffeeContent = PurchaseFormContent & { readonly messages: DryCoffeeMessages };

/** Campos editables en el orden de captura del formulario Access. */
const EDITABLE: string[] = [
  'agency', 'fund', 'idNumber', 'fullName', 'idType', 'address', 'cellphone',
  'special', 'sustentationPrice', 'bonus', 'costs',
  'totalStoredWeight', 'totalHuskWeight', 'healthyStoredWeight',
  'bags', 'grossKg', 'tare', 'penalty', 'shrinkageDiscount', 'otherDiscounts',
];

/** Requeridos para habilitar "Imprimir" (bonus/penalty/descuentos pueden quedar en blanco = 0). */
const REQUIRED: string[] = [
  'agency', 'fund', 'idNumber', 'fullName', 'idType', 'address', 'cellphone',
  'special', 'sustentationPrice', 'costs',
  'totalStoredWeight', 'totalHuskWeight', 'healthyStoredWeight', 'bags', 'grossKg', 'tare',
];

/** Campos siempre de solo lectura (calculados por el servidor o pendientes de otro módulo). */
const READONLY: string[] = [
  'date', 'announcement', 'announcementDate', 'invoice', 'productCode', 'basePriceLoad',
  'huskPrice', 'program', 'quota', 'idPart1', 'wastePercentage', 'huskPercentage', 'factor',
  'netKg', 'kgPrice', 'grossValue', 'netToPay',
];

const num = (v: string | undefined | null): number => {
  const s = (v ?? '').trim();
  return s === '' ? 0 : Number(s);
};

/**
 * Compras Café Seco. Reutiliza el diseño existente (PurchaseFormViewComponent + purchase-form-dry.json)
 * sin tocarlo; solo agrega la lógica: autollenado del anuncio, bloqueo secuencial de campos,
 * Escape para reiniciar, botón Imprimir condicional y guardado únicamente al imprimir.
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
  private readonly location = inject(Location);

  private readonly base = toSignal(this.content.loadJson<DryCoffeeContent>('purchase-form-dry'));
  readonly messages = computed<DryCoffeeMessages | undefined>(() => this.base()?.messages);
  readonly agencies = signal<Agency[]>([]);
  readonly funds = signal<Fund[]>([]);
  readonly announcement = signal<Announcement | null>(null);
  readonly result = signal<DryCoffeePurchaseResponse | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly savedNoticeOpen = signal(false);
  /** Se incrementa cuando hay que reconstruir el contenido (commit, anuncio, guardado, reset). */
  private readonly tick = signal(0);

  model: FormModel = {};
  private readonly locked = new Set<string>();
  private readonly today = new Date().toISOString().slice(0, 10);
  private readonly fieldCache = new Map<string, FormFieldDefinition>();
  private agencyOptions: string[] = [];
  private fundOptions: string[] = [];
  private readonly agencyIdByName = new Map<string, number>();
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
    this.service.agencies().subscribe((list) => {
      this.agencies.set(list);
      this.agencyOptions = list.map((a) => a.name);
      list.forEach((a) => this.agencyIdByName.set(a.name, a.id));
      this.tick.update((n) => n + 1);
    });
    this.service.funds().subscribe((list) => {
      this.funds.set(list);
      this.fundOptions = list.map((f) => `${f.code} - ${f.name}`);
      list.forEach((f) => this.fundIdByName.set(`${f.code} - ${f.name}`, f.id));
      this.tick.update((n) => n + 1);
    });
  }

  onFieldValueChange(event: { key: string; value: string | number }): void {
    this.model[event.key] = String(event.value);
  }

  onFieldCommitted(key: string): void {
    if (!EDITABLE.includes(key) || !(this.model[key] ?? '').trim()) {
      return;
    }
    this.locked.add(key);
    if (key === 'agency' || key === 'fund') {
      this.maybeLoadAnnouncement();
    }
    this.tick.update((n) => n + 1);
  }

  private maybeLoadAnnouncement(): void {
    const agencyId = this.agencyIdByName.get(this.model['agency'] ?? '');
    const fundId = this.fundIdByName.get(this.model['fund'] ?? '');
    if (!agencyId || !fundId || this.announcement()) {
      return;
    }
    this.errorMessage.set(null);
    this.service.latestAnnouncement(agencyId, fundId).subscribe({
      next: (a) => {
        this.announcement.set(a);
        this.tick.update((n) => n + 1);
      },
      error: () => {
        this.errorMessage.set(this.messages()?.noAnnouncement ?? null);
        this.tick.update((n) => n + 1);
      },
    });
  }

  print(): void {
    if (!this.canPrint()) {
      return;
    }
    this.errorMessage.set(null);
    const [firstName, lastName] = this.splitName(this.model['fullName'] ?? '');
    const request: DryCoffeePurchaseRequest = {
      agencyId: this.agencyIdByName.get(this.model['agency'])!,
      fundId: this.fundIdByName.get(this.model['fund'])!,
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
      healthyUnitPrice: num(this.model['sustentationPrice']),
      bonus: num(this.model['bonus']),
      penalty: num(this.model['penalty']),
      costs: num(this.model['costs']),
      withholdingExempt: false,
      freightDiscount: num(this.model['shrinkageDiscount']),
      otherDiscounts: num(this.model['otherDiscounts']),
      paymentMethod: 'EFECTIVO',
      checkNumber: null,
    };
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

  /** Escape reinicia todo el formulario en blanco y editable (única forma de "volver atrás"). */
  @HostListener('document:keydown.escape')
  reset(): void {
    this.model = {};
    this.locked.clear();
    this.announcement.set(null);
    this.result.set(null);
    this.errorMessage.set(null);
    this.savedNoticeOpen.set(false);
    this.fieldCache.clear();
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
      !!this.announcement() &&
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

    const ann = this.announcement();
    const res = this.result();
    const computedValues: Record<string, string | number> = {
      date: this.today,
      announcement: ann?.announcementNumber ?? '',
      announcementDate: ann?.announcementDate ?? '',
      basePriceLoad: res?.basePriceLoad ?? ann?.basePriceLoad ?? '',
      invoice: '',
      productCode: res?.productCode ?? '',
      huskPrice: '',
      program: '',
      quota: '',
      idPart1: this.model['idNumber'] ?? '',
      wastePercentage: res?.wastePercentage ?? '',
      huskPercentage: res?.defectivePercentage ?? '',
      factor: res?.healthyPercentage ?? '',
      netKg: res
        ? res.netKg
        : this.model['grossKg'] && this.model['tare']
          ? num(this.model['grossKg']) - num(this.model['tare'])
          : '',
      kgPrice: res?.unitPrice ?? '',
      grossValue: res?.grossValue ?? '',
      netToPay: res?.netToPay ?? '',
    };

    const field = (key: string): FormFieldDefinition => {
      const b = bmap.get(key)!;
      const patch: { options?: string[]; readonly?: boolean; value?: string | number } = {};
      if (key === 'agency') {
        patch.options = this.agencyOptions;
      } else if (key === 'fund') {
        patch.options = this.fundOptions;
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
        prev.options === next.options
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
        ? { ...base.paymentPanel, totalValue: res?.netToPay ?? '' }
        : undefined,
      reprintButtonLabel: this.canPrint() ? 'Imprimir' : undefined,
    };
  }
}
