import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output, inject, signal } from '@angular/core';

import { Agency } from '../../../core/models/agency.model';
import { AgencyService } from '../../../core/services/agency.service';
import { AuthService } from '../../../core/services/auth.service';
import { ComboboxComponent } from '../combobox/combobox';

/**
 * Agencia de las pantallas de Inventarios: USER ve la de su sesion, fija y de solo lectura; ADMIN la
 * elige con el combobox de Compras (filtra mientras escribe). Emite el id solo cuando el texto
 * coincide con una agencia real, si no emite null. El backend igual fuerza la agencia para USER.
 */
@Component({
  selector: 'app-agency-picker',
  standalone: true,
  imports: [CommonModule, ComboboxComponent],
  template: `
    <app-combobox
      *ngIf="admin; else fixed"
      [options]="names()"
      [value]="auth.agencyName() ?? ''"
      [inputClass]="inputClass"
      (valueChange)="onValue($event)"
    ></app-combobox>
    <ng-template #fixed>
      <input type="text" readonly [class]="inputClass + ' bg-slate-100'" [value]="auth.agencyName() ?? ''" />
    </ng-template>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AgencyPickerComponent {
  readonly auth = inject(AuthService);
  readonly admin = this.auth.isAdmin();

  @Input() inputClass = 'w-full rounded border border-slate-300 px-2 py-1 text-sm';
  @Output() readonly agencyChange = new EventEmitter<number | null>();

  readonly names = signal<string[]>([]);
  private agencies: Agency[] = [];

  constructor() {
    if (this.admin) {
      inject(AgencyService).list().subscribe((list) => {
        this.agencies = list;
        this.names.set(list.map((a) => a.name));
      });
    }
  }

  onValue(text: string): void {
    this.agencyChange.emit(this.agencies.find((a) => a.name === text)?.id ?? null);
  }
}
