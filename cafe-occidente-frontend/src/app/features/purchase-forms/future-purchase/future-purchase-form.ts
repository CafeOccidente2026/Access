import { CommonModule, Location } from '@angular/common';
import { ChangeDetectionStrategy, Component, ElementRef, HostListener, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import pdfMake from 'pdfmake/build/pdfmake';
import pdfFonts from 'pdfmake/build/vfs_fonts';

import { FormFieldDefinition, PurchaseFormContent } from '../../../core/models';
import { Announcement, Fund } from '../../../core/models/dry-coffee-purchase.model';
import { FuturePurchaseRequest, FuturePurchaseResponse } from '../../../core/models/future-purchase.model';
import { AnnouncementService } from '../../../core/services/announcement.service';
import { AuthService } from '../../../core/services/auth.service';
import { ContentService } from '../../../core/services/content.service';
import { FuturePurchaseService } from '../../../core/services/future-purchase.service';
import { GrowerService } from '../../../core/services/grower.service';
import { ConfirmDialogComponent, PurchaseFormViewComponent } from '../../../shared/ui';
import { parseDisplayNumber } from '../../../shared/utils/number-format';
import { isSequentialFieldEnabled } from '../../../shared/utils/sequential-gate';
import { buildFuturePurchaseManifestoDocDefinition } from './future-purchase-invoice';

type FormModel = Record<string, string>;

interface FuturePurchaseMessages {
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

type FuturePurchaseContent = PurchaseFormContent & { readonly messages: FuturePurchaseMessages };

/** Fondo fijo en RP: el formulario real (Form_COMPRAS A FUTURO.bas) no tiene Cuadro_combinado37
 *  (Fondo) en ningun lado - el anuncio vigente se busca directo por agencia+Especial, mismo patron
 *  que Verde/Pasilla. */
const RP_FUND_CODE = 'RP';

const EDITABLE: string[] = ['idPart1', 'special', 'qualityIncrement', 'finca', 'municipality', 'vereda', 'deliveryDate', 'kilos'];
const REQUIRED: string[] = ['idPart1', 'special', 'kilos', 'deliveryDate'];
const FOCUS_ORDER: string[] = ['idPart1', 'special', 'qualityIncrement', 'finca', 'municipality', 'vereda', 'deliveryDate', 'kilos'];

/**
 * "Ingresar Compras a Futuro" (Form_COMPRAS A FUTURO.bas): sin cascada de precio ni factura - es un
 * compromiso de entrega futura, no una compra liquidada. Cédula busca al caficultor; Especial trae
 * el anuncio vigente (Fondo fijo RP) con sus precios informativos; Kilos_LostFocus simplemente copia
 * SaldoKilos = Kilos (arranca igual al anunciado, se va descontando con Fertifuturo). El backend
 * valida fallecido y pertenencia a programa (`GrowerService.requireProgramMembership`) - esos
 * mensajes reales se muestran tal cual los devuelve, no se repiten en el frontend.
 */
@Component({
  selector: 'app-future-purchase-form',
  standalone: true,
  imports: [CommonModule, PurchaseFormViewComponent, ConfirmDialogComponent],
  templateUrl: './future-purchase-form.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FuturePurchaseFormComponent {
  private readonly content = inject(ContentService);
  private readonly service = inject(FuturePurchaseService);
  private readonly announcementService = inject(AnnouncementService);
  private readonly growerService = inject(GrowerService);
  private readonly authService = inject(AuthService);
  private readonly location = inject(Location);
  private readonly elementRef = inject(ElementRef);

  private readonly base = toSignal(this.content.loadJson<FuturePurchaseContent>('purchase-form-future'));
  readonly messages = computed<FuturePurchaseMessages | undefined>(() => this.base()?.messages);
  readonly announcementInfo = signal<Announcement | null>(null);
  readonly result = signal<FuturePurchaseResponse | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly blockedByDeceased = signal(false);
  readonly savedNoticeOpen = signal(false);
  private readonly tick = signal(0);

  model: FormModel = {};
  private readonly locked = new Set<string>();
  private readonly fieldCache = new Map<string, FormFieldDefinition>();
  private rpFundId: number | null = null;

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
    this.model['agency'] = this.authService.agencyName() ?? '';
    this.announcementService.funds().subscribe((list) => {
      this.rpFundId = list.find((f: Fund) => f.code === RP_FUND_CODE)?.id ?? null;
    });
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
    if (key !== 'idPart1') {
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
      this.loadAnnouncementInfo();
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

  /** Cuadro_combinado30_AfterUpdate: trae el anuncio vigente (Fondo RP fijo) con sus precios
   *  informativos - solo para mostrar en pantalla, el backend los vuelve a resolver el mismo al
   *  guardar (no viajan en el request). */
  private loadAnnouncementInfo(): void {
    const agencyId = this.authService.agencyId();
    const specialType = this.model['special'];
    if (!agencyId || !this.rpFundId || !specialType) {
      return;
    }
    this.announcementService.latest(agencyId, this.rpFundId, specialType).subscribe({
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

  /** Extrae el mensaje real del backend (fallecido, no pertenece a programa) en vez de taparlo con
   *  un mensaje generico - esas validaciones viven solo en el backend. */
  private backendErrorMessage(err: unknown): string | null {
    const message = (err as { error?: { message?: string } })?.error?.message;
    return message ?? this.messages()?.saveError ?? null;
  }

  private buildRequest(): FuturePurchaseRequest | null {
    const agencyId = this.authService.agencyId();
    if (!agencyId || !REQUIRED.every((k) => (this.model[k] ?? '').trim() !== '')) {
      return null;
    }
    return {
      agencyId,
      idNumber: this.model['idPart1'],
      specialType: this.model['special'],
      announcedKg: parseDisplayNumber(this.model['kilos']),
      deliveryDate: this.model['deliveryDate'],
      finca: this.model['finca'] || null,
      municipality: this.model['municipality'] || null,
      vereda: this.model['vereda'] || null,
    };
  }

  /** Comando36_Click: guarda el compromiso y abre el Manifiesto (la carta-compromiso). */
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
        this.printManifesto(res);
        this.tick.update((n) => n + 1);
      },
      error: (err) => {
        console.error('Error al registrar compromiso Compras a Futuro:', err?.error ?? err);
        this.errorMessage.set(this.backendErrorMessage(err));
        this.tick.update((n) => n + 1);
      },
    });
  }

  private printManifesto(purchase: FuturePurchaseResponse): void {
    const docDefinition = buildFuturePurchaseManifestoDocDefinition(purchase);
    pdfMake.vfs = pdfFonts;
    pdfMake.createPdf(docDefinition).open();
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
    this.announcementInfo.set(null);
    this.result.set(null);
    this.errorMessage.set(null);
    this.blockedByDeceased.set(false);
    this.savedNoticeOpen.set(false);
    this.fieldCache.clear();
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
    return !this.result() && !!this.announcementInfo() && REQUIRED.every((k) => (this.model[k] ?? '').trim() !== '');
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
      announcement: info?.announcementNumber ?? '',
      announcementDate: info?.announcementDate ?? '',
      basePriceLoad: info?.basePriceLoad ?? '',
      healthyUnitPrice: info?.healthyUnitPrice ?? '',
      defectiveUnitPrice: info?.defectiveUnitPrice ?? '',
      bonus: info?.bonus ?? '',
      costs: info?.costs ?? '',
    };
    const READONLY_COMPUTED = Object.keys(computedValues);

    const field = (key: string): FormFieldDefinition => {
      const b = bmap.get(key)!;
      const patch: { readonly?: boolean; value?: string | number } = {};
      if (READONLY_COMPUTED.includes(key)) {
        patch.readonly = true;
        patch.value = computedValues[key];
      } else if (b.readonly || this.locked.has(key)) {
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
      federationFields: row(base.federationFields),
      identificationFields: row(base.identificationFields)!,
      contactFields: row(base.contactFields),
      qualityFields: row(base.qualityFields),
      weightFields: row(base.weightFields),
      reprintButtonLabel: this.canPrint() ? base.reprintButtonLabel : undefined,
    };
  }
}
