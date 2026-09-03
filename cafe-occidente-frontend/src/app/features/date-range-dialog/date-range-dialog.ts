import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';

import { ContentService } from '../../core/services/content.service';
import { NavigationService } from '../../core/services/navigation.service';
import { AppButtonComponent } from '../../shared/ui';
import { DateRangeDialogContent } from './date-range-dialog.model';

/** Dialogo modal "Diálogo Compras" para filtrar por rango de fechas. */
@Component({
  selector: 'app-date-range-dialog',
  standalone: true,
  imports: [CommonModule, FormsModule, AppButtonComponent],
  templateUrl: './date-range-dialog.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DateRangeDialogComponent {
  private readonly content = inject(ContentService);
  private readonly navigation = inject(NavigationService);

  readonly data = toSignal(this.content.loadJson<DateRangeDialogContent>('date-range-dialog'));

  fromDate = '';
  toDate = '';

  onCancel(): void {
    this.navigation.goTo('/compras');
  }
}
