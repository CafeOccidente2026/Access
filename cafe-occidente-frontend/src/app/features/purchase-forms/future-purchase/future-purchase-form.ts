import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, ElementRef, HostListener, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';

import { FormFieldDefinition, PurchaseFormContent, PurchasePayment } from '../../../core/models';
import { Announcement } from '../../../core/models/dry-coffee-purchase.model';
import { FuturePurchaseRequest } from '../../../core/models/future-purchase.model';
import { AuthService } from '../../../core/services/auth.service';
import { ContentService } from '../../../core/services/content.service';
import { FuturePurchaseService } from '../../../core/services/future-purchase.service';
import { GrowerService } from '../../../core/services/grower.service';
import { ConfirmDialogComponent, PurchaseFormViewComponent } from '../../../shared/ui';
import { formatDisplayNumber, parseDisplayNumber, stripAnnouncementPrefix } from '../../../shared/utils/number-format';
import { isSequentialFieldEnabled } from '../../../shared/utils/sequential-gate';
import { buildFuturePurchaseManifestoDocDefinition } from './future-purchase-invoice';
import { advanceFocus, focusField } from '../form-focus';
import { hasRequired, saveAndPrint } from '../purchase-print';
import { UnsavedCloseForm } from '../unsaved-close';

type FormModel = Record<string, string>;

interface FuturePurchaseMessages {
  readonly acceptLabel: string;
  readonly noAnnouncement: string;
  readonly deceasedBlocked: string;
  readonly idNumberNotFound: string;
  readonly saveError: string;
  readonly savedNotice: string;
  readonly invoiceError: string;
  readonly closeWarning: string;
  readonly closeConfirm: string;
  readonly closeCancel: string;
}

type FuturePurchaseContent = PurchaseFormContent & { readonly messages: FuturePurchaseMessages };

const EDITABLE: string[] = ['idPart1', 'special', 'announcement', 'finca', 'municipality', 'vereda', 'deliveryDate', 'kilos'];
const REQUIRED: string[] = ['idPart1', 'special', 'announcement', 'kilos', 'deliveryDate'];
const FOCUS_ORDER: string[] = ['idPart1', 'special', 'announcement', 'finca', 'municipality', 'vereda', 'deliveryDate', 'kilos'];
/** Q5: Finca, Mpio y Vereda son opcionales. Enter pasa por ellos, pero no se bloquean al confirmarlos (prueba del
 *  10-nov: lo que se escribia ahi por error ya no se podia corregir) ni hacen esperar a la fecha de entrega. */
const OPTIONAL: string[] = ['finca', 'municipality', 'vereda'];
const GATE_ORDER = FOCUS_ORDER.filter((key) => !OPTIONAL.includes(key));

/** Texto de cada anuncio en la lista: número y Pr carga perg s (RN-21.4.4). */
function announcementLabel(a: Announcement): string {
  return `${stripAnnouncementPrefix(a.announcementNumber)} · ${formatDisplayNumber(a.basePriceLoad, 'currency')}`;
}

/**
 * "Ingresar Compras a Futuro" (RF-21.4, Form_COMPRAS A FUTURO.bas): sin cascada de precio ni factura - es un
 * compromiso de entrega futura, no una compra liquidada. Cédula busca al caficultor; Especial llena la lista de
 * Anuncio con los anuncios de hoy (RN-21.4.3; Access tomaba el último) y el elegido trae sus precios
 * informativos; Imprimir guarda una sola vez y abre el Manifiesto con 2 copias; Kilos_LostFocus simplemente copia
 * SaldoKilos = Kilos (arranca igual al anunciado, se descontará al facturar, RF-21.5). El backend
 * valida fallecido y pertenencia a programa (`GrowerService.requireProgramMembership`) - esos
 * mensajes reales se muestran tal cual los devuelve, no se repiten en el frontend.
 */
