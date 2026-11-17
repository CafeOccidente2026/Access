import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, of } from 'rxjs';

import { ResolutionAlert } from '../../../core/models/control-record.model';
import { AuthService } from '../../../core/services/auth.service';
import { ContentService } from '../../../core/services/content.service';
import { ControlRecordService } from '../../../core/services/control-record.service';
import { formatDate } from '../../utils/number-format';

interface ResolutionAlertContent {
  readonly upcoming: string;
  readonly today: string;
  readonly expired: string;
}

/** El texto de una alerta: por vencer, vence hoy o ya vencida. */
export function resolutionAlertText(alert: ResolutionAlert, c: ResolutionAlertContent): string {
  const template = alert.daysLeft > 0 ? c.upcoming : alert.daysLeft === 0 ? c.today : c.expired;
  return template
    .replace('{agency}', alert.agencyName)
    .replace('{date}', formatDate(alert.expirationDate))
    .replace('{days}', String(alert.daysLeft));
}

/**
 * R1 (17-nov): aviso de las resoluciones DIAN por vencer, en Acceso Principal y en el Menú Principal. Solo para el
 * Administrador (todas las agencias) y para quien tiene el permiso de Registro de Control (la suya).
 */
@Component({
  selector: 'app-resolution-alert',
  standalone: true,
  template: `
    @for (line of lines(); track line) {
      <p role="alert" class="mx-4 mt-2 rounded border border-red-300 bg-accent-yellow px-3 py-2 text-sm font-semibold text-red-800">
        {{ line }}
      </p>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ResolutionAlertComponent {
  private readonly auth = inject(AuthService);
  private readonly content = toSignal(inject(ContentService).loadJson<ResolutionAlertContent>('resolution-alert'));
  private readonly alerts = toSignal(
    this.auth.canAccess(['ADMINISTRADOR'], ['REGISTRO_CONTROL'])
      ? inject(ControlRecordService).resolutionAlerts().pipe(catchError(() => of([])))
      : of([]),
    { initialValue: [] as ResolutionAlert[] },
  );

  readonly lines = computed(() => {
    const c = this.content();
    return c ? this.alerts().map((a) => resolutionAlertText(a, c)) : [];
  });
}
