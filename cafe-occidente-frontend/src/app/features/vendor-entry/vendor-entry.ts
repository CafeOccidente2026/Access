import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, ViewChild, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';

import { ContentService } from '../../core/services/content.service';
import { NavigationService } from '../../core/services/navigation.service';
import { VendorService } from '../../core/services/vendor.service';
import { AccessWindowComponent, AgencyPickerComponent, FormFlowDirective } from '../../shared/ui';
import { saveOnce } from '../../shared/utils/save-once';
import { shortDate } from '../conductor-form/conductor-form';
import { buildVendorRequest, validationError } from './vendor-entry-request';
import { VendorEntryContent, VendorKind } from './vendor-entry.model';

/**
 * Catalogar Terceros (RF-28.1, antes "Ingresar Vendedores"): el dialogo elige Persona Juridica (NIT,
 * Razon Social y Clasificacion) o Persona Natural; Fecha Afiliacion = hoy y Tipo = C fijos. Tras guardar el formulario queda en blanco para el
 * siguiente, como hacia Access al cerrar y reabrir el formulario.
 */
@Component({
  selector: 'app-vendor-entry',
  standalone: true,
  imports: [CommonModule, AccessWindowComponent, AgencyPickerComponent, FormFlowDirective],
  templateUrl: './vendor-entry.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class VendorEntryComponent {
  @ViewChild(FormFlowDirective) private readonly flow?: FormFlowDirective;
  private readonly vendorService = inject(VendorService);
  private readonly navigation = inject(NavigationService);

  readonly content = toSignal(inject(ContentService).loadJson<VendorEntryContent>('vendor-entry'));
  readonly today = shortDate(new Date().toLocaleDateString('en-CA'));

  readonly choice = signal<VendorKind | null>(null);
  readonly kind = signal<VendorKind | null>(null);
  readonly fields = computed(() => {
    const kind = this.kind();
    return kind ? (this.content()?.forms[kind] ?? []) : [];
  });
  readonly values = signal<Record<string, string>>({});
  readonly message = signal<string | null>(null);
  readonly error = signal(false);
  readonly saving = signal(false);

  selected(): VendorKind {
    return this.choice() ?? this.content()!.dialog.defaultOption;
  }

  accept(event: Event): void {
    event.preventDefault();
    this.kind.set(this.selected());
  }

  set(key: string, value: string): void {
    this.values.update((v) => ({ ...v, [key]: value }));
  }

  save(): void {
    const content = this.content()!;
    const kind = this.kind()!;
    const problem = validationError(this.fields(), this.values(), content.messages);
    this.error.set(!!problem);
    this.message.set(problem);
    if (problem) {
      return;
    }
    saveOnce(this.saving, () => this.vendorService.create(buildVendorRequest(kind, this.fields(), this.values())), {
      next: (saved) => {
        this.values.set({});
        this.flow?.restart();
        this.message.set(content.messages.saved.replace('{id}', saved.idNumber));
      },
      error: (err) => {
        this.error.set(true);
        const duplicate = String(err?.error?.message ?? '').includes('Ya existe');
        this.message.set(duplicate ? content.messages.duplicate : content.messages.error);
      },
    });
  }

  back(): void {
    this.navigation.goTo(this.content()?.backRoute);
  }
}
