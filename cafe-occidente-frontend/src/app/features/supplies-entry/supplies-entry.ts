import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, ViewChild, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';

import { SuppliesEntryResponse } from '../../core/models/supplies.model';
import { AuthService } from '../../core/services/auth.service';
import { ContentService } from '../../core/services/content.service';
import { GrowerService } from '../../core/services/grower.service';
import { NavigationService } from '../../core/services/navigation.service';
import { SuppliesService } from '../../core/services/supplies.service';
import { AccessWindowComponent, AgencyPickerComponent, FormFieldComponent, FormFlowDirective } from '../../shared/ui';
import { formatThousands } from '../../shared/utils/number-format';
import { renderPdfPreview } from '../../shared/utils/pdf-preview';
import { buildLoanReceiptDoc } from './loan-receipt-pdf';
import { buildRequest, initialValues, missingField } from './supplies-entry-request';
import { SuppliesEntryContent, SuppliesField } from './supplies-entry.model';

/**
 * Altas de "MENUS SUMINISTROS" (Caja suministros, Ajustes Caja, Caja Menor Suministros, Gastos,
 * SUMINISTROS, Cheques Girados, Empaque Suministros, Prestamo Empaques). Una sola pantalla: campos,
 * etiquetas y reglas de cada formulario de Access salen de supplies-entry.json (data.screen).
 */
@Component({
  selector: 'app-supplies-entry',
  standalone: true,
  imports: [CommonModule, AccessWindowComponent, AgencyPickerComponent, FormFieldComponent, FormFlowDirective],
  templateUrl: './supplies-entry.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SuppliesEntryComponent {
  @ViewChild(FormFlowDirective) private readonly flow?: FormFlowDirective;
  private readonly suppliesService = inject(SuppliesService);
  private readonly growerService = inject(GrowerService);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly navigation = inject(NavigationService);
  private readonly screenKey: string = inject(ActivatedRoute).snapshot.data['screen'];

  readonly content = toSignal(inject(ContentService).loadJson<SuppliesEntryContent>('supplies-entry'));
  readonly screen = computed(() => this.content()?.screens[this.screenKey]);

  readonly agencyId = signal<number | null>(inject(AuthService).agencyId());
  private readonly edited = signal<Record<string, string> | null>(null);
  readonly values = computed(() => {
    const screen = this.screen();
    return this.edited() ?? (screen ? initialValues(screen) : {});
  });
  readonly growerNames = signal<{ first: string; last: string } | null>(null);
  readonly message = signal<string | null>(null);
  readonly error = signal(false);
  readonly saving = signal(false);
  readonly saved = signal<SuppliesEntryResponse | null>(null);
  readonly pdfUrl = signal<SafeResourceUrl | null>(null);

  /** Definicion para app-form-field con el valor actual (queda de solo lectura tras guardar el prestamo). */
  fieldWithValue(field: SuppliesField): SuppliesField {
    return { ...field, value: this.values()[field.key] ?? '', readonly: field.readonly || !!this.saved() };
  }

  set(key: string, value: string | number): void {
    this.edited.set({ ...this.values(), [key]: String(value) });
  }

  /** Cedula: solo digitos, con puntos de miles mientras se escribe. */
  idNumberDisplay(): string {
    return formatThousands(this.values()['idNumber'] ?? '');
  }

  onIdNumber(input: HTMLInputElement): void {
    const digits = input.value.replace(/\D/g, '');
    input.value = formatThousands(digits);
    this.set('idNumber', digits);
    this.growerNames.set(null);
  }

  lookupGrower(): void {
    const idNumber = this.values()['idNumber'];
    if (!this.screen()?.lookupGrower || !idNumber || this.saved()) {
      return;
    }
    this.growerService.findByIdNumber(idNumber).subscribe({
      next: (g) => {
        this.message.set(null);
        this.growerNames.set({
          first: `${g.firstName} ${g.secondName ?? ''}`.trim(),
          last: `${g.lastName} ${g.secondLastName ?? ''}`.trim(),
        });
      },
      error: () => this.fail(this.content()!.messages.growerNotFound),
    });
  }

  save(): void {
    const screen = this.screen()!;
    const messages = this.content()!.messages;
    const missing = missingField(screen, this.values());
    if (missing) {
      this.fail(messages.required.replace('{label}', missing));
      return;
    }
    if (!this.agencyId()) {
      this.fail(messages.chooseAgency);
      return;
    }
    this.saving.set(true);
    this.suppliesService.create(screen.endpoint, buildRequest(screen, this.values(), this.agencyId())).subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.error.set(false);
        this.message.set(messages.saved.replace('{id}', saved.transactionId));
        if (screen.printLoan) {
          this.saved.set(saved); // quedan los datos a la vista hasta imprimir, como en Access
        } else {
          this.edited.set(null);
          this.flow?.restart();
        }
      },
      error: (err: unknown) => {
        this.saving.set(false);
        this.fail((err as { error?: { message?: string } })?.error?.message ?? messages.saveError);
      },
    });
  }

  printLoan(): void {
    const values = this.values();
    const doc = buildLoanReceiptDoc(this.content()!.loanReceipt, this.saved()!, Number(values['quantity']), values['packagingType']);
    renderPdfPreview(doc, this.sanitizer, (url) => this.pdfUrl.set(url));
  }

  /** Otro prestamo despues de imprimir. */
  reset(): void {
    this.edited.set(null);
    this.saved.set(null);
    this.pdfUrl.set(null);
    this.growerNames.set(null);
    this.message.set(null);
    this.flow?.restart();
  }

  back(): void {
    this.navigation.goTo(this.screen()?.backRoute);
  }

  private fail(text: string): void {
    this.error.set(true);
    this.message.set(text);
  }
}
