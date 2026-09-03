import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { PurchaseFormContent } from '../../../core/models';
import { AccessWindowComponent } from '../access-window/access-window';
import { AppButtonComponent } from '../app-button/app-button';
import { FieldRowComponent } from '../field-row/field-row';
import { FormFieldComponent } from '../form-field/form-field';
import { PaymentPanelComponent } from '../payment-panel/payment-panel';
import { SectionDividerComponent } from '../section-divider/section-divider';

const THEME_BODY_CLASS: Record<PurchaseFormContent['theme'], string> = {
  sky: 'bg-panel-sky',
  green: 'bg-panel-green',
  teal: 'bg-panel-teal-strong',
  cyan: 'bg-panel-cyan',
};

/**
 * Vista generica para los formularios de compra (seco, otros, verde, pasilla
 * y consulta). Toda la variacion entre pantallas vive en el JSON de contenido;
 * este componente solo se encarga de la disposicion visual.
 */
@Component({
  selector: 'app-purchase-form-view',
  standalone: true,
  imports: [
    CommonModule,
    AccessWindowComponent,
    AppButtonComponent,
    FieldRowComponent,
    FormFieldComponent,
    PaymentPanelComponent,
    SectionDividerComponent,
  ],
  templateUrl: './purchase-form-view.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PurchaseFormViewComponent {
  @Input({ required: true }) content!: PurchaseFormContent;
  @Input() readOnly = false;
  @Output() readonly reprintPressed = new EventEmitter<void>();

  get bodyClass(): string {
    return THEME_BODY_CLASS[this.content.theme];
  }
}
