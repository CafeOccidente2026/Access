import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';

import { PurchaseFormContent } from '../../../core/models';
import { ContentService } from '../../../core/services/content.service';
import { PurchaseFormViewComponent } from '../../../shared/ui';

/** Formulario "Facturar Anuncios con Cupos": carga su contenido y delega la vista al componente compartido. */
@Component({
  selector: 'app-quota-billing-form',
  standalone: true,
  imports: [CommonModule, PurchaseFormViewComponent],
  templateUrl: './quota-billing-form.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QuotaBillingFormComponent {
  private readonly content = inject(ContentService);

  readonly data = toSignal(this.content.loadJson<PurchaseFormContent>('purchase-form-quota-billing'));
}
