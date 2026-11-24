import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, resource, signal } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';

import { ContentService } from '../../core/services/content.service';
import { RemittanceService } from '../../core/services/remittance.service';
import { AppButtonComponent } from '../../shared/ui/app-button/app-button';
import { ConfirmDialogComponent } from '../../shared/ui/confirm-dialog/confirm-dialog';
import { RemittanceWorkbook, remittanceWorkbooks, saveBlob, workbookBlobs } from './remittance-files';

/** Textos de los botones (public/assets/data/remittance-files.json). */
export interface RemittanceFilesContent {
  readonly filesLabel: string;
  readonly fileLabels: Record<string, string>;
  readonly downloadAllLabel: string;
  readonly messages: Record<'filesError' | 'downloadAllHint', string>;
}

/**
 * Los 4 archivos de la remesa (ParaOperaciones, ParaOpen, GeneraPlano y ParaAlmacafe), un boton por archivo y uno
 * para las 4. Lo usan Registrar Salidas al guardar y Reimprimir Remision al buscar.
 */
@Component({
  selector: 'app-remittance-files',
  standalone: true,
  imports: [CommonModule, AppButtonComponent, ConfirmDialogComponent],
  template: `
    <div *ngIf="content() as c" class="flex flex-wrap items-center gap-2 text-sm">
      <span class="font-medium">{{ c.filesLabel }}:</span>
      <app-button *ngFor="let book of books()" [label]="c.fileLabels[book.sheet] ?? book.sheet" [disabled]="!filesReady()" (pressed)="download(book)"></app-button>
      <app-button *ngIf="books().length" [label]="c.downloadAllLabel" [disabled]="!filesReady()" (pressed)="downloadAll()"></app-button>
    </div>
    <app-confirm-dialog *ngIf="message() as m" [message]="m" (confirmed)="message.set(null)"></app-confirm-dialog>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RemittanceFilesComponent {
  private readonly service = inject(RemittanceService);

  readonly remittanceId = input.required<number>();
  readonly content = toSignal(inject(ContentService).loadJson<RemittanceFilesContent>('remittance-files'));
  readonly message = signal<string | null>(null);
  /** Descarga de un archivo; los tests la reemplazan. */
  saveFile: (fileName: string, blob: Blob) => void = saveBlob;

  /** Las filas de los 4 archivos, cargadas antes del clic. */
  private readonly filesResource = rxResource({
    params: () => this.remittanceId(),
    stream: ({ params }) => this.service.files(params),
  });
  readonly books = computed(() => (this.filesResource.hasValue() ? remittanceWorkbooks(this.filesResource.value()) : []));
  private readonly blobsResource = resource({
    params: () => (this.books().length ? this.books() : undefined),
    loader: ({ params }) => workbookBlobs(params),
  });
  readonly filesReady = computed(() => this.blobsResource.hasValue());

  /** Un archivo por clic y sin esperas: Chrome bloquea las descargas que no salen directo de un clic. */
  download(book: RemittanceWorkbook): void {
    const blob = this.blobsResource.hasValue() ? this.blobsResource.value().get(book.fileName) : undefined;
    if (blob) {
      this.saveFile(book.fileName, blob);
    } else {
      this.message.set(this.content()?.messages.filesError ?? null);
    }
  }

  /**
   * Los 4 en el mismo clic. Chrome pide permiso la primera vez que un sitio baja varios archivos y la pagina no se
   * entera de la respuesta: por eso el aviso se muestra siempre (Permitir, o los botones de cada archivo).
   */
  downloadAll(): void {
    const blobs = this.blobsResource.hasValue() ? this.blobsResource.value() : undefined;
    if (!blobs) {
      this.message.set(this.content()?.messages.filesError ?? null);
      return;
    }
    for (const book of this.books()) {
      const blob = blobs.get(book.fileName);
      if (blob) {
        this.saveFile(book.fileName, blob);
      }
    }
    this.message.set(this.content()?.messages.downloadAllHint ?? null);
  }
}
