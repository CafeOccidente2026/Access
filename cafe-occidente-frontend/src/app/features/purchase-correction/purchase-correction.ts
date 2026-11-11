import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, linkedSignal, signal } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';

import { PurchasePayment } from '../../core/models/payment-method.model';
import { AnnulmentCandidate, AnnulmentDirection, AnnulmentModule } from '../../core/models/purchase-annulment.model';
import { PurchaseCorrectionRequest, PurchaseCorrectionResult } from '../../core/models/purchase-correction.model';
import { PurchaseQueryDetail } from '../../core/models/purchase-query.model';
import { ContentService } from '../../core/services/content.service';
import { NavigationService } from '../../core/services/navigation.service';
import { PurchaseCorrectionService } from '../../core/services/purchase-correction.service';
import { PurchaseQueryService } from '../../core/services/purchase-query.service';
import { AccessWindowComponent, FormFlowDirective, InvoiceNavigatorComponent, NavigatorButton, PurchaseFormViewComponent } from '../../shared/ui';
import { formatDisplayNumber } from '../../shared/utils/number-format';
import { saveOnce } from '../../shared/utils/save-once';
import { PurchaseQueryContent } from '../purchase-query/purchase-query';
import { fillQueryContent } from '../purchase-query/purchase-query-fields';

/** Un campo que se corrige; {@code text}: direccion, celular y numero de cheque (no son montos). */
export interface CorrectionField {
  readonly key: string;
  readonly label: string;
  readonly text?: boolean;
}

export interface PurchaseCorrectionContent {
  readonly windowTitle: string;
  readonly invoiceLabel: string;
  readonly searchLabel: string;
  readonly backLabel: string;
  readonly backRoute: string;
  readonly navigation: NavigatorButton[];
  readonly modules: Record<AnnulmentModule, string>;
  readonly heading: string;
  /** Lo que el area dejo corregir en cada modulo (pesos, muestra, castigo, descuentos, direccion y celular). */
  readonly fields: Record<AnnulmentModule, CorrectionField[]>;
  readonly paymentFields: CorrectionField[];
  readonly results: CorrectionField[];
  readonly beforeLabel: string;
  readonly afterLabel: string;
  readonly paymentTotalLabel: string;
  readonly saveLabel: string;
  readonly messages: Record<'notFound' | 'noMore' | 'error' | 'annulled' | 'unbalanced' | 'saved' | 'saveError', string>;
}

type Values = Readonly<Record<string, string>>;
type ListRequest =
  | { readonly invoice: number }
  | { readonly from: number | null; readonly direction: AnnulmentDirection };

const AMOUNTS = ['checkAmount', 'transferAmount', 'cardAmount'] as const;
const toText = (value: unknown): string => (value == null ? '' : String(value));
const toNumber = (value: string | undefined): number => Number((value ?? '').replace(',', '.')) || 0;

/** Los valores guardados de la compra para los campos que se corrigen. */
function storedValues(detail: PurchaseQueryDetail, fields: readonly CorrectionField[]): Values {
  const purchase = detail.purchase as unknown as Record<string, unknown>;
  return Object.fromEntries(fields.map((f) => [f.key, toText(purchase[f.key])]));
}

function storedPayment(detail: PurchaseQueryDetail): Values {
  const p = detail.payment;
  return {
    cashAmount: toText(p?.cashAmount ?? detail.purchase.netToPay),
    checkAmount: toText(p?.checkAmount ?? 0),
    checkNumber: toText(p?.checkNumber),
    transferAmount: toText(p?.transferAmount ?? 0),
    cardAmount: toText(p?.cardAmount ?? 0),
  };
}

function toPayment(values: Values): PurchasePayment {
  return {
    cashAmount: toNumber(values['cashAmount']),
    checkAmount: toNumber(values['checkAmount']),
    transferAmount: toNumber(values['transferAmount']),
    cardAmount: toNumber(values['cardAmount']),
    checkNumber: values['checkNumber']?.trim() || null,
  };
}

/**
 * "Menu Habilitacion" (HU-05, respuesta del area 11-nov): busca la compra como Consultar Compras, la muestra en
 * solo lectura y deja corregir solo pesos, muestra, castigo, descuentos, direccion, celular y formas de pago. El
 * servidor recalcula con el anuncio original y guarda con el mismo numero de factura. Si no se puede corregir
 * (anulada, en remesa, con salidas...), la primera vista previa trae el motivo y no se deja editar.
 */
