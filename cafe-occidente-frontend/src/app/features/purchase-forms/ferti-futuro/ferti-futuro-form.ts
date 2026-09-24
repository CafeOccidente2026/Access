import { CommonModule, Location } from '@angular/common';
import { ChangeDetectionStrategy, Component, ElementRef, HostListener, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import pdfMake from 'pdfmake/build/pdfmake';
import pdfFonts from 'pdfmake/build/vfs_fonts';

import { FormFieldDefinition, PurchaseFormContent } from '../../../core/models';
import { Announcement, Fund } from '../../../core/models/dry-coffee-purchase.model';
import {
  FertiFuturoPurchaseCalculation,
  FertiFuturoPurchaseRequest,
  FertiFuturoPurchaseResponse,
  FertiFuturoNextInvoiceNumber,
} from '../../../core/models/ferti-futuro-purchase.model';
import { AnnouncementService } from '../../../core/services/announcement.service';
import { AuthService } from '../../../core/services/auth.service';
import { ContentService } from '../../../core/services/content.service';
import { FertiFuturoPurchaseService } from '../../../core/services/ferti-futuro-purchase.service';
import { GrowerService } from '../../../core/services/grower.service';
import { ConfirmDialogComponent, PurchaseFormViewComponent } from '../../../shared/ui';
import { parseDisplayNumber, stripAnnouncementPrefix } from '../../../shared/utils/number-format';
import { isSequentialFieldEnabled } from '../../../shared/utils/sequential-gate';
import { buildFertiFuturoInvoiceDocDefinition, loadLogoDataUrl } from './ferti-futuro-invoice';

type FormModel = Record<string, string>;

interface FertiFuturoMessages {
  readonly acceptLabel: string;
  readonly noAnnouncement: string;
  readonly deceasedBlocked: string;
  readonly idNumberNotFound: string;
  readonly futurePurchaseNotFound: string;
  readonly saveError: string;
  readonly savedNotice: string;
  readonly closeWarning: string;
  readonly closeConfirm: string;
  readonly closeCancel: string;
}

type FertiFuturoContent = PurchaseFormContent & { readonly messages: FertiFuturoMessages };

const EDITABLE: string[] = [
  'fund', 'idPart1', 'fullName', 'idType', 'address', 'special', 'futurePurchaseId',
  'sacos', 'grossKg', 'netKg', 'healthyStoredWeight', 'defectiveStoredWeight',
  'penalty', 'freightDiscount', 'otherDiscounts',
];

/** Requeridos para habilitar "Imprimir" (defectiveStoredWeight/descuentos pueden quedar en blanco = 0). */
const REQUIRED: string[] = ['fund', 'idPart1', 'special', 'sacos', 'grossKg', 'netKg', 'healthyStoredWeight'];

const ZERO_IF_EMPTY: string[] = ['defectiveStoredWeight', 'penalty', 'freightDiscount', 'otherDiscounts'];

const READONLY: string[] = [
  'agency', 'date', 'announcement', 'announcementDate', 'associated', 'invoicePrefix', 'invoiceNumber',
  'productCode', 'sustentationPrice', 'huskPrice', 'bonus', 'costs', 'idNumber',
  'futurePurchaseBalance', 'healthyPercentage', 'defectivePercentage', 'qualityIncrementAmount',
  'tareKg', 'kgPrice', 'grossValue', 'contribution', 'netToPay',
];

const FOCUS_ORDER: string[] = [
  'fund', 'idPart1', 'special',
  'sacos', 'grossKg', 'netKg', 'healthyStoredWeight', 'defectiveStoredWeight',
  'penalty', 'freightDiscount', 'otherDiscounts',
];

const num = parseDisplayNumber;

/**
 * FERTIFUTURO (Form_FERTIFUTURO.bas): liquidación con cascada de precio completa y factura propia
 * (comparte el rango DIAN con Seco/Verde/Pasilla/Otros). A diferencia de esos 4, Peso Alm Sana/Peso
 * Alm Defec son inputs directos (sin Peso Tot Alm/Pasilla), Kilos Netos/Kilos Brutos son ambos
 * inputs (Destare lo calcula el servidor: grossKg - netKg), y no captura Celular. Vínculo opcional
 * a un compromiso de Compras a Futuro (Form_FUTURE FERTIFUTURO.bas, Texto29_AfterUpdate): si se
 * liga, valida que Kilos Netos no supere el saldo pendiente del compromiso y lo descuenta al guardar
 * - el backend ya hace esa validación, acá solo se muestra el mensaje real si se dispara.
 */
@Component({
  selector: 'app-ferti-futuro-form',
  standalone: true,
  imports: [CommonModule, PurchaseFormViewComponent, ConfirmDialogComponent],
  templateUrl: './ferti-futuro-form.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FertiFuturoFormComponent {
  private readonly content = inject(ContentService);
  private readonly service = inject(FertiFuturoPurchaseService);
  private readonly announcementService = inject(AnnouncementService);
  private readonly growerService = inject(GrowerService);
  private readonly authService = inject(AuthService);
  private readonly location = inject(Location);
  private readonly elementRef = inject(ElementRef);

  private readonly base = toSignal(this.content.loadJson<FertiFuturoContent>('purchase-form-ferti-futuro'));
  readonly messages = computed<FertiFuturoMessages | undefined>(() => this.base()?.messages);
  readonly funds = signal<Fund[]>([]);
  readonly announcementInfo = signal<Announcement | null>(null);
  readonly invoiceReservation = signal<FertiFuturoNextInvoiceNumber | null>(null);
  readonly calc = signal<FertiFuturoPurchaseCalculation | null>(null);
  readonly result = signal<FertiFuturoPurchaseResponse | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly blockedByDeceased = signal(false);
  readonly savedNoticeOpen = signal(false);
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
    this.reserveInvoiceNumber();
    this.logoDataUrlPromise = loadLogoDataUrl('assets/images/cafe-occidente-logo.png');
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
    if (key !== 'idPart1' && key !== 'futurePurchaseId') {
      this.locked.add(key);
    }
    this.runSideEffects(key);
    this.tick.update((n) => n + 1);
    if (key !== 'idPart1' && key !== 'futurePurchaseId') {
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
      case 'special':
        this.loadAnnouncementInfo();
        break;
      case 'futurePurchaseId':
        this.lookupFuturePurchase();
        break;
      case 'sacos':
      case 'grossKg':
      case 'netKg':
      case 'healthyStoredWeight':
      case 'defectiveStoredWeight':
      case 'penalty':
      case 'freightDiscount':
      case 'otherDiscounts':
        this.recalculate();
        break;
    }
  }

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
        this.locked.add('idPart1');
        // Mismo fix aplicado hoy en el resto de los formularios: si el dato migrado viene vacio, no
        // se bloquea, para que el cajero lo pueda completar a mano.
        ['fullName', 'idType', 'address'].forEach((k) => {
          if ((this.model[k] ?? '').trim() !== '') {
            this.locked.add(k);
          }
        });
        this.tick.update((n) => n + 1);
        this.advanceFocus('idPart1');
      },
      error: () => {
        this.errorMessage.set(this.messages()?.idNumberNotFound ?? 'Este número de cédula no existe.');
        this.tick.update((n) => n + 1);
      },
    });
  }

  /** Vínculo opcional a un compromiso de Compras a Futuro: solo valida/muestra el saldo, no bloquea
   *  la captura si no se usa (campo vacío = liquidación sin compromiso, caso normal). */
  private lookupFuturePurchase(): void {
    const raw = (this.model['futurePurchaseId'] ?? '').trim();
    if (!raw) {
      this.model['futurePurchaseBalance'] = '';
      this.locked.delete('futurePurchaseId');
      this.tick.update((n) => n + 1);
      return;
    }
    const id = Number(raw);
    this.service.findFuturePurchase(id).subscribe({
      next: (fp) => {
        this.model['futurePurchaseBalance'] = String(fp.remainingKg);
        this.locked.add('futurePurchaseId');
        this.tick.update((n) => n + 1);
      },
      error: () => {
        this.model['futurePurchaseId'] = '';
        this.model['futurePurchaseBalance'] = '';
        this.errorMessage.set(this.messages()?.futurePurchaseNotFound ?? 'Ese compromiso no existe.');
        this.tick.update((n) => n + 1);
      },
    });
  }

  private loadAnnouncementInfo(): void {
    const agencyId = this.authService.agencyId();
    const fundId = this.fundIdByName.get(this.model['fund'] ?? '');
    const specialType = this.model['special'];
    if (!agencyId || !fundId || !specialType) {
      return;
    }
    this.announcementService.latest(agencyId, fundId, specialType).subscribe({
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

  /** Mensaje real del backend (fallecido, saldo de compromiso excedido, negativo) en vez de uno
   *  generico - esas validaciones viven solo en el backend. */
  private backendErrorMessage(err: unknown): string | null {
    const message = (err as { error?: { message?: string } })?.error?.message;
    return message ?? this.messages()?.saveError ?? null;
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
      error: (err) => {
        this.calc.set(null);
        this.errorMessage.set(this.backendErrorMessage(err));
        this.tick.update((n) => n + 1);
      },
    });
  }

  private buildRequest(): FertiFuturoPurchaseRequest | null {
    const agencyId = this.authService.agencyId();
    const fundId = this.fundIdByName.get(this.model['fund'] ?? '');
    const invoiceNumber = this.invoiceReservation()?.invoiceNumber;
    const hasRequiredCascadeInputs = REQUIRED.every((k) => (this.model[k] ?? '').trim() !== '');
    if (!agencyId || !fundId || !invoiceNumber || !hasRequiredCascadeInputs) {
      return null;
    }
    const futurePurchaseId = (this.model['futurePurchaseId'] ?? '').trim();
    return {
      agencyId,
      fundId,
      invoiceNumber,
      specialType: this.model['special'],
      idNumber: this.model['idPart1'],
      firstName: this.splitName(this.model['fullName'] ?? '')[0],
      lastName: this.splitName(this.model['fullName'] ?? '')[1],
      growerType: (this.model['idType'] ?? '').trim().toUpperCase(),
      address: this.model['address'],
      futurePurchaseId: futurePurchaseId ? Number(futurePurchaseId) : null,
      sacos: num(this.model['sacos']),
      netKg: num(this.model['netKg']),
      grossKg: num(this.model['grossKg']),
      healthyStoredWeight: num(this.model['healthyStoredWeight']),
      defectiveStoredWeight: num(this.model['defectiveStoredWeight']),
      penalty: num(this.model['penalty']),
      withholdingExempt: false,
      freightDiscount: num(this.model['freightDiscount']),
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
        console.error('Error al registrar liquidación Fertifuturo:', err?.error ?? err);
        this.errorMessage.set(this.backendErrorMessage(err));
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
    this.announcementInfo.set(null);
    this.calc.set(null);
    this.result.set(null);
    this.errorMessage.set(null);
    this.blockedByDeceased.set(false);
    this.savedNoticeOpen.set(false);
    this.fieldCache.clear();
    this.prefillAgency();
    this.reserveInvoiceNumber();
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

  private buildContent(base: FertiFuturoContent): PurchaseFormContent {
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

    const info = this.announcementInfo();
    const inv = this.invoiceReservation();
    const c = this.calc();
    const growerType = (this.model['idType'] ?? '').trim().toUpperCase();

    const computedValues: Record<string, string | number> = {
      agency: this.authService.agencyName() ?? '',
      date: this.today,
      announcement: info?.announcementNumber ? stripAnnouncementPrefix(info.announcementNumber) : '',
      announcementDate: info?.announcementDate ?? '',
      associated: growerType === 'S' ? 'ASOCIADO' : growerType === 'C' ? 'NO ASOCIADO' : '',
      invoicePrefix: inv?.prefix ?? '',
      invoiceNumber: inv?.invoiceNumber ?? '',
      sustentationPrice: info?.healthyUnitPrice ?? '',
      huskPrice: info?.defectiveUnitPrice ?? '',
      bonus: info?.bonus ?? '',
      costs: info?.costs ?? '',
      healthyPercentage: c?.healthyPercentage ?? '',
      defectivePercentage: c?.defectivePercentage ?? '',
      qualityIncrementAmount: c?.qualityIncrementAmount ?? '',
      tareKg: c?.tareKg ?? '',
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
        patch.value = computedValues[key] ?? this.model[key] ?? '';
      } else if (this.locked.has(key)) {
        patch.readonly = true;
        patch.value = this.model[key] ?? '';
      } else if (key !== 'futurePurchaseId' && !isSequentialFieldEnabled(key, FOCUS_ORDER, this.locked)) {
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
      reprintButtonLabel: this.canPrint() ? 'Imprimir' : undefined,
    };
  }

  private printInvoice(purchase: FertiFuturoPurchaseResponse): void {
    this.logoDataUrlPromise.then((logoDataUrl) => {
      const docDefinition = buildFertiFuturoInvoiceDocDefinition(purchase, logoDataUrl);
      pdfMake.vfs = pdfFonts;
      pdfMake.createPdf(docDefinition).open();
    });
  }
}
