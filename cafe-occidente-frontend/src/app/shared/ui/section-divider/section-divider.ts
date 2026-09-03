import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

/** Barra divisoria de seccion (por ejemplo "LIQUIDACION DOCUMENTO..."). */
@Component({
  selector: 'app-section-divider',
  standalone: true,
  templateUrl: './section-divider.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SectionDividerComponent {
  @Input() text = '';
  @Input() tone: 'cyan' | 'neutral' = 'cyan';
}
