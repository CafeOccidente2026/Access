import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';

import { PurchaseFormContent } from '../../core/models';
import { ContentService } from '../../core/services/content.service';
import { PurchaseFormViewComponent } from '../../shared/ui';

/** Pantalla "Consulta Compras": muestra un documento ya registrado, en modo lectura. */
@Component({
  selector: 'app-purchase-query',
  standalone: true,
  imports: [CommonModule, PurchaseFormViewComponent],
  templateUrl: './purchase-query.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PurchaseQueryComponent {
  private readonly content = inject(ContentService);

  readonly data = toSignal(this.content.loadJson<PurchaseFormContent>('purchase-query'));

  onReprint(): void {
    // La impresion del documento equivalente se implementara junto al backend.
  }
}
