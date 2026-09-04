import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';

import { ContentService } from '../../core/services/content.service';
import { AccessWindowComponent, FormFieldComponent } from '../../shared/ui';
import { QuotaAssignmentContent } from './quota-assignment.model';

/** Pantalla "Asignar Cupo a Anuncios": cupo de compra asignado a cada anuncio. */
@Component({
  selector: 'app-quota-assignment',
  standalone: true,
  imports: [CommonModule, AccessWindowComponent, FormFieldComponent],
  templateUrl: './quota-assignment.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QuotaAssignmentComponent {
  private readonly content = inject(ContentService);

  readonly data = toSignal(this.content.loadJson<QuotaAssignmentContent>('quota-assignment'));
}
