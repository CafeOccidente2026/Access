import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';

import { Conductor } from '../../core/models/grower.model';
import { AuthService } from '../../core/services/auth.service';
import { GrowerService } from '../../core/services/grower.service';
import { AccessWindowComponent, AgencyPickerComponent, ComboboxComponent } from '../../shared/ui';

type Mode = 'choose' | 'new' | 'update';

/** 'YYYY-MM-DD' -> 'dd/mm/aaaa'. */
export function shortDate(isoDate: string | null): string {
  if (!isoDate) {
    return '';
  }
  const [year, month, day] = isoDate.slice(0, 10).split('-');
  return `${day}/${month}/${year}`;
}

/** Instante ISO -> 'dd/mm/aaaa hh:mm' en hora local. */
export function dateTime(instant: string | null): string {
  if (!instant) {
    return '';
  }
  const d = new Date(instant);
  const two = (n: number) => String(n).padStart(2, '0');
  return `${two(d.getDate())}/${two(d.getMonth() + 1)}/${d.getFullYear()} ${two(d.getHours())}:${two(d.getMinutes())}`;
}

/**
 * "Ingresar Conductores" (Form_MENUS INVENTARIOS.bas, Comando6): Access pregunta Si/No y abre
 * "Conductores" (alta, RecordSource Asociados, DataEntry) o "Conductores Actualizacion" (solo
 * Emp_Transp y Vehiculo editables, el resto Locked).
 */
@Component({
  selector: 'app-conductor-form',
  standalone: true,
  imports: [CommonModule, FormsModule, AccessWindowComponent, AgencyPickerComponent, ComboboxComponent],
  templateUrl: './conductor-form.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConductorFormComponent {
  private readonly growerService = inject(GrowerService);

  readonly mode = signal<Mode>('choose');
  readonly message = signal<string | null>(null);
  readonly error = signal<string | null>(null);
  readonly shortDate = shortDate;
  readonly dateTime = dateTime;

  // Alta
  readonly today = new Date().toLocaleDateString('en-CA');
  readonly agencyId = signal<number | null>(inject(AuthService).agencyId());
  readonly form = { idNumber: '', firstName: '', lastName: '', address: '', transportCompany: '', vehiclePlate: '' };

  // Actualizacion
  readonly matches = signal<Conductor[]>([]);
  readonly matchLabels = computed(() => this.matches().map((c) => this.label(c)));
  readonly selected = signal<Conductor | null>(null);
  transportCompany = '';
  vehiclePlate = '';

  choose(mode: Mode): void {
    this.mode.set(mode);
    this.message.set(null);
    this.error.set(null);
    this.selected.set(null);
    this.matches.set([]);
  }

  create(): void {
    const f = this.form;
    const agencyId = this.agencyId();
    this.message.set(null);
    this.error.set(null);
    if (!f.idNumber.trim() || !f.firstName.trim() || !f.lastName.trim() || !agencyId) {
      this.error.set('Cédula, Nombre, Apellidos y Agencia son obligatorios');
      return;
    }
    this.growerService
      .createConductor({
        idNumber: f.idNumber.trim(),
        firstName: f.firstName.trim(),
        secondName: null,
        lastName: f.lastName.trim(),
        secondLastName: null,
        agencyId,
        transportCompany: f.transportCompany.trim() || null,
        vehiclePlate: f.vehiclePlate.trim() || null,
        address: f.address.trim() || null,
      })
      .subscribe({
        next: () => {
          this.message.set('Conductor registrado');
          Object.assign(this.form, { idNumber: '', firstName: '', lastName: '', address: '', transportCompany: '', vehiclePlate: '' });
        },
        error: (err) => {
          const message = (err as { error?: { message?: string } })?.error?.message ?? '';
          this.error.set(
            message.includes('Ya existe')
              ? 'Esa cédula ya existe. Use "Actualizar uno ya existente".'
              : 'No se pudo registrar el conductor',
          );
        },
      });
  }

  /** Filtra mientras se escribe (el backend exige 3 digitos y devuelve max 10 conductores). */
  onIdNumberTyped(text: string): void {
    const chosen = this.matches().find((c) => this.label(c) === text);
    if (chosen) {
      this.open(chosen);
      return;
    }
    this.selected.set(null);
    const prefix = text.trim();
    if (prefix.length < 3) {
      this.matches.set([]);
      return;
    }
    this.growerService.searchConductors(prefix).subscribe((list) => this.matches.set(list));
  }

  save(): void {
    const conductor = this.selected();
    if (!conductor) {
      return;
    }
    this.message.set(null);
    this.error.set(null);
    this.growerService
      .updateConductor(conductor.idNumber, this.transportCompany.trim() || null, this.vehiclePlate.trim() || null)
      .subscribe({
        next: (updated) => {
          this.open(updated);
          this.message.set('Conductor actualizado');
        },
        error: () => this.error.set('No se pudo actualizar el conductor'),
      });
  }

  private open(conductor: Conductor): void {
    this.selected.set(conductor);
    this.transportCompany = conductor.transportCompany ?? '';
    this.vehiclePlate = conductor.vehiclePlate ?? '';
  }

  private label(c: Conductor): string {
    return conductorLabel(c);
  }
}

/** Asociaciones de Access no tienen apellido: se saltea el nulo en vez de mostrar "null". */
export function conductorLabel(c: Pick<Conductor, 'idNumber' | 'firstName' | 'lastName'>): string {
  return `${c.idNumber} - ${[c.firstName, c.lastName].filter(Boolean).join(' ')}`;
}
