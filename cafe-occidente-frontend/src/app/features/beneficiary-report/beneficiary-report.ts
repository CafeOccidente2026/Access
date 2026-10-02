import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';

import { AuthService } from '../../core/services/auth.service';
import { ContentService } from '../../core/services/content.service';
import { NavigationService } from '../../core/services/navigation.service';
import { VendorService } from '../../core/services/vendor.service';
import { AccessWindowComponent, AgencyPickerComponent, FormFlowDirective } from '../../shared/ui';
import { renderPdfPreview } from '../../shared/utils/pdf-preview';
import { buildBeneficiaryDoc } from './beneficiary-report-pdf';
import { BeneficiaryReportContent } from './beneficiary-report.model';

/**
 * "Genera Inf Ventas Benef" (InputBox de la consulta Beneficiario) e "Inf Ventas Benef por Fechas"
 * (Dialogo Beneficiario): Cedula Like texto & "*" (vacio = todas) y vista previa en PDF. ADMIN elige
 * agencia (vacia = todas); USER ve la suya.
 */
@Component({
  selector: 'app-beneficiary-report',
  standalone: true,
  imports: [CommonModule, AccessWindowComponent, AgencyPickerComponent, FormFlowDirective],
  templateUrl: './beneficiary-report.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BeneficiaryReportComponent {
  private readonly vendorService = inject(VendorService);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly navigation = inject(NavigationService);
  private readonly reportKey: string = inject(ActivatedRoute).snapshot.data['report'];

  readonly admin = inject(AuthService).isAdmin();
  readonly content = toSignal(inject(ContentService).loadJson<BeneficiaryReportContent>('beneficiary-report'));
  readonly report = computed(() => this.content()?.reports[this.reportKey]);

  readonly agencyId = signal<number | null>(inject(AuthService).agencyId());
  readonly idNumber = signal('');
  readonly from = signal('');
  readonly to = signal('');
  readonly message = signal<string | null>(null);
  readonly pdfUrl = signal<SafeResourceUrl | null>(null);

  accept(event: Event): void {
    event.preventDefault();
    const content = this.content()!;
    const report = this.report()!;
    this.message.set(null);
    this.pdfUrl.set(null);
    if (report.dates && (!this.from() || !this.to() || this.from() > this.to())) {
      this.message.set(content.messages.dates);
      return;
    }
    const params = {
      agencyId: this.agencyId(),
      idNumber: this.idNumber().replace(/\D/g, ''),
      from: report.dates ? this.from() : null,
      to: report.dates ? this.to() : null,
    };
    this.vendorService.beneficiary(params).subscribe({
      next: (rows) => {
        if (rows.length === 0) {
          this.message.set(content.messages.empty);
          return;
        }
        renderPdfPreview(buildBeneficiaryDoc(report, content.text, rows), this.sanitizer, (url) => this.pdfUrl.set(url));
      },
      error: () => this.message.set(content.messages.error),
    });
  }

  cancel(): void {
    this.navigation.goTo(this.content()?.backRoute);
  }
}
