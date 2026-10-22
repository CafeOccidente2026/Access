import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, EventEmitter, Input, OnInit, Output, inject, signal } from '@angular/core';

import { Agency } from '../../../core/models/agency.model';
import { AgencyService } from '../../../core/services/agency.service';
import { AuthService } from '../../../core/services/auth.service';
import { ComboboxComponent } from '../combobox/combobox';

/**
 * Campo Agencia de las pantallas: muestra la agencia de trabajo sin poder editarla, para todos los
 * roles (RN-05.1.3); el Administrador la cambia con el boton Agencias del menu principal. Solo con
 * selectable (Exportar Informacion, que se eliminara) el Administrador elige otra con el combobox;
 * emite el id cuando el texto coincide con una agencia real, si no emite null.
 */
@Component({
  selector: 'app-agency-picker',
  standalone: true,
  imports: [CommonModule, ComboboxComponent],
  template: `
    <app-combobox
      *ngIf="admin && selectable; else fixed"
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
export class AgencyPickerComponent implements OnInit {
  readonly auth = inject(AuthService);
  readonly admin = this.auth.isAdmin();

  @Input() inputClass = 'w-full rounded border border-slate-300 px-2 py-1 text-sm';
  @Input() selectable = false;
  @Output() readonly agencyChange = new EventEmitter<number | null>();

  readonly names = signal<string[]>([]);
  private agencies: Agency[] = [];

  private readonly agencyService = inject(AgencyService);

  ngOnInit(): void {
    if (this.admin && this.selectable) {
      this.agencyService.list().subscribe((list) => {
        this.agencies = list;
        this.names.set(list.map((a) => a.name));
      });
    }
  }

  onValue(text: string): void {
    this.agencyChange.emit(this.agencies.find((a) => a.name === text)?.id ?? null);
  }
}
