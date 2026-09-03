import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { StatusBarConfig } from '../../../core/models';

/** Barra de navegacion de registros, similar a la de los formularios de Access. */
@Component({
  selector: 'app-status-bar',
  standalone: true,
  templateUrl: './status-bar.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StatusBarComponent {
  @Input() config: StatusBarConfig = {};
}