@Component({
  selector: 'app-future-purchase-form',
  standalone: true,
  imports: [CommonModule, PurchaseFormViewComponent, ConfirmDialogComponent],
  templateUrl: '../purchase-form.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FuturePurchaseFormComponent extends UnsavedCloseForm {
  private readonly content = inject(ContentService);
  private readonly service = inject(FuturePurchaseService);
  private readonly growerService = inject(GrowerService);
  private readonly authService = inject(AuthService);
  private readonly elementRef = inject(ElementRef);

  private readonly base = toSignal(this.content.loadJson<FuturePurchaseContent>('purchase-form-future'));
  readonly messages = computed<FuturePurchaseMessages | undefined>(() => this.base()?.messages);
  readonly announcementInfo = signal<Announcement | null>(null);
  /** Anuncios de hoy del especial elegido. */
  readonly todayAnnouncements = signal<Announcement[]>([]);
  /** true mientras se guarda: un segundo Imprimir no vuelve a enviar. */
  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly blockedByDeceased = signal(false);
  readonly savedNoticeOpen = signal(false);
  /** Compra futura no tiene formas de pago: la plantilla compartida la pide, pero nunca se llena. */
  readonly payment = signal<PurchasePayment | null>(null);
  private readonly tick = signal(0);
  private readonly announcementOptions = computed(() => this.todayAnnouncements().map(announcementLabel));

  override model: FormModel = {};
  private readonly locked = new Set<string>();
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
    this.model['agency'] = this.authService.agencyName() ?? '';
    // Foco en el primer campo apenas carga el contenido - ver mismo fix en dry-coffee-form.ts.
    effect(() => {
      if (this.base()) {
        this.focusField(FOCUS_ORDER[0]);
      }
    });
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
    // idPart1 se bloquea (locked) recien cuando lookupGrower confirma que la cedula existe - hasta
    // entonces sigue editable para poder corregirla (ver mensaje "no existe" en lookupGrower).
    if (key !== 'idPart1' && !OPTIONAL.includes(key)) {
      this.locked.add(key);
    }
    this.runSideEffects(key);
    this.tick.update((n) => n + 1);
    if (key !== 'idPart1') {
      this.advanceFocus(key);
    }
  }

  private runSideEffects(key: string): void {
    if (key === 'idPart1') {
      this.lookupGrower();
    } else if (key === 'special') {
      this.loadTodayAnnouncements();
    } else if (key === 'announcement') {
      this.pickAnnouncement();
    } else if (key === 'kilos') {
      // Kilos_LostFocus (Form_COMPRAS A FUTURO.bas): SaldoKilos = Kilos al momento de anunciar.
      this.model['balance'] = this.model['kilos'];
      this.locked.add('balance');
    }
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
        const firstNames = [grower.firstName, grower.secondName].filter(Boolean).join(' ');
        const lastNames = [grower.lastName, grower.secondLastName].filter(Boolean).join(' ');
        this.model['firstNames'] = firstNames;
        this.model['lastNames'] = lastNames;
        this.model['idType'] = grower.growerType;
        this.model['address'] = grower.address;
        this.model['idNumber'] = idNumber;
        this.model['fullName'] = [firstNames, lastNames].filter(Boolean).join(' ');
        this.locked.add('idPart1');
        // Mismo fix aplicado hoy en Seco/Verde/Pasilla/Otros/Cupos: si el dato migrado viene vacio,
        // no se bloquea, para que el cajero lo pueda completar a mano.
        ['firstNames', 'lastNames', 'idType', 'address', 'idNumber', 'fullName'].forEach((k) => {
          if ((this.model[k] ?? '').trim() !== '') {
            this.locked.add(k);
          }
        });
        this.lookupProgram();
        this.tick.update((n) => n + 1);
        this.advanceFocus('idPart1');
      },
      error: () => {
        this.errorMessage.set(this.messages()?.idNumberNotFound ?? 'Este número de cédula no existe.');
        this.tick.update((n) => n + 1);
      },
    });
  }

  private lookupProgram(): void {
    const idNumber = (this.model['idPart1'] ?? '').trim();
    this.growerService.findProgram(idNumber, this.model['special']).subscribe((program) => {
      this.model['program'] = program ? program.programa : '';
      if (program) {
        this.locked.add('program');
      } else {
        this.locked.delete('program');
      }
      this.tick.update((n) => n + 1);
    });
  }

  /** RN-21.4.3: el Especial llena la lista de Anuncio con los anuncios de hoy de ese especial. */
  private loadTodayAnnouncements(): void {
    const specialType = this.model['special'];
    this.service.todayAnnouncements(specialType).subscribe({
      next: (list) => {
        this.todayAnnouncements.set(list);
        if (list.length === 0) {
          this.unlockSpecial();
          this.errorMessage.set(this.messages()?.noAnnouncement ?? null);
        }
        this.tick.update((n) => n + 1);
      },
      error: () => {
        this.unlockSpecial();
        this.errorMessage.set(this.messages()?.noAnnouncement ?? null);
        this.tick.update((n) => n + 1);
      },
    });
  }

  /** RN-21.4.5: el anuncio elegido trae su fecha, sus precios, la bonificación y los costos. */
  private pickAnnouncement(): void {
    const picked = this.todayAnnouncements().find((a) => announcementLabel(a) === this.model['announcement']);
    if (!picked) {
      this.model['announcement'] = '';
      this.locked.delete('announcement');
      return;
    }
    this.announcementInfo.set(picked);
  }

  private unlockSpecial(): void {
    this.model['special'] = '';
    this.locked.delete('special');
    this.todayAnnouncements.set([]);
    this.focusField('special');
  }

  /** Extrae el mensaje real del backend (fallecido, no pertenece a programa) en vez de taparlo con
   *  un mensaje generico - esas validaciones viven solo en el backend. */
  private backendErrorMessage(err: unknown): string | null {
    const message = (err as { error?: { message?: string } })?.error?.message;
    return message ?? this.messages()?.saveError ?? null;
  }

  private buildRequest(): FuturePurchaseRequest | null {
    const info = this.announcementInfo();
    if (!info || !hasRequired(this.model, REQUIRED)) {
      return null;
    }
    return {
      idNumber: this.model['idPart1'],
      specialType: this.model['special'],
      announcementNumber: Number(stripAnnouncementPrefix(info.announcementNumber)),
      announcedKg: parseDisplayNumber(this.model['kilos']),
      deliveryDate: this.model['deliveryDate'],
      finca: this.model['finca'] || null,
      municipality: this.model['municipality'] || null,
      vereda: this.model['vereda'] || null,
    };
  }

  /** Comando36_Click: guarda el compromiso una sola vez y abre el Manifiesto (2 copias, RN-21.4.8). */
  print(): void {
    const request = this.canPrint() ? this.buildRequest() : null;
    if (!request) {
      return;
    }
    saveAndPrint(this, {
      name: 'Compras a Futuro',
      save: () => this.service.create(request),
      invoice: (purchase) => buildFuturePurchaseManifestoDocDefinition(purchase),
      messages: () => this.messages(),
      refresh: () => this.tick.update((n) => n + 1),
      // Fallecido o fuera del programa: el mensaje real del servidor, no uno genérico.
      saveError: (err) => {
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
    this.model = { agency: this.authService.agencyName() ?? '' };
    this.locked.clear();
    this.payment.set(null);
    this.announcementInfo.set(null);
    this.todayAnnouncements.set([]);
    this.errorMessage.set(null);
    this.blockedByDeceased.set(false);
    this.savedNoticeOpen.set(false);
    this.fieldCache.clear();
    this.tick.update((n) => n + 1);
  }

  protected override hasUnsavedInput(): boolean {
    return EDITABLE.some((k) => (this.model[k] ?? '').trim() !== '');
  }


  private canPrint(): boolean {
    return !!this.announcementInfo() && hasRequired(this.model, REQUIRED);
  }

  private buildContent(base: FuturePurchaseContent): PurchaseFormContent {
    const bmap = new Map<string, FormFieldDefinition>();
    const collect = (fields?: FormFieldDefinition[]) => fields?.forEach((f) => bmap.set(f.key, f));
    collect(base.topFields);
    collect(base.federationFields);
    collect(base.identificationFields);
    collect(base.contactFields);
    collect(base.qualityFields);
    collect(base.weightFields);

    const info = this.announcementInfo();
    const computedValues: Record<string, string | number> = {
      announcementDate: info?.announcementDate ?? '',
      basePriceLoad: info?.basePriceLoad ?? '',
      healthyUnitPrice: info?.healthyUnitPrice ?? '',
      defectiveUnitPrice: info?.defectiveUnitPrice ?? '',
      bonus: info?.bonus ?? '',
      costs: info?.costs ?? '',
      qualityIncrement: info?.qualityIncrement ?? '',
    };
    const READONLY_COMPUTED = Object.keys(computedValues);

    const field = (key: string): FormFieldDefinition => {
      const b = bmap.get(key)!;
      const patch: { readonly?: boolean; value?: string | number; options?: string[] } = {};
      if (key === 'announcement') {
        patch.options = this.announcementOptions();
      }
      if (READONLY_COMPUTED.includes(key)) {
        patch.readonly = true;
        patch.value = computedValues[key];
      } else if (b.readonly || this.locked.has(key)) {
        patch.readonly = true;
        patch.value = this.model[key] ?? '';
      } else if (!isSequentialFieldEnabled(key, GATE_ORDER, this.locked)) {
        patch.readonly = true;
      }
      const next: FormFieldDefinition = { ...b, ...patch };
      const prev = this.fieldCache.get(key);
      if (prev && prev.value === next.value && prev.readonly === next.readonly && prev.options === next.options) {
        return prev;
      }
      this.fieldCache.set(key, next);
      return next;
    };

    const row = (fields?: FormFieldDefinition[]) => fields?.map((f) => field(f.key));

    return {
      ...base,
      topFields: row(base.topFields)!,
      federationFields: row(base.federationFields),
      identificationFields: row(base.identificationFields)!,
      contactFields: row(base.contactFields),
      qualityFields: row(base.qualityFields),
      weightFields: row(base.weightFields),
      reprintButtonLabel: this.canPrint() ? base.reprintButtonLabel : undefined,
    };
  }

  /** Salto de foco del formulario (form-focus): mismo orden de siempre. */
  private readonly focusField = (key: string) => focusField(this.elementRef.nativeElement, key);
  private readonly advanceFocus = (afterKey: string) => advanceFocus(this.elementRef.nativeElement, FOCUS_ORDER, afterKey);
}
