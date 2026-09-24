import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';

import { ContentService } from '../../core/services/content.service';
import { AccessWindowComponent, MenuButtonGridComponent } from '../../shared/ui';
import { AnnouncementsMenuContent } from './announcements-menu.model';

/** Submenú ADMIN "Actualizar Anuncios": agrupa las dos pantallas de publicar anuncio, antes
 *  sueltas directo en el Menú Principal. */
@Component({
  selector: 'app-announcements-menu',
  standalone: true,
  imports: [CommonModule, AccessWindowComponent, MenuButtonGridComponent],
  templateUrl: './announcements-menu.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AnnouncementsMenuComponent {
  private readonly content = inject(ContentService);

  readonly data = toSignal(this.content.loadJson<AnnouncementsMenuContent>('announcements-menu'));
}
