import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';

import { ContentService } from '../../../core/services/content.service';

/** R3: aviso mientras el Web Worker arma el PDF de un informe; la pantalla sigue respondiendo. */
@Component({
  selector: 'app-pdf-generating',
  standalone: true,
  template: `@if (active() && text(); as t) {
    <p class="text-sm text-slate-600" role="status" aria-live="polite">{{ t.generating }}</p>
  }`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PdfGeneratingComponent {
  readonly active = input(false);
  protected readonly text = toSignal(inject(ContentService).loadJson<{ generating: string }>('pdf'));
}
