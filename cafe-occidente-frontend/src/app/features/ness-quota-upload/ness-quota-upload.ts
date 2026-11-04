import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, linkedSignal, signal } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';

import { NessQuotaUploadResult } from '../../core/models/vendor.model';
import { ContentService } from '../../core/services/content.service';
import { VendorService } from '../../core/services/vendor.service';
import { AccessWindowComponent, AppButtonComponent } from '../../shared/ui';
import { formatDisplayNumber } from '../../shared/utils/number-format';
import { saveOnce } from '../../shared/utils/save-once';

type Count = 'added' | 'changed' | 'unchanged' | 'removed' | 'rows';

export interface NessQuotaUploadContent {
  readonly windowTitle: string;
  readonly closeRoute: string;
  readonly instructions: string;
  readonly loadLabel: string;
  readonly checking: string;
  readonly errorsTitle: string;
  readonly warningsTitle: string;
  readonly previewTitle: string;
  readonly counts: Record<Count, string>;
  readonly applyLabel: string;
  readonly cancelLabel: string;
  readonly historyTitle: string;
  readonly historyColumns: string[];
  readonly historyEmpty: string;
  readonly messages: Record<'applied' | 'error' | 'previewError', string>;
}

/**
 * Actualiza Cupos NESS (HU-54, RF-28.4), solo el Administrador. En Access el boton corria la macro
 * "ImportarNess" (borraba NESS e importaba D:/NESS); aqui se carga el archivo desde el navegador, el servidor
 * muestra cuantos cupos cambian y Aplicar reemplaza la tabla en una transaccion y deja el historial.
 */
@Component({
  selector: 'app-ness-quota-upload',
  standalone: true,
  imports: [CommonModule, AccessWindowComponent, AppButtonComponent],
  templateUrl: './ness-quota-upload.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NessQuotaUploadComponent {
  private readonly service = inject(VendorService);

  readonly content = toSignal(inject(ContentService).loadJson<NessQuotaUploadContent>('ness-quota-upload'));
  readonly countKeys: readonly Count[] = ['added', 'changed', 'unchanged', 'removed', 'rows'];

  readonly file = signal<File | undefined>(undefined);
  readonly saving = signal(false);
  readonly message = signal<string | null>(null);

  /** Elegir un archivo pide la vista previa; sin archivo no hay nada que mostrar. */
  private readonly previewResource = rxResource({
    params: () => this.file(),
    stream: ({ params }) => this.service.previewNessUpload(params),
  });
  /** La vista previa, o lo que respondio Aplicar si el servidor rechazo el archivo. */
  readonly preview = linkedSignal<NessQuotaUploadResult | undefined>(() =>
    this.previewResource.hasValue() ? this.previewResource.value() : undefined);
  readonly checking = computed(() => this.previewResource.isLoading());
  readonly previewFailed = computed(() => this.previewResource.status() === 'error');
  readonly errors = computed(() => this.preview()?.errors ?? []);
  readonly warnings = computed(() => this.preview()?.warnings ?? []);
  readonly canApply = computed(() =>
    !!this.file() && !!this.preview() && this.errors().length === 0 && !this.checking() && !this.saving());

  private readonly historyVersion = signal(0);
  private readonly historyResource = rxResource({
    params: () => ({ version: this.historyVersion() }),
    stream: () => this.service.nessUploads(),
  });
  readonly history = computed(() => (this.historyResource.hasValue() ? this.historyResource.value() : []));

  choose(file: File | undefined): void {
    this.message.set(null);
    this.file.set(file);
  }

  apply(): void {
    const file = this.file();
    const content = this.content();
    if (!file || !content || !this.canApply()) {
      return;
    }
    this.message.set(null);
    saveOnce(this.saving, () => this.service.applyNessUpload(file), {
      next: (result) => {
        if (result.errors.length) {
          this.preview.set(result);
          return;
        }
        this.file.set(undefined);
        this.message.set(content.messages.applied);
        this.historyVersion.update((v) => v + 1);
      },
      error: () => this.message.set(content.messages.error),
    });
  }

  cancel(): void {
    this.choose(undefined);
  }

  count(value: number): string {
    return formatDisplayNumber(value, 'count');
  }
}
