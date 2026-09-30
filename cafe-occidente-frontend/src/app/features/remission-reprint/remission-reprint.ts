import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';

import { AuthService } from '../../core/services/auth.service';
import { InventoryService } from '../../core/services/inventory.service';
import { AccessWindowComponent, AgencyPickerComponent } from '../../shared/ui';
import { renderPdfPreview } from '../../shared/utils/pdf-preview';
import { loadLogoDataUrl } from '../purchase-forms/dry-coffee/dry-coffee-invoice';
import { buildRemissionDocDefinition } from '../remission-form/remission-invoice';

/** "Reimprimir Remisión" (Form_MENUS INVENTARIOS.bas, Comando7 -> consulta "Remision", que pide el
 *  numero con un InputBox): mismo PDF de 4 copias que al crear la remision. */
@Component({
  selector: 'app-remission-reprint',
  standalone: true,
  imports: [CommonModule, AccessWindowComponent, AgencyPickerComponent],
  templateUrl: './remission-reprint.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RemissionReprintComponent {
  private readonly inventoryService = inject(InventoryService);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly logoDataUrlPromise = loadLogoDataUrl('assets/images/cafe-occidente-logo.png');

  readonly agencyId = signal<number | null>(inject(AuthService).agencyId());
  readonly remissionNumber = signal('');
  readonly message = signal<string | null>(null);
  readonly pdfUrl = signal<SafeResourceUrl | null>(null);

  search(): void {
    const agencyId = this.agencyId();
    const number = this.remissionNumber().trim();
    this.message.set(null);
    this.pdfUrl.set(null);
    if (!agencyId) {
      this.message.set('Elija una agencia de la lista');
      return;
    }
    if (!number) {
      this.message.set('Escriba el número de remisión');
      return;
    }
    this.inventoryService.findRemissionByNumber(agencyId, number).subscribe({
      next: (results) => {
        if (results.length === 0) {
          this.message.set('No existe una remisión con ese número');
          return;
        }
        this.logoDataUrlPromise.then((logo) =>
          renderPdfPreview(buildRemissionDocDefinition(results[0], logo), this.sanitizer, (url) => this.pdfUrl.set(url)),
        );
      },
      error: () => this.message.set('No se pudo buscar la remisión'),
    });
  }
}
