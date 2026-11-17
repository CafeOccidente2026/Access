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
import { announcementDisplay, formatDisplayNumber, parseDisplayNumber, stripAnnouncementPrefix } from '../../../shared/utils/number-format';
import { isSequentialFieldEnabled } from '../../../shared/utils/sequential-gate';
import { buildFuturePurchaseManifestoDocDefinition } from './future-purchase-invoice';
import { advanceFocus, focusField } from '../form-focus';
import { hasRequired, saveAndPrint } from '../purchase-print';
import { lockFilled } from '../seller-lookup';
import { PurchaseMessages, UnsavedCloseForm } from '../unsaved-close';
import { serverError } from '../../../shared/utils/server-error';
import { federationValues } from '../federation';
import { localToday } from '../../../shared/utils/local-date';
import { Observable, map } from 'rxjs';

type FormModel = Record<string, string>;

interface FuturePurchaseMessages extends PurchaseMessages {
  readonly noAnnouncement: string;
  readonly deceasedBlocked: string;
  readonly notInProgram: string;
  readonly deliveryDatePast: string;
  readonly kilosNotPositive: string;
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
  return `${announcementDisplay(a.announcementNumber)} · ${formatDisplayNumber(a.basePriceLoad, 'currency')}`;
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
      this.changeSpecial();
    } else if (key === 'announcement') {
      this.pickAnnouncement();
    } else if (key === 'deliveryDate' && this.model['deliveryDate'] < localToday()) {
      this.reject('deliveryDate', this.messages()?.deliveryDatePast);
    } else if (key === 'kilos') {
      if (!(parseDisplayNumber(this.model['kilos']) > 0)) {
        this.reject('kilos', this.messages()?.kilosNotPositive);
        return;
      }
      // Kilos_LostFocus (Form_COMPRAS A FUTURO.bas): SaldoKilos = Kilos al momento de anunciar.
      this.model['balance'] = this.model['kilos'];
      this.locked.add('balance');
    }
  }

  /** Hallazgos #10 y #16: el valor rechazado se borra, se avisa al momento y el foco vuelve a ese campo. */
  private reject(key: string, message: string | undefined): void {
    this.model[key] = '';
    this.locked.delete(key);
    this.retryKey = key;
    this.errorMessage.set(message ?? null);
  }

  /**
   * Hallazgos #10 y #11: al elegir el Especial se revisa de una vez el programa NESS (Texto34_AfterUpdate) y se llena
   * Información Federación con el programa de ese Especial. Si cambia el Especial, el anuncio se elige otra vez.
   */
  private changeSpecial(): void {
    if (this.announcementInfo() && this.announcementInfo()!.specialType !== this.model['special']) {
      this.model['announcement'] = '';
      this.locked.delete('announcement');
      this.announcementInfo.set(null);
    }
    const idNumber = (this.model['idPart1'] ?? '').trim();
    if (!idNumber) {
      this.loadTodayAnnouncements();
      return;
    }
    this.growerService.findProgram(idNumber, this.model['special']).subscribe((program) => {
      Object.assign(this.model, federationValues(program));
      if (program) {
        this.loadTodayAnnouncements();
      } else {
        this.unlockSpecial();
        this.errorMessage.set(this.messages()?.notInProgram ?? null);
      }
      this.tick.update((n) => n + 1);
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
        const firstNames = [grower.firstName, grower.secondName].filter(Boolean).join(' ');
        const lastNames = [grower.lastName, grower.secondLastName].filter(Boolean).join(' ');
        this.model['firstNames'] = firstNames;
        this.model['lastNames'] = lastNames;
        this.model['idType'] = grower.growerType;
        this.model['address'] = grower.address;
        this.locked.add('idPart1');
        lockFilled(this.model, this.locked, ['firstNames', 'lastNames', 'idType', 'address']);
        this.loadLastPlace(idNumber);
        this.tick.update((n) => n + 1);
        this.advanceFocus('idPart1');
      },
      error: () => {
        this.errorMessage.set(this.messages()?.idNumberNotFound ?? 'Este número de cédula no existe.');
        this.tick.update((n) => n + 1);
      },
    });
  }

  /** Hallazgo #14: Finca, Mpio y Vereda de la última compra a futuro de la cédula, si no se han digitado. */
  private loadLastPlace(idNumber: string): void {
    this.service.lastPlace(idNumber).subscribe((place) => {
      for (const key of OPTIONAL) {
        if (place && !(this.model[key] ?? '').trim()) {
          this.model[key] = place[key as keyof typeof place] ?? '';
        }
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
        } else {
          this.rememberPrice();
        }
        this.tick.update((n) => n + 1);
      },
      error: (err) => {
        this.unlockSpecial();
        this.errorMessage.set(serverError(err, this.messages()?.noAnnouncement));
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
    return serverError(err, this.messages()?.saveError);
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
    this.refocusRetry((k) => this.focusField(k));
  }

  /** Hallazgo #6: los anuncios de hoy del Especial elegido; si se publica otro, el precio cambió. */
  protected override currentAnnouncement(): Observable<string> | null {
    const special = this.model['special'];
    return special && this.todayAnnouncements().length
      ? this.service.todayAnnouncements(special).pipe(map((list) => list.map((a) => a.announcementNumber).join(',')))
      : null;
  }

  @HostListener('document:keydown.escape')
  reset(): void {
    this.model = { agency: this.authService.agencyName() ?? '' };
    this.locked.clear();
    this.retryKey = null;
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
    collect(base.netWeightFields);

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
      } else if (b.readonly || (this.locked.has(key) && !EDITABLE.includes(key))) {
        // Hallazgo #12: lo digitado (fecha incluida) se puede corregir hasta guardar; lo que trae el sistema no.
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
      netWeightFields: row(base.netWeightFields),
      reprintButtonLabel: this.canPrint() ? base.reprintButtonLabel : undefined,
    };
  }

  /** Salto de foco del formulario (form-focus): mismo orden de siempre. */
  private readonly focusField = (key: string) => focusField(this.elementRef.nativeElement, key);
  private readonly advanceFocus = (afterKey: string) => advanceFocus(this.elementRef.nativeElement, FOCUS_ORDER, afterKey);
}