@Component({
  selector: 'app-purchase-correction',
  standalone: true,
  imports: [CommonModule, AccessWindowComponent, FormFlowDirective, InvoiceNavigatorComponent, PurchaseFormViewComponent],
  templateUrl: './purchase-correction.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PurchaseCorrectionComponent {
  private readonly service = inject(PurchaseCorrectionService);
  private readonly query = inject(PurchaseQueryService);
  private readonly navigation = inject(NavigationService);
  private readonly contentService = inject(ContentService);

  readonly content = toSignal(this.contentService.loadJson<PurchaseCorrectionContent>('purchase-correction'));
  private readonly queryContent = toSignal(this.contentService.loadJson<PurchaseQueryContent>('purchase-query'));

  private readonly request = signal<ListRequest | undefined>(undefined);
  private readonly list = rxResource({
    params: () => this.request(),
    stream: ({ params }) =>
      'invoice' in params ? this.service.findByInvoice(params.invoice) : this.service.navigate(params.from, params.direction),
  });
  private readonly listValue = computed(() => (this.list.hasValue() ? this.list.value() : undefined));
  readonly candidates = linkedSignal<AnnulmentCandidate[] | undefined, AnnulmentCandidate[]>({
    source: this.listValue,
    computation: (list, previous) => (list?.length ? list : (previous?.value ?? [])),
  });
  readonly selected = linkedSignal<AnnulmentCandidate[], AnnulmentCandidate | null>({
    source: this.candidates,
    computation: (list) => list[0] ?? null,
  });

  private readonly detail = rxResource({
    params: () => {
      const s = this.selected();
      return s ? { module: s.module, id: s.id } : undefined;
    },
    stream: ({ params }) => this.query.detail(params.module, params.id),
  });
  readonly detailValue = computed(() => (this.detail.hasValue() ? this.detail.value() : undefined));
  readonly annulled = computed(() => this.detailValue()?.status === 'ANULADA');

  /** La compra como esta guardada, en solo lectura y sin reimprimir (eso queda en Consultar Compras). */
  readonly view = computed(() => {
    const detail = this.detailValue();
    const content = this.queryContent();
    return detail && content
      ? { ...fillQueryContent(content, detail, content.correctedMark), reprintButtonLabel: undefined }
      : null;
  });

  readonly fields = computed(() => {
    const module = this.detailValue()?.module;
    return module ? (this.content()?.fields[module] ?? []) : [];
  });
  private readonly stored = computed(() => {
    const detail = this.detailValue();
    return detail ? storedValues(detail, this.fields()) : null;
  });
  readonly values = linkedSignal<Values | null, Values>({ source: this.stored, computation: (stored) => stored ?? {} });
  /** Lo que se mando a calcular: los valores guardados al abrir y luego cada cambio confirmado de un campo. */
  private readonly submitted = linkedSignal<Values | null, Values | null>({ source: this.stored, computation: (s) => s });

  private readonly preview = rxResource({
    params: () => {
      const detail = this.detailValue();
      const values = this.submitted();
      return detail && values && !this.annulled() ? { detail, values } : undefined;
    },
    stream: ({ params }) =>
      this.service.preview(params.detail.module, params.detail.purchase.id, this.buildRequest(params.detail, params.values, null)),
  });
  readonly after = computed<PurchaseCorrectionResult | null>(() => (this.preview.hasValue() ? this.preview.value() : null));
  readonly previewError = computed(() => {
    const error = this.preview.error() as HttpErrorResponse | undefined;
    return error ? (error.error?.message ?? this.content()?.messages.error ?? null) : null;
  });
  /** La compra no se puede corregir: fallo la vista previa con los valores guardados. */
  readonly blocked = computed(() => !!this.previewError() && this.submitted() === this.stored());

  /** Formas de pago: las guardadas al abrir; con cada calculo nuevo, la diferencia del neto va a efectivo. */
  readonly payment = linkedSignal<{ detail?: PurchaseQueryDetail; after: PurchaseCorrectionResult | null }, Values>({
    source: () => ({ detail: this.detailValue(), after: this.after() }),
    computation: (source, previous) => {
      if (!source.detail) {
        return {};
      }
      const base = previous && previous.source.detail === source.detail ? previous.value : storedPayment(source.detail);
      if (!source.after) {
        return base;
      }
      const cash = source.after.netToPay - AMOUNTS.reduce((sum, key) => sum + toNumber(base[key]), 0);
      return cash >= 0 ? { ...base, cashAmount: String(cash) } : base;
    },
  });
  readonly paymentTotal = computed(() => {
    const p = toPayment(this.payment());
    return p.cashAmount + p.checkAmount + p.transferAmount + p.cardAmount;
  });
  readonly balanced = computed(() => this.after()?.netToPay === this.paymentTotal());
  readonly paymentTotalText = computed(() => formatDisplayNumber(this.paymentTotal(), 'count'));

  readonly saving = signal(false);
  readonly canSave = computed(() => !!this.after() && this.balanced() && !this.blocked() && !this.preview.isLoading());
  /** Resultado del ultimo guardado; se borra al cambiar de compra. */
  private readonly notice = linkedSignal<AnnulmentCandidate | null, { text: string; error: boolean } | null>({
    source: this.selected,
    computation: () => null,
  });
  readonly noticeError = computed(() => !!this.notice()?.error);
  private readonly listMessage = computed(() => {
    const m = this.content()?.messages;
    if (!m) {
      return null;
    }
    if (this.list.error() || this.detail.error()) {
      return m.error;
    }
    if (this.listValue()?.length === 0) {
      const request = this.request();
      return request && 'invoice' in request ? m.notFound : m.noMore;
    }
    return null;
  });
  readonly message = computed(() => this.notice()?.text ?? this.listMessage());

  search(event: Event, invoice: string): void {
    event.preventDefault();
    const number = Number(invoice.replace(/\D/g, ''));
    if (number) {
      this.request.set({ invoice: number });
    }
  }

  go(direction: AnnulmentDirection): void {
    this.request.set({ from: this.selected()?.invoiceNumber ?? null, direction });
  }

  choose(candidate: AnnulmentCandidate): void {
    this.selected.set(candidate);
  }

  setValue(key: string, value: string): void {
    this.values.update((values) => ({ ...values, [key]: value }));
  }

  setPayment(key: string, value: string): void {
    this.payment.update((payment) => ({ ...payment, [key]: value }));
  }

  /** Al confirmar un campo (Enter o salir de el) el servidor recalcula con el anuncio original. */
  recalculate(): void {
    this.submitted.set({ ...this.values() });
  }

  save(): void {
    const detail = this.detailValue();
    const content = this.content();
    if (!detail || !content || !this.canSave()) {
      return;
    }
    const request = this.buildRequest(detail, this.values(), toPayment(this.payment()));
    saveOnce(this.saving, () => this.service.correct(detail.module, detail.purchase.id, request), {
      next: () => {
        this.notice.set({ text: content.messages.saved.replace('{invoice}', String(detail.purchase.invoiceNumber)), error: false });
        this.detail.reload();
      },
      error: (err: HttpErrorResponse) => this.notice.set({ text: err?.error?.message ?? content.messages.saveError, error: true }),
    });
  }

  back(): void {
    this.navigation.goTo(this.content()?.backRoute);
  }

  /** Antes (guardado) y despues (calculado) de cada resultado, formateados como en la ficha. */
  result(key: string): { before: string; after: string } {
    const purchase = this.detailValue()?.purchase as unknown as Record<string, number> | undefined;
    const after = this.after() as unknown as Record<string, number> | null;
    // Kilos con sus decimales; pesos enteros, como la ficha de Anular Documento.
    const show = (value: number | undefined) =>
      value == null ? '' : formatDisplayNumber(value, key === 'netKg' ? 'currency' : 'count');
    return { before: show(purchase?.[key]), after: show(after?.[key]) };
  }

  private buildRequest(detail: PurchaseQueryDetail, values: Values, payment: PurchasePayment | null): PurchaseCorrectionRequest {
    const typed = Object.fromEntries(
      this.fields().map((f) => [f.key, f.text ? (values[f.key] ?? '').trim() : toNumber(values[f.key])]),
    );
    const purchase = detail.purchase as unknown as Record<string, unknown>;
    return {
      // Fijos: el servidor usa los de la compra; van porque el request de cada modulo los pide.
      fundId: 0,
      idNumber: detail.purchase.idNumber,
      specialType: detail.purchase.specialType,
      compKgPrice: purchase['compKgPrice'] as number | undefined,
      address: '',
      cellphone: '',
      ...typed,
      payment,
    };
  }
}
