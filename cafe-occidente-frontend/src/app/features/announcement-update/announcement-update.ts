import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';

import { AnnouncementService } from '../../core/services/announcement.service';
import { ContentService } from '../../core/services/content.service';
import { AccessWindowComponent, AppButtonComponent } from '../../shared/ui';
import { AnnouncementUpdateContent } from './announcement-update.model';

/** Pantalla ADMIN: "Actualizar Anuncio con Factor" (ANUNCIOS CORRF) - siempre crea un anuncio nuevo. */
@Component({
  selector: 'app-announcement-update',
  standalone: true,
  imports: [CommonModule, FormsModule, AccessWindowComponent, AppButtonComponent],
  templateUrl: './announcement-update.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AnnouncementUpdateComponent {
  private readonly content = inject(ContentService);
  private readonly announcementService = inject(AnnouncementService);

  readonly page = toSignal(this.content.loadJson<AnnouncementUpdateContent>('announcement-update'));
  readonly message = signal<string | null>(null);
  readonly error = signal<string | null>(null);

  basePriceLoad: number | null = null;
  defectiveUnitPrice: number | null = null;
  specialSurcharge: number | null = null;
  specialType = '';

  update(): void {
    this.message.set(null);
    this.error.set(null);
    if (
      this.basePriceLoad == null ||
      this.defectiveUnitPrice == null ||
      this.specialSurcharge == null ||
      !this.specialType
    ) {
      return;
    }
    this.announcementService
      .create({
        basePriceLoad: this.basePriceLoad,
        defectiveUnitPrice: this.defectiveUnitPrice,
        specialSurcharge: this.specialSurcharge,
        specialType: this.specialType,
      })
      .subscribe({
        next: (announcement) =>
          this.message.set(`${this.page()?.successMessage ?? ''} ${announcement.announcementNumber}`),
        error: () => this.error.set(this.page()?.errorMessage ?? null),
      });
  }
}
