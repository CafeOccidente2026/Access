import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';

import { PurchaseFormContent } from '../../../core/models';
import { ContentService } from '../../../core/services/content.service';
import { PurchaseFormViewComponent } from '../../../shared/ui';

/** Formulario de compra: carga su contenido y delega la vista al componente compartido. */
@Component({
  selector: 'app-other-coffee-form',
  standalone: true,
  imports: [CommonModule, PurchaseFormViewComponent],
  templateUrl: './other-coffee-form.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OtherCoffeeFormComponent {
  private readonly content = inject(ContentService);

  readonly data = toSignal(this.content.loadJson<PurchaseFormContent>('purchase-form-other'));
}
