import { ChangeDetectionStrategy, Component, Input, inject } from '@angular/core';

import { AuthService } from '../../../core/services/auth.service';

/**
 * Campo Agencia de las pantallas: muestra la agencia de trabajo sin poder editarla, para todos los
 * roles (RN-05.1.3); el Administrador la cambia con el boton Agencias del menu principal.
 */
@Component({
  selector: 'app-agency-picker',
  standalone: true,
  template: `<input type="text" readonly [class]="inputClass + ' bg-slate-100'" [value]="auth.agencyName() ?? ''" />`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AgencyPickerComponent {
  readonly auth = inject(AuthService);

  @Input() inputClass = 'w-full rounded border border-slate-300 px-2 py-1 text-sm';
}
