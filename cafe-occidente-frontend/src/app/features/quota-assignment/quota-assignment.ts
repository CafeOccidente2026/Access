import { HttpErrorResponse } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';

import { FormFieldDefinition } from '../../core/models';
import { AnnouncementQuotaResponse, QuotaCandidate } from '../../core/models/announcement-quota.model';
import { AnnouncementQuotaService } from '../../core/services/announcement-quota.service';
import { AuthService } from '../../core/services/auth.service';
import { ContentService } from '../../core/services/content.service';
import { AccessWindowComponent, FormFieldComponent } from '../../shared/ui';
import { parseDisplayNumber, stripAnnouncementPrefix } from '../../shared/utils/number-format';
import { saveOnce } from '../../shared/utils/save-once';
import { QuotaAssignmentContent } from './quota-assignment.model';

/** Texto de cada anuncio en la lista: número, especial y Pr Base Carg Perg Seco, para buscar por cualquiera (RN-21.1.2, RN-21.1.3). */
function optionLabel(candidate: QuotaCandidate): string {
  const a = candidate.announcement;
  return `${a.announcementNumber} · ${a.specialType} · ${a.basePriceLoad}`;
}

/**
 * "Asignar Cupo a Anuncios" (RF-21.1, Access "AN FUR HEUTE"). La lista la arma el servidor: anuncios de la
 * agencia de trabajo sin cupo, sin pasilla ni verde, con sus Kilos Entregados. Solo Cupo se digita; Saldo
 * lo calcula el servidor. Guardado, el anuncio sale de la lista y su cupo ya no se cambia (RN-21.1.9).
 */
@Component({
  selector: 'app-quota-assignment',
  standalone: true,
  imports: [CommonModule, AccessWindowComponent, FormFieldComponent],
  templateUrl: './quota-assignment.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QuotaAssignmentComponent {
  private readonly content = inject(ContentService);
  private readonly announcementQuotaService = inject(AnnouncementQuotaService);
  private readonly authService = inject(AuthService);

  readonly page = toSignal(this.content.loadJson<QuotaAssignmentContent>('quota-assignment'));
  readonly candidates = rxResource({ stream: () => this.announcementQuotaService.available() });
  readonly selected = signal<QuotaCandidate | null>(null);
  readonly saved = signal<AnnouncementQuotaResponse | null>(null);
  readonly saving = signal(false);
  readonly savedMessage = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);
  private readonly quota = signal('');

  private readonly options = computed(() => (this.candidates.hasValue() ? this.candidates.value() : []));

  readonly announcementField = computed<FormFieldDefinition>(() => {
    const selected = this.selected();
    return {
      key: 'announcement',
      label: this.page()?.announcementLabel ?? '',
      type: 'select',
      options: this.options().map(optionLabel),
      value: selected ? optionLabel(selected) : '',
    };
  });

  // Valores crudos: el formato de Colombia lo pone FormFieldComponent según el "type" del campo.
  private readonly model = computed((): Record<string, string> => {
    const agency = this.authService.agencyName() ?? '';
    const selected = this.selected();
    if (!selected) {
      return { agency };
    }
    const a = selected.announcement;
    const saved = this.saved();
    return {
      agency,
      announcementDate: a.announcementDate,
      special: a.specialType,
      basePriceCps: String(a.basePriceLoad),
      healthyStoredPrice: String(a.healthyUnitPrice),
      defectStoredPrice: String(a.defectiveUnitPrice),
      bonus: String(a.bonus),
      costs: String(a.costs),
      deliveredKg: String(saved?.deliveredKg ?? selected.deliveredKg),
      quota: saved ? String(saved.assignedQuota) : this.quota(),
      balance: saved ? String(saved.balance) : '',
    };
  });

  readonly leftFields = computed(() => this.buildFields(this.page()?.leftColumn));
  readonly rightFields = computed(() => this.buildFields(this.page()?.rightColumn));
  readonly canSave = computed(() => !!this.selected() && !this.saved());

  onAnnouncementChange(label: string): void {
    this.selected.set(this.options().find((c) => optionLabel(c) === label) ?? null);
    this.saved.set(null);
    this.quota.set('');
    this.savedMessage.set(null);
    this.errorMessage.set(null);
  }

  onFieldValueChange(event: { key: string; value: string | number }): void {
    if (event.key === 'quota') {
      this.quota.set(String(event.value));
    }
  }

  save(): void {
    const selected = this.selected();
    if (!selected || this.saved()) {
      return;
    }
    this.savedMessage.set(null);
    this.errorMessage.set(null);
    const number = Number(stripAnnouncementPrefix(selected.announcement.announcementNumber));
    const request = { assignedQuota: parseDisplayNumber(this.quota()) };
    saveOnce(this.saving, () => this.announcementQuotaService.assign(number, request), {
      next: (quota) => {
        this.saved.set(quota);
        this.candidates.value.update((list) => list?.filter((c) => c !== selected));
        this.savedMessage.set(this.page()?.savedMessage ?? null);
      },
      error: (err: HttpErrorResponse) => {
        this.errorMessage.set(err.error?.message ?? this.page()?.errorMessage ?? null);
      },
    });
  }

  private buildFields(base: FormFieldDefinition[] | undefined): FormFieldDefinition[] {
    const model = this.model();
    const quotaEditable = this.canSave();
    return (base ?? []).map((f) => ({
      ...f,
      value: model[f.key] ?? '',
      readonly: f.key === 'quota' ? !quotaEditable : true,
    }));
  }
}
