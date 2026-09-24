import { HttpErrorResponse } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';

import { FormFieldDefinition } from '../../core/models';
import { AnnouncementQuotaRequest, AnnouncementQuotaResponse } from '../../core/models/announcement-quota.model';
import { Announcement } from '../../core/models/dry-coffee-purchase.model';
import { AnnouncementQuotaService } from '../../core/services/announcement-quota.service';
import { AnnouncementService } from '../../core/services/announcement.service';
import { AuthService } from '../../core/services/auth.service';
import { ContentService } from '../../core/services/content.service';
import { AccessWindowComponent, FormFieldComponent } from '../../shared/ui';
import { parseDisplayNumber, stripAnnouncementPrefix } from '../../shared/utils/number-format';
import { QuotaAssignmentContent } from './quota-assignment.model';

/**
 * Pantalla ADMIN "Asignar Cupo a Anuncios" (Access "ACTUALIZA CUPOS"): tope total de kilos que se
 * puede recibir bajo un anuncio ya publicado. La agencia es la de la sesión (igual que en los 5
 * formularios de compra - nunca se elige); lo que el admin elige es el anuncio (`history()`, mismo
 * endpoint que ya usa "Actualizar Anuncio"). Entregados/Saldo se recalculan en cada consulta
 * (`AnnouncementQuotaService.get`), nunca se guardan - solo `Cupo` es editable.
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
  private readonly announcementService = inject(AnnouncementService);
  private readonly announcementQuotaService = inject(AnnouncementQuotaService);
  private readonly authService = inject(AuthService);

  readonly page = toSignal(this.content.loadJson<QuotaAssignmentContent>('quota-assignment'));
  readonly announcements = signal<Announcement[]>([]);
  readonly announcementNumber = signal<number | null>(null);
  readonly isCreate = signal(false);
  readonly savedMessage = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);
  private readonly model = signal<Record<string, string>>({});

  readonly leftFields = computed(() => this.buildFields(this.page()?.leftColumn));
  readonly rightFields = computed(() => this.buildFields(this.page()?.rightColumn));

  constructor() {
    this.model.set({ agency: this.authService.agencyName() ?? '' });
    this.announcementService.history().subscribe((list) => this.announcements.set(list));
  }

  onAnnouncementChange(displayNumber: string): void {
    this.savedMessage.set(null);
    this.errorMessage.set(null);
    const agency = this.authService.agencyName() ?? '';
    this.model.set({ agency });
    if (!displayNumber) {
      this.announcementNumber.set(null);
      return;
    }
    const announcement = this.announcements().find((a) => a.announcementNumber === displayNumber);
    const agencyId = this.authService.agencyId();
    const rawNumber = Number(stripAnnouncementPrefix(displayNumber));
    if (!announcement || !agencyId || Number.isNaN(rawNumber)) {
      return;
    }
    this.announcementNumber.set(rawNumber);
    this.model.set({
      agency,
      announcementDate: announcement.announcementDate,
      special: announcement.specialType,
      basePriceCps: String(announcement.basePriceLoad),
      defectStoredPrice: String(announcement.defectiveUnitPrice),
      healthyStoredPrice: String(announcement.healthyUnitPrice),
      bonus: String(announcement.bonus),
      costs: String(announcement.costs),
    });
    this.announcementQuotaService.get(agencyId, rawNumber).subscribe({
      next: (quota) => {
        this.isCreate.set(false);
        this.model.update((m) => ({ ...m, ...this.toModel(quota) }));
      },
      error: (err: HttpErrorResponse) => {
        if (err.status === 404) {
          this.isCreate.set(true);
        } else {
          this.errorMessage.set(this.page()?.errorMessage ?? null);
        }
      },
    });
  }

  onFieldValueChange(event: { key: string; value: string | number }): void {
    this.model.update((m) => ({ ...m, [event.key]: String(event.value) }));
  }

  save(): void {
    const agencyId = this.authService.agencyId();
    const number = this.announcementNumber();
    if (!agencyId || !number) {
      return;
    }
    this.savedMessage.set(null);
    this.errorMessage.set(null);
    const request: AnnouncementQuotaRequest = { assignedQuota: parseDisplayNumber(this.model()['quota']) };
    this.announcementQuotaService.assign(agencyId, number, request).subscribe({
      next: (quota) => {
        this.isCreate.set(false);
        this.model.update((m) => ({ ...m, ...this.toModel(quota) }));
        this.savedMessage.set(this.page()?.savedMessage ?? null);
      },
      error: (err: HttpErrorResponse) => {
        this.errorMessage.set(err.error?.message ?? this.page()?.errorMessage ?? null);
      },
    });
  }

  // Valores crudos, sin formatear: igual que el resto de los campos de solo lectura de la app
  // (ver computedValues en dry-coffee-form.ts), el formato Colombia lo aplica FormFieldComponent
  // segun el "type" del campo - formatear aca tambien duplicaba el formato y lo corrompia
  // (1700 -> "1.700" -> re-parseado como 1,7 -> mostrado "1,70").
  private toModel(quota: AnnouncementQuotaResponse): Record<string, string> {
    return {
      quota: String(quota.assignedQuota),
      deliveredKg: String(quota.deliveredKg),
      balance: String(quota.balance),
    };
  }

  private buildFields(base: FormFieldDefinition[] | undefined): FormFieldDefinition[] {
    if (!base) {
      return [];
    }
    const model = this.model();
    const announcementSelected = this.announcementNumber() !== null;
    return base.map((f) => ({
      ...f,
      value: model[f.key] ?? '',
      readonly: f.key === 'quota' ? !announcementSelected : true,
    }));
  }
}
