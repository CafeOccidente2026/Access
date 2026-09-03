import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';

import { PurchaseFormContent } from '../../../core/models';
import { ContentService } from '../../../core/services/content.service';
import { PurchaseFormViewComponent } from '../../../shared/ui';

/** Formulario de compra: carga su contenido y delega la vista al componente compartido. */
@Component({
  selector: 'app-green-coffee-form',
  standalone: true,
  imports: [CommonModule, PurchaseFormViewComponent],
  templateUrl: './green-coffee-form.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GreenCoffeeFormComponent {
  private readonly content = inject(ContentService);

  readonly data = toSignal(this.content.loadJson<PurchaseFormContent>('purchase-form-green'));
}
