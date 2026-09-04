import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';

import { PurchaseFormContent } from '../../../core/models';
import { ContentService } from '../../../core/services/content.service';
import { PurchaseFormViewComponent } from '../../../shared/ui';

/** Formulario "Ingresar Compras a Futuro": carga su contenido y delega la vista al componente compartido. */
@Component({
  selector: 'app-future-purchase-form',
  standalone: true,
  imports: [CommonModule, PurchaseFormViewComponent],
  templateUrl: './future-purchase-form.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FuturePurchaseFormComponent {
  private readonly content = inject(ContentService);

  readonly data = toSignal(this.content.loadJson<PurchaseFormContent>('purchase-form-future'));
}
