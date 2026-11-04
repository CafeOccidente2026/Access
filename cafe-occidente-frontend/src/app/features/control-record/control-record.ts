import { HttpErrorResponse } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { finalize, of } from 'rxjs';

import { FormFieldDefinition } from '../../core/models';
import { Agency } from '../../core/models/agency.model';
import { ControlRecordRequest, ControlRecordResponse } from '../../core/models/control-record.model';
import { AgencyService } from '../../core/services/agency.service';
import { AuthService } from '../../core/services/auth.service';
import { ContentService } from '../../core/services/content.service';
import { ControlRecordService } from '../../core/services/control-record.service';
import { AccessWindowComponent, ConfirmDialogComponent, FormFieldComponent } from '../../shared/ui';
import { parseDisplayNumber } from '../../shared/utils/number-format';
import { ControlRecordContent } from './control-record.model';

/** Pantalla ADMIN "Registro de Control": ver/crear/editar el ControlRecord de una agencia. Como en
 *  Access, abre ya con el registro de la agencia de la sesion cargado; el selector queda para
 *  crear/editar el de otra agencia. */
@Component({
  selector: 'app-control-record',
  standalone: true,
  imports: [CommonModule, AccessWindowComponent, ConfirmDialogComponent, FormFieldComponent],
  templateUrl: './control-record.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ControlRecordComponent {
  private readonly content = inject(ContentService);
  private readonly agencyService = inject(AgencyService);
  private readonly controlRecordService = inject(ControlRecordService);
  private readonly authService = inject(AuthService);

  readonly page = toSignal(this.content.loadJson<ControlRecordContent>('control-record'));
  readonly agencies = signal<Agency[]>([]);
  readonly agencyId = signal<number | null>(null);
  readonly isCreate = signal(false);
  readonly savedMessage = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);
  /** Hay un guardado en curso: los botones que guardan quedan deshabilitados y Enter no vuelve a enviar. */
  readonly saving = signal(false);
  private readonly model = signal<Record<string, string>>({});

  /** Recalculan solo cuando cambian sus dependencias (model/agencyId/page) - nunca en cada
   *  ciclo de deteccion de cambios, a diferencia de llamar un metodo simple desde *ngFor. */
  readonly leftFields = computed(() => this.buildFields(this.page()?.leftColumn));
  readonly rightFields = computed(() => this.buildFields(this.page()?.rightColumn));

  constructor() {
    // La lista primero: el prefijo sugerido de una agencia sin registro sale de ella. Con el permiso de
    // la pantalla (RF-02), quien no es Administrador solo ve su agencia.
    const sessionAgencyId = this.authService.agencyId();
    const agencies$ = this.authService.isAdmin()
      ? this.agencyService.list()
      : of<Agency[]>(sessionAgencyId === null ? [] : [{ id: sessionAgencyId, name: this.authService.agencyName() ?? '' }]);
    agencies$.subscribe((agencies) => {
      this.agencies.set(agencies);
      if (sessionAgencyId !== null) {
        this.onAgencyChange(String(sessionAgencyId));
      }
    });
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
    // Si se cambia de agencia antes de que llegue la respuesta, la respuesta vieja se descarta: si
    // no, los valores de la agencia anterior quedaban en el formulario de la nueva (y al guardar se
    // escribian sobre ella).
    this.controlRecordService.getByAgency(id).subscribe({
      next: (record) => {
        if (this.agencyId() !== id) {
          return;
        }
        this.isCreate.set(false);
        this.model.set(this.toModel(record));
      },
      error: (err: HttpErrorResponse) => {
        if (this.agencyId() !== id) {
          return;
        }
        if (err.status === 404) {
          this.isCreate.set(true);
          // D2: si la agencia traia prefijo de Access, el Prefijo aparece prellenado.
          const suggested = this.agencies().find((a) => a.id === id)?.suggestedPrefix;
          this.model.set(suggested ? { prefix: suggested } : {});
        } else {
          this.errorMessage.set(this.page()?.errorMessage ?? null);
        }
      },
    });
  }

  /** Sin esto, cada tecla recrea todos los campos (buildFields devuelve objetos nuevos) y el input
   *  en edicion pierde el foco. */
  trackByKey(_: number, field: FormFieldDefinition): string {
    return field.key;
  }

  onFieldValueChange(event: { key: string; value: string | number }): void {
    this.model.update((m) => ({ ...m, [event.key]: String(event.value) }));
  }

  save(): void {
    const id = this.agencyId();
    if (this.saving() || !id) {
      return;
    }
    this.savedMessage.set(null);
    this.errorMessage.set(null);
    const request = this.buildRequest();
    const call = this.isCreate()
      ? this.controlRecordService.create(id, request)
      : this.controlRecordService.update(id, request);
    this.saving.set(true);
    call.pipe(finalize(() => this.saving.set(false))).subscribe({
      next: (record) => {
        if (this.agencyId() !== id) {
          return; // ya se guardo contra su agencia; no pintarlo sobre la que se eligio despues
        }
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

  /** Agencias con Registro de Control activo: las que reciben el cafe verde de "Aplicar a todas". */
  readonly agenciesWithRecord = computed(() => this.agencies().filter((agency) => agency.hasControlRecord).length);
  readonly confirmingGreen = signal(false);
  /** "Aplicar a todas" es solo del Administrador; con el permiso de la pantalla (RF-02) se trabaja solo la propia agencia. */
  readonly isAdmin = this.authService.isAdmin();

  /** Area de compras, 2026-10-26: los 5 valores de cafe verde a todas las agencias, previa confirmacion. */
  askApplyGreenToAll(): void {
    if (!this.saving()) {
      this.confirmingGreen.set(true);
    }
  }

  applyGreenToAll(): void {
    this.confirmingGreen.set(false);
    if (this.saving()) {
      return;
    }
    const request = this.buildRequest();
    this.savedMessage.set(null);
    this.errorMessage.set(null);
    this.saving.set(true);
    this.controlRecordService
      .applyGreenCoffeeToAll({
        greenTotalStoredWeight: request.greenTotalStoredWeight,
        greenTotalHuskWeight: request.greenTotalHuskWeight,
        greenHealthyStoredWeight: request.greenHealthyStoredWeight,
        greenHealthyUnitPrice: request.greenHealthyUnitPrice,
        greenDefectiveUnitPrice: request.greenDefectiveUnitPrice,
      })
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (result) =>
          this.savedMessage.set(this.page()?.applyGreenDone.replace('{count}', String(result.agencies)) ?? null),
        error: (err: HttpErrorResponse) => this.errorMessage.set(err.error?.message ?? this.page()?.errorMessage ?? null),
      });
  }

  private toModel(record: ControlRecordResponse): Record<string, string> {
    const model: Record<string, string> = {};
    (Object.keys(record) as (keyof ControlRecordResponse)[])
      .filter((key) => !['id', 'agencyId', 'agencyName', 'active'].includes(key))
      .forEach((key) => {
        const value = record[key];
        // Decimales con coma, como los escribe el usuario: con punto ("0.5"), el campo numerico lo
        // toma como separador de miles al editar y "0.6" terminaba guardandose como 6.
        model[key] = typeof value === 'number' ? String(value).replace('.', ',') : String(value ?? '');
      });
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
      prefix: str('prefix'),
      costs: num('costs'),
      sampleSize: num('sampleSize'),
      excelsoKg: num('excelsoKg'),
      greenCoffeePercentage: num('greenCoffeePercentage'),
      associatePercentage: num('associatePercentage'),
      nonAssociateDiscount: num('nonAssociateDiscount'),
      trustedId: str('trustedId'),
      dianResolution: str('dianResolution'),
      resolutionDate: str('resolutionDate'),
      resolutionFrom: num('resolutionFrom'),
      resolutionTo: num('resolutionTo'),
      validity: num('validity'),
      specialtyThreshold: num('specialtyThreshold'),
      greenTotalStoredWeight: num('greenTotalStoredWeight'),
      greenTotalHuskWeight: num('greenTotalHuskWeight'),
      greenHealthyStoredWeight: num('greenHealthyStoredWeight'),
      greenHealthyUnitPrice: num('greenHealthyUnitPrice'),
      greenDefectiveUnitPrice: num('greenDefectiveUnitPrice'),
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
      readonly: !agencySelected || !!f.readonly,
    }));
  }
}
