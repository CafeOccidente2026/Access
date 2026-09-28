import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import pdfMake from 'pdfmake/build/pdfmake';
import pdfFonts from 'pdfmake/build/vfs_fonts';
import type { TDocumentDefinitions } from 'pdfmake/interfaces';

import { DryCoffeePurchaseService } from '../../core/services/dry-coffee-purchase.service';
import { AccessWindowComponent } from '../../shared/ui';
import { buildAnnouncementDetailDoc, buildAnnouncementSummaryDoc, shortDate } from './announcement-report-pdf';

/**
 * "Generar Reporte Anuncio" (Form_MENUS COMPRAS.bas, Comando7 -> macro "Imprimir Anuncio" ->
 * reportes ANUNCIO y RESUMEN ANUNCIO). Los dos PDF se muestran embebidos en la misma pantalla:
 * sin ventanas emergentes, asi el navegador no bloquea el segundo.
 */
@Component({
  selector: 'app-announcement-report',
  standalone: true,
  imports: [CommonModule, AccessWindowComponent],
  templateUrl: './announcement-report.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AnnouncementReportComponent {
  private readonly service = inject(DryCoffeePurchaseService);
  private readonly sanitizer = inject(DomSanitizer);

  readonly year = new Date().getFullYear();
  readonly minDate = `${this.year}-01-01`;
  readonly maxDate = `${this.year}-12-31`;
  readonly date = signal(new Date().toLocaleDateString('en-CA'));
  readonly message = signal<string | null>(null);
  readonly detailUrl = signal<SafeResourceUrl | null>(null);
  readonly summaryUrl = signal<SafeResourceUrl | null>(null);

  generate(): void {
    const date = this.date();
    this.message.set(null);
    this.detailUrl.set(null);
    this.summaryUrl.set(null);
    if (!date.startsWith(`${this.year}-`)) {
      this.message.set(`La fecha debe ser del año ${this.year}`);
      return;
    }
    this.service.findByDate(date).subscribe({
      next: (purchases) => {
        if (purchases.length === 0) {
          this.message.set('No hay compras para esa fecha');
          return;
        }
        const label = shortDate(date);
        this.render(buildAnnouncementDetailDoc(purchases, label), this.detailUrl);
        this.render(buildAnnouncementSummaryDoc(purchases, label), this.summaryUrl);
      },
      error: () => this.message.set('No se pudo generar el reporte'),
    });
  }

  private render(docDefinition: TDocumentDefinitions, target: ReturnType<typeof signal<SafeResourceUrl | null>>): void {
    pdfMake.vfs = pdfFonts;
    pdfMake.createPdf(docDefinition).getBlob((blob) => {
      target.set(this.sanitizer.bypassSecurityTrustResourceUrl(URL.createObjectURL(blob)));
    });
  }
}
