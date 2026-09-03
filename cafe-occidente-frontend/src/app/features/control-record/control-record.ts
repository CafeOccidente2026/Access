import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';

import { ContentService } from '../../core/services/content.service';
import { AccessWindowComponent, FormFieldComponent, StatusBarComponent } from '../../shared/ui';
import { ControlRecordContent } from './control-record.model';

/** Pantalla "Registro de Control": parametros generales de la operacion de compras. */
@Component({
  selector: 'app-control-record',
  standalone: true,
  imports: [CommonModule, AccessWindowComponent, FormFieldComponent, StatusBarComponent],
  templateUrl: './control-record.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ControlRecordComponent {
  private readonly content = inject(ContentService);

  readonly data = toSignal(this.content.loadJson<ControlRecordContent>('control-record'));
}
