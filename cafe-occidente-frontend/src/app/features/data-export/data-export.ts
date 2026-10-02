import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';

import { AuthService } from '../../core/services/auth.service';
import { ContentService } from '../../core/services/content.service';
import { DataExportService } from '../../core/services/data-export.service';
import { NavigationService } from '../../core/services/navigation.service';
import { AccessWindowComponent, AgencyPickerComponent, FormFlowDirective } from '../../shared/ui';

export interface DataExportContent {
  readonly scopeLabel: string;
  readonly allAgencies: string;
  readonly oneAgency: string;
  readonly agencyLabel: string;
  readonly fromLabel: string;
  readonly toLabel: string;
  readonly acceptLabel: string;
  readonly cancelLabel: string;
  readonly backRoute: string;
  readonly files: string;
  readonly screens: Record<string, { windowTitle: string; endpoint: string; dates: boolean; note?: string }>;
  readonly messages: Record<'chooseAgency' | 'dates' | 'done' | 'error', string>;
}

/** Nombre que manda el backend en Content-Disposition (filename="..."). */
export function attachmentName(header: string | null, fallback: string): string {
  return /filename="?([^";]+)"?/.exec(header ?? '')?.[1] ?? fallback;
}

/**
 * "Exportar Informacion" (macro ExportarCompras) y "Exportado Especial" (ExportadoEspecialBuys) del
 * Menu Principal, solo ADMIN: antes de exportar se elige todas las agencias o una puntual.
 */
@Component({
  selector: 'app-data-export',
  standalone: true,
  imports: [CommonModule, AccessWindowComponent, AgencyPickerComponent, FormFlowDirective],
  templateUrl: './data-export.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DataExportComponent {
  private readonly exportService = inject(DataExportService);
  private readonly navigation = inject(NavigationService);
  private readonly screenKey: string = inject(ActivatedRoute).snapshot.data['screen'];

  readonly content = toSignal(inject(ContentService).loadJson<DataExportContent>('data-export'));
  readonly screen = computed(() => this.content()?.screens[this.screenKey]);

  readonly allAgencies = signal(true);
  readonly agencyId = signal<number | null>(inject(AuthService).agencyId());
  readonly from = signal('');
  readonly to = signal('');
  readonly message = signal<string | null>(null);
  readonly error = signal(false);
  readonly running = signal(false);

  accept(event: Event): void {
    event.preventDefault();
    const content = this.content()!;
    const screen = this.screen()!;
    const problem = !this.allAgencies() && this.agencyId() === null
      ? content.messages.chooseAgency
      : screen.dates && (!this.from() || !this.to() || this.from() > this.to())
        ? content.messages.dates
        : null;
    this.error.set(!!problem);
    this.message.set(problem);
    if (problem) {
      return;
    }
    this.running.set(true);
    const params = {
      agencyId: this.allAgencies() ? null : this.agencyId(),
      from: screen.dates ? this.from() : null,
      to: screen.dates ? this.to() : null,
    };
    this.exportService.export(screen.endpoint, params).subscribe({
      next: (response) => {
        this.running.set(false);
        const name = attachmentName(response.headers.get('Content-Disposition'), 'AplicComprasArchivos.zip');
        const link = document.createElement('a');
        link.href = URL.createObjectURL(response.body!);
        link.download = name;
        link.click();
        URL.revokeObjectURL(link.href);
        this.message.set(content.messages.done.replace('{file}', name));
      },
      error: () => {
        this.running.set(false);
        this.error.set(true);
        this.message.set(content.messages.error);
      },
    });
  }

  cancel(): void {
    this.navigation.goTo(this.content()?.backRoute);
  }
}
