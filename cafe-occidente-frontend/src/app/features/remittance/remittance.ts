import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, linkedSignal, signal } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';

import { RemittancePurchase } from '../../core/models/remittance.model';
import { AuthService } from '../../core/services/auth.service';
import { ContentService } from '../../core/services/content.service';
import { RemittanceService } from '../../core/services/remittance.service';
import { AccessWindowComponent, AppButtonComponent, ConfirmDialogComponent } from '../../shared/ui';
import { formatDisplayNumber } from '../../shared/utils/number-format';
import { saveOnce } from '../../shared/utils/save-once';
import { shortDate } from '../announcement-report/announcement-report-pdf';
import { RemittanceWorkbook, remittanceWorkbooks, saveWorkbooks } from './remittance-files';

export interface RemittanceContent {
  readonly windowTitle: string;
  readonly fundLabel: string;
  readonly funds: string[];
  readonly specialLabel: string;
  readonly allLabel: string;
  readonly acceptLabel: string;
  readonly columns: Record<'selected' | 'invoice' | 'purchaseDate' | 'specialType' | 'idNumber' | 'sellerName' | 'netKg'
    | 'grossValue', string>;
  readonly pickFirst: string;
  readonly empty: string;
  readonly accumulatedLabel: string;
  readonly packagingQuantityLabel: string;
  readonly packagingTypeLabel: string;
  readonly packagingTypes: string[];
  readonly generateLabel: string;
  readonly searchLabel: string;
  readonly searchButton: string;
  readonly summary: string;
  readonly statuses: Record<string, string>;
  readonly filesLabel: string;
  readonly fileLabels: Record<string, string>;
  readonly unmarkLabel: string;
  readonly unmarkConfirm: string;
  readonly yesLabel: string;
  readonly noLabel: string;
  readonly messages: Record<'generated' | 'notFound' | 'unmarked' | 'error' | 'filesError', string>;
}

/** Aceptar del dialogo: el fondo y el especial elegidos; n vuelve a pedir la lista con lo mismo. */
interface PendingRequest {
  readonly fund: string;
  readonly special: string;
  readonly n: number;
}

const key = (p: RemittancePurchase) => `${p.module}-${p.id}`;
const fill = (text: string, values: Record<string, string | number>) =>
  Object.entries(values).reduce((t, [k, v]) => t.replaceAll(`{${k}}`, String(v)), text);

/**
 * Genera Remesa (HU-13 / RF-11, HU-14 / RF-12). Como Access: el dialogo pide el Especial (o Todos) y Aceptar;
 * Genera Plano lista las facturas pendientes con su casilla y el Acumulado de kilos; Genera Remesa guarda,
 * descarga los 4 archivos y las facturas salen de pendientes. Se puede buscar una remesa por numero para
 * volver a descargar sus archivos o, el Administrador, desmarcarla.
 */
