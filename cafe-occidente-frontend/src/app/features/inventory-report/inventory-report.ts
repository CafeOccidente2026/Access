import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin } from 'rxjs';

import { ProductCodeOption } from '../../core/models/inventory.model';
import { AuthService } from '../../core/services/auth.service';
import { ContentService } from '../../core/services/content.service';
import { InventoryService } from '../../core/services/inventory.service';
import { AccessWindowComponent, AgencyPickerComponent, ComboboxComponent } from '../../shared/ui';
import { renderPdfPreview } from '../../shared/utils/pdf-preview';
import { buildInventoryReportDoc } from './inventory-report-pdf';

/** En Access la consulta INV pedia la especialidad con un InputBox (texto libre, sin lista). La
 *  lista sale de los "Especial" de los formularios de compra, mas los fijos de Verde y Pasilla. */
const PURCHASE_FORMS = ['purchase-form-dry', 'purchase-form-other', 'purchase-form-ferti-futuro', 'purchase-form-future', 'purchase-form-quota-purchase'];
const FIXED_SPECIALS = ['CV', 'PASILLA']; // GreenCoffeePurchaseServiceImpl / HuskPurchaseServiceImpl

export function specialOptions(forms: unknown[]): string[] {
  const found = new Set<string>(FIXED_SPECIALS);
  const walk = (node: unknown): void => {
    if (Array.isArray(node)) {
      node.forEach(walk);
    } else if (node && typeof node === 'object') {
      const field = node as { key?: string; options?: unknown };
      if (field.key === 'special' && Array.isArray(field.options)) {
        field.options.forEach((o) => typeof o === 'string' && found.add(o));
      }
      Object.values(node).forEach(walk);
    }
  };
  forms.forEach(walk);
  return [...found].sort((a, b) => a.localeCompare(b));
}

/**
 * "Reporte Inventario por Cod." (DialogoInvXCod -> INVXCODPROD, vista continua) y "por Esp."
 * (reporte INVENTARIO, paginado). Mismo dialogo Aceptar / Cancelar.
 */
@Component({
  selector: 'app-inventory-report',
  standalone: true,
  imports: [CommonModule, AccessWindowComponent, AgencyPickerComponent, ComboboxComponent],
  templateUrl: './inventory-report.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InventoryReportComponent {
  private readonly inventoryService = inject(InventoryService);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);

  readonly byCode = inject(ActivatedRoute).snapshot.data['mode'] === 'code';
  readonly admin = this.auth.isAdmin();
  readonly year = new Date().getFullYear();

  readonly agencyId = signal<number | null>(this.auth.agencyId());
  readonly products = signal<ProductCodeOption[]>([]);
  readonly productLabels = computed(() => this.products().map((p) => `${p.code} ${p.name ?? ''}`.trimEnd()));
  readonly specials = signal<string[]>([]);
  readonly choice = signal('');
  readonly message = signal<string | null>(null);
  readonly pdfUrl = signal<SafeResourceUrl | null>(null);

  constructor() {
    if (this.byCode) {
      this.inventoryService.productCodes().subscribe((list) => this.products.set(list));
    } else {
      const content = inject(ContentService);
      forkJoin(PURCHASE_FORMS.map((f) => content.loadJson<unknown>(f))).subscribe((forms) => this.specials.set(specialOptions(forms)));
    }
  }

  cancel(): void {
    this.router.navigateByUrl('/compras/inventarios');
  }

  accept(): void {
    const agencyId = this.agencyId();
    const choice = this.choice().trim();
    this.message.set(null);
    this.pdfUrl.set(null);
    if (!agencyId) {
      this.message.set('Elija una agencia de la lista');
      return;
    }
    const productCode = this.byCode ? this.products()[this.productLabels().indexOf(choice)]?.code : undefined;
    if (this.byCode ? !productCode : !this.specials().includes(choice)) {
      this.message.set(this.byCode ? 'Elija un código de producto de la lista' : 'Elija una especialidad de la lista');
      return;
    }
    const filter = productCode ? { productCode } : { specialType: choice };
    this.inventoryService.inventoryReport(agencyId, filter).subscribe({
      next: (rows) => {
        if (rows.length === 0) {
          this.message.set(`No hay movimientos de ${this.byCode ? 'ese producto' : 'esa especialidad'} en ${this.year}`);
          return;
        }
        renderPdfPreview(buildInventoryReportDoc(rows, this.byCode), this.sanitizer, (url) => this.pdfUrl.set(url));
      },
      error: () => this.message.set('No se pudo generar el reporte'),
    });
  }
}
