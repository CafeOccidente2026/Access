import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { PaymentPanelDefinition } from '../../../core/models';

/** Panel "Formas de Pago" que se repite en los formularios de compra. */
@Component({
  selector: 'app-payment-panel',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './payment-panel.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PaymentPanelComponent {
  @Input({ required: true }) definition!: PaymentPanelDefinition;
}
