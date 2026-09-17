import { HttpErrorResponse } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';

import { FormFieldDefinition } from '../../core/models';
import { Agency } from '../../core/models/agency.model';
import { ControlRecordRequest, ControlRecordResponse } from '../../core/models/control-record.model';
import { AgencyService } from '../../core/services/agency.service';
import { ContentService } from '../../core/services/content.service';
import { ControlRecordService } from '../../core/services/control-record.service';
import { AccessWindowComponent, FormFieldComponent } from '../../shared/ui';
import { parseDisplayNumber } from '../../shared/utils/number-format';
import { ControlRecordContent } from './control-record.model';

/** Pantalla ADMIN "Registro de Control": ver/crear/editar el ControlRecord de una agencia. */
@Component({
  selector: 'app-control-record',
  standalone: true,
  imports: [CommonModule, AccessWindowComponent, FormFieldComponent],
  templateUrl: './control-record.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ControlRecordComponent {
  private readonly content = inject(ContentService);
  private readonly agencyService = inject(AgencyService);
  private readonly controlRecordService = inject(ControlRecordService);

  readonly page = toSignal(this.content.loadJson<ControlRecordContent>('control-record'));
  readonly agencies = signal<Agency[]>([]);
  readonly agencyId = signal<number | null>(null);
  readonly isCreate = signal(false);
  readonly savedMessage = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);
  private readonly model = signal<Record<string, string>>({});

  /** Recalculan solo cuando cambian sus dependencias (model/agencyId/page) - nunca en cada
   *  ciclo de deteccion de cambios, a diferencia de llamar un metodo simple desde *ngFor. */
  readonly leftFields = computed(() => this.buildFields(this.page()?.leftColumn));
  readonly rightFields = computed(() => this.buildFields(this.page()?.rightColumn));

  constructor() {
    this.agencyService.list().subscribe((agencies) => this.agencies.set(agencies));
  }

  onAgencyChange(value: string): void {
    this.savedMessage.set(null);
    this.errorMessage.set(null);
    const id = value ? Number(value) : null;
    this.agencyId.set(id);
    this.model.set({});
    if (id === null) {
      return;
    }
    this.controlRecordService.getByAgency(id).subscribe({
      next: (record) => {
        this.isCreate.set(false);
        this.model.set(this.toModel(record));
      },
      error: (err: HttpErrorResponse) => {
        if (err.status === 404) {
          this.isCreate.set(true);
          this.model.set({});
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
    const id = this.agencyId();
    if (!id) {
      return;
    }
    this.savedMessage.set(null);
    this.errorMessage.set(null);
    const request = this.buildRequest();
    const call = this.isCreate()
      ? this.controlRecordService.create(id, request)
      : this.controlRecordService.update(id, request);
    call.subscribe({
      next: (record) => {
        this.isCreate.set(false);
        this.model.set(this.toModel(record));
        this.savedMessage.set(this.page()?.savedMessage ?? null);
      },
      error: (err: HttpErrorResponse) => {
        const fieldError = err.error?.fieldErrors && Object.values(err.error.fieldErrors)[0];
        const specific = (fieldError as string | undefined) ?? err.error?.message;
        this.errorMessage.set(specific ?? this.page()?.errorMessage ?? null);
      },
    });
  }

  private toModel(record: ControlRecordResponse): Record<string, string> {
    const model: Record<string, string> = {};
    (Object.keys(record) as (keyof ControlRecordResponse)[])
      .filter((key) => !['id', 'agencyId', 'agencyName', 'active'].includes(key))
      .forEach((key) => (model[key] = String(record[key] ?? '')));
    return model;
  }

  private buildRequest(): ControlRecordRequest {
    const model = this.model();
    const num = (key: string) => parseDisplayNumber(model[key]);
    const str = (key: string) => model[key] ?? '';
    return {
      controlNumber: num('controlNumber'),
      baseFactor: num('baseFactor'),
      baseWithholding: num('baseWithholding'),
      baseLoad: num('baseLoad'),
      withholdingPercentage: num('withholdingPercentage'),
      baseHusk: num('baseHusk'),
      avgHuskPercentage: num('avgHuskPercentage'),
      purchasePoint: str('purchasePoint'),
      prefix: str('prefix'),
      costs: num('costs'),
      sampleSize: num('sampleSize'),
      excelsoKg: num('excelsoKg'),
      greenCoffeePercentage: num('greenCoffeePercentage'),
      specialtyThreshold: num('specialtyThreshold'),
      associatePercentage: num('associatePercentage'),
      nonAssociateDiscount: num('nonAssociateDiscount'),
      trustedId: str('trustedId'),
      dianResolution: str('dianResolution'),
      resolutionDate: str('resolutionDate'),
      resolutionFrom: num('resolutionFrom'),
      resolutionTo: num('resolutionTo'),
      validity: num('validity'),
    };
  }

  private buildFields(base: FormFieldDefinition[] | undefined): FormFieldDefinition[] {
    if (!base) {
      return [];
    }
    const model = this.model();
    const agencySelected = this.agencyId() !== null;
    return base.map((f) => ({
      ...f,
      value: model[f.key] ?? '',
      readonly: !agencySelected,
    }));
  }
}