@Component({
  selector: 'app-remittance',
  standalone: true,
  imports: [CommonModule, AccessWindowComponent, AppButtonComponent, ConfirmDialogComponent],
  templateUrl: './remittance.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RemittanceComponent {
  private readonly service = inject(RemittanceService);
  private readonly auth = inject(AuthService);

  readonly content = toSignal(inject(ContentService).loadJson<RemittanceContent>('remittance'));

  readonly fund = signal('RP');
  /** '' = Todos. */
  readonly special = signal('');
  readonly packagingQuantity = signal('');
  readonly packagingType = signal('');
  readonly saving = signal(false);
  readonly unmarking = signal(false);
  readonly confirmingUnmark = signal(false);
  /** Descarga de los libros; los tests la reemplazan. */
  saveFiles: (books: readonly RemittanceWorkbook[]) => Promise<void> = saveWorkbooks;

  private readonly specialsResource = rxResource({
    params: () => this.fund(),
    stream: ({ params }) => this.service.specials(params),
  });
  readonly specials = computed(() => (this.specialsResource.hasValue() ? this.specialsResource.value() : []));

  private readonly pendingRequest = signal<PendingRequest | undefined>(undefined);
  private readonly pendingResource = rxResource({
    params: () => this.pendingRequest(),
    stream: ({ params }) => this.service.pending(params.fund, params.special),
  });
  readonly pending = computed(() => (this.pendingResource.hasValue() ? this.pendingResource.value() : undefined));
  /** Las marcas se borran cada vez que llega una lista nueva. */
  readonly selected = linkedSignal<RemittancePurchase[] | undefined, Set<string>>({
    source: this.pending,
    computation: () => new Set<string>(),
  });
  readonly accumulated = computed(() =>
    (this.pending() ?? []).filter((p) => this.selected().has(key(p))).reduce((sum, p) => sum + p.netKg, 0));
  readonly canGenerate = computed(() =>
    this.selected().size > 0 && Number(this.packagingQuantity()) > 0 && this.packagingType() !== '');

  private readonly searchRequest = signal<{ number: string } | undefined>(undefined);
  private readonly searchResource = rxResource({
    params: () => this.searchRequest(),
    stream: ({ params }) => this.service.findByNumber(params.number),
  });
  private readonly searchValue = computed(() => (this.searchResource.hasValue() ? this.searchResource.value() : undefined));
  readonly found = linkedSignal({ source: this.searchValue, computation: (list) => list?.[0] ?? null });
  readonly canUnmark = computed(() => this.auth.isAdmin() && this.found()?.status === 'VIGENTE');

  /** Las filas de los 4 archivos de la remesa vigente que se muestra, cargadas antes del clic. */
  private readonly filesResource = rxResource({
    params: () => {
      const r = this.found();
      return r?.status === 'VIGENTE' ? r.id : undefined;
    },
    stream: ({ params }) => this.service.files(params),
  });
  readonly books = computed(() => (this.filesResource.hasValue() ? remittanceWorkbooks(this.filesResource.value()) : []));

  readonly message = linkedSignal({
    source: this.searchValue,
    computation: (list) => (list?.length === 0 ? (this.content()?.messages.notFound ?? null) : null),
  });

  accept(): void {
    this.pendingRequest.set({ fund: this.fund(), special: this.special(), n: (this.pendingRequest()?.n ?? 0) + 1 });
  }

  isSelected(p: RemittancePurchase): boolean {
    return this.selected().has(key(p));
  }

  toggle(p: RemittancePurchase): void {
    this.selected.update((set) => {
      const next = new Set(set);
      if (!next.delete(key(p))) {
        next.add(key(p));
      }
      return next;
    });
  }

  generate(): void {
    const request = this.pendingRequest();
    const content = this.content();
    if (!request || !content || !this.canGenerate()) {
      return;
    }
    const purchases = (this.pending() ?? []).filter((p) => this.isSelected(p)).map((p) => ({ module: p.module, id: p.id }));
    this.message.set(null);
    saveOnce(this.saving, () => this.service.create({
      fundCode: request.fund, specialType: request.special || null, purchases,
      packagingQuantity: Number(this.packagingQuantity()), packagingType: this.packagingType(),
    }), {
      next: (remittance) => {
        this.found.set(remittance);
        this.message.set(fill(content.messages.generated, { number: remittance.number }));
        this.packagingQuantity.set('');
        this.accept();
      },
      error: (err) => this.message.set(err?.error?.message ?? content.messages.error),
    });
  }

  search(number: string): void {
    if (number.trim()) {
      this.searchRequest.set({ number: number.trim() });
    }
  }

  /** Un archivo por clic: Chrome bloquea las descargas automaticas que siguen a la primera. */
  async download(book: RemittanceWorkbook): Promise<void> {
    try {
      await this.saveFiles([book]);
    } catch {
      this.message.set(this.content()?.messages.filesError ?? null);
    }
  }

  askUnmark(): void {
    this.confirmingUnmark.set(true);
  }

  unmark(): void {
    const remittance = this.found();
    const content = this.content();
    this.confirmingUnmark.set(false);
    if (!remittance || !content) {
      return;
    }
    saveOnce(this.unmarking, () => this.service.unmark(remittance.id), {
      next: (unmarked) => {
        this.found.set(unmarked);
        this.message.set(fill(content.messages.unmarked, { number: unmarked.number }));
        if (this.pendingRequest()) {
          this.accept();
        }
      },
      error: (err) => this.message.set(err?.error?.message ?? content.messages.error),
    });
  }

  summary(content: RemittanceContent): string {
    const r = this.found();
    if (!r) {
      return '';
    }
    return fill(content.summary, {
      status: content.statuses[r.status] ?? r.status,
      purchases: r.purchases,
      kg: formatDisplayNumber(r.netKg, 'currency'),
      packaging: [r.packagingQuantity, r.packagingType].filter((v) => v != null).join(' '),
    });
  }

  confirmText(content: RemittanceContent): string {
    return fill(content.unmarkConfirm, { number: this.found()?.number ?? '' });
  }

  kg(value: number): string {
    return formatDisplayNumber(value, 'currency');
  }

  money(value: number): string {
    return formatDisplayNumber(value, 'count');
  }

  date(iso: string): string {
    return shortDate(iso);
  }
}
