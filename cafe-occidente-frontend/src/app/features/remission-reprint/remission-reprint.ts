import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import pdfMake from 'pdfmake/build/pdfmake';
import pdfFonts from 'pdfmake/build/vfs_fonts';

import { RemissionResponse } from '../../core/models/inventory.model';
import { AuthService } from '../../core/services/auth.service';
import { InventoryService } from '../../core/services/inventory.service';
import { AccessWindowComponent } from '../../shared/ui';
import { formatDisplayNumber } from '../../shared/utils/number-format';
import { loadLogoDataUrl } from '../purchase-forms/dry-coffee/dry-coffee-invoice';
import { buildRemissionDocDefinition } from '../remission-form/remission-invoice';

/** "Reimprimir Remisión" (Form_MENUS INVENTARIOS.bas, Comando7 -> reporte Remision). */
@Component({
  selector: 'app-remission-reprint',
  standalone: true,
  imports: [CommonModule, AccessWindowComponent],
  templateUrl: './remission-reprint.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RemissionReprintComponent {
  private readonly authService = inject(AuthService);
  private readonly inventoryService = inject(InventoryService);
  private readonly logoDataUrlPromise = loadLogoDataUrl('assets/images/cafe-occidente-logo.png');

  readonly remissionNumber = signal('');
  readonly remission = signal<RemissionResponse | null>(null);
  readonly notFound = signal(false);

  kg(value: number): string {
    return formatDisplayNumber(value, 'count');
  }

  search(): void {
    const agencyId = this.authService.agencyId();
    const number = Number(this.remissionNumber());
    this.remission.set(null);
    this.notFound.set(false);
    if (!agencyId || !number) {
      return;
    }
    this.inventoryService.findRemissionByNumber(agencyId, number).subscribe((results) => {
      if (results.length === 0) {
        this.notFound.set(true);
      } else {
        this.remission.set(results[0]);
      }
    });
  }

  print(): void {
    const remission = this.remission();
    if (!remission) {
      return;
    }
    this.logoDataUrlPromise.then((logoDataUrl) => {
      const docDefinition = buildRemissionDocDefinition(remission, logoDataUrl);
      pdfMake.vfs = pdfFonts;
      pdfMake.createPdf(docDefinition).open();
    });
  }
}
