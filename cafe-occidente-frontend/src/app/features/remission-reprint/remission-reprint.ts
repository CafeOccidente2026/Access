import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';

import { RemissionResponse } from '../../core/models/inventory.model';
import { AuthService } from '../../core/services/auth.service';
import { ContentService } from '../../core/services/content.service';
import { InventoryService } from '../../core/services/inventory.service';
import { AccessWindowComponent, AgencyPickerComponent, AppButtonComponent, ConfirmDialogComponent, FormFlowDirective } from '../../shared/ui';
import { renderPdfPreview } from '../../shared/utils/pdf-preview';
import { validReason } from '../../shared/utils/annulment-reason';
import { saveOnce } from '../../shared/utils/save-once';
import { loadLogoDataUrl } from '../purchase-forms/dry-coffee/dry-coffee-invoice';
import { buildRemissionDocDefinition } from '../remission-form/remission-invoice';
import { RemittanceFilesComponent } from '../remittance/remittance-files-panel';
import { RemittanceUnmarkComponent } from '../remittance/remittance-unmark';
import { serverError } from '../../shared/utils/server-error';

/** Textos de la pantalla (public/assets/data/remission-reprint.json). */
export interface RemissionReprintContent {
  readonly windowTitle: string;
  readonly agencyLabel: string;
  readonly numberLabel: string;
  readonly acceptLabel: string;
  readonly pdfTitle: string;
  readonly annulledLabel: string;
  readonly annulLabel: string;
  readonly annulConfirm: string;
  readonly yesLabel: string;
  readonly noLabel: string;
  readonly reasonLabel: string;
  readonly messages: Record<'missingNumber' | 'notFound' | 'pdfError' | 'searchError' | 'annulled' | 'annulError' | 'fromAccess'
    | 'reasonRequired', string>;
}

const fill = (text: string, number: string) => text.replace('{number}', number);

/**
 * "Reimprimir Remisión" (Form_MENUS INVENTARIOS.bas, Comando7 -> consulta "Remision", que pide el numero con un
 * InputBox): mismo PDF de 4 copias que al crear la remision. Respuestas del area (23-nov): tambien vuelve a bajar los
 * 4 archivos de su remesa y, al Administrador o a quien tenga el permiso, le deja anularla. El Administrador tambien
 * desmarca aca las remesas viejas sin remision.
 */
@Component({
  selector: 'app-remission-reprint',
  standalone: true,
  imports: [CommonModule, AccessWindowComponent, AgencyPickerComponent, AppButtonComponent, ConfirmDialogComponent,
    FormFlowDirective, RemittanceFilesComponent, RemittanceUnmarkComponent],
  templateUrl: './remission-reprint.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RemissionReprintComponent {
  private readonly inventoryService = inject(InventoryService);
  private readonly auth = inject(AuthService);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly logoDataUrlPromise = loadLogoDataUrl('assets/images/cafe-occidente-logo.png');

  readonly content = toSignal(inject(ContentService).loadJson<RemissionReprintContent>('remission-reprint'));
  readonly remissionNumber = signal('');
  readonly message = signal<string | null>(null);
  readonly pdfUrl = signal<SafeResourceUrl | null>(null);
  readonly found = signal<RemissionResponse | null>(null);
  readonly confirmingAnnul = signal(false);
  readonly annulling = signal(false);
  /** 26-nov: motivo obligatorio (minimo 10 caracteres); queda en el registro de anulaciones. */
  readonly annulReason = signal('');
  /** Desmarcar remesa (remesas viejas sin remision): solo el Administrador. */
  readonly isAdmin = computed(() => this.auth.isAdmin());
  readonly canAnnul = computed(() =>
    this.found()?.status === 'VIGENTE' && this.auth.canAccess(['ADMINISTRADOR'], ['ANULAR_REMISION']));

  search(): void {
    const number = this.remissionNumber().trim();
    const c = this.content();
    this.message.set(null);
    this.pdfUrl.set(null);
    this.found.set(null);
    if (!c) {
      return;
    }
    if (!number) {
      this.message.set(c.messages.missingNumber);
      return;
    }
    this.inventoryService.findRemissionByNumber(number).subscribe({
      next: (results) => {
        if (results.length === 0) {
          this.message.set(c.messages.notFound);
          return;
        }
        this.found.set(results[0]);
        this.logoDataUrlPromise
          .then((logo) =>
            renderPdfPreview(buildRemissionDocDefinition(results[0], logo), this.sanitizer, (url) => this.pdfUrl.set(url)),
          )
          .catch(() => this.message.set(c.messages.pdfError));
      },
      error: (err) => this.message.set(serverError(err, c.messages.searchError)),
    });
  }

  confirmText(c: RemissionReprintContent): string {
    return fill(c.annulConfirm, this.found()?.displayNumber ?? '');
  }

  /** Anular Remision: una salida historica de Access no se anula; se avisa en vez de pedir la confirmacion. */
  pressAnnul(): void {
    const remission = this.found();
    const c = this.content();
    if (remission?.fromAccess && c) {
      this.message.set(fill(c.messages.fromAccess, remission.displayNumber));
      return;
    }
    if (c && !validReason(this.annulReason())) {
      this.message.set(c.messages.reasonRequired);
      return;
    }
    this.confirmingAnnul.set(true);
  }

  annul(): void {
    const remission = this.found();
    const c = this.content();
    this.confirmingAnnul.set(false);
    if (!remission || !c) {
      return;
    }
    saveOnce(this.annulling, () => this.inventoryService.annulRemission(remission.id, this.annulReason()), {
      next: (annulled) => {
        this.found.set(annulled);
        this.annulReason.set('');
        this.message.set(fill(c.messages.annulled, annulled.displayNumber));
      },
      error: (err) => this.message.set(serverError(err, c.messages.annulError)),
    });
  }
}
