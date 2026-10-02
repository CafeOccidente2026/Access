import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Observable } from 'rxjs';

import { Associate, AssociatePage } from '../../core/models/vendor.model';
import { ContentService } from '../../core/services/content.service';
import { NavigationService } from '../../core/services/navigation.service';
import { VendorService } from '../../core/services/vendor.service';
import { AccessWindowComponent, FormFlowDirective } from '../../shared/ui';
import { shortDate } from '../conductor-form/conductor-form';

export interface AssociateField {
  readonly key: keyof Associate;
  readonly label: string;
  /** date = Short Date; bool = Format True/False ("Verdadero"/"Falso"); check = casilla; memo = varias lineas. */
  readonly type?: 'date' | 'bool' | 'check' | 'memo';
}

export interface AssociatesContent {
  readonly windowTitle: string;
  readonly heading: string;
  readonly trueLabel: string;
  readonly falseLabel: string;
  readonly recordLabel: string;
  readonly ofLabel: string;
  readonly searchLabel: string;
  readonly searchButton: string;
  readonly backLabel: string;
  readonly backRoute: string;
  readonly nav: Record<'first' | 'previous' | 'next' | 'last', string>;
  readonly messages: Record<'notFound' | 'error', string>;
  readonly columns: AssociateField[][];
}

/** Texto de un control del formulario "Asociados" de Access (todos Locked). */
export function displayValue(associate: Associate, field: AssociateField, content: Pick<AssociatesContent, 'trueLabel' | 'falseLabel'>): string {
  const value = associate[field.key];
  if (field.type === 'bool') {
    return value ? content.trueLabel : content.falseLabel;
  }
  if (field.type === 'date') {
    return shortDate(value as string | null);
  }
  return value == null ? '' : String(value);
}

/** "Consultar Asociados": formulario Asociados de Access, solo lectura, un registro con navegacion. */
@Component({
  selector: 'app-associates',
  standalone: true,
  imports: [CommonModule, AccessWindowComponent, FormFlowDirective],
  templateUrl: './associates.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AssociatesComponent {
  private readonly vendorService = inject(VendorService);
  private readonly navigation = inject(NavigationService);

  readonly content = toSignal(inject(ContentService).loadJson<AssociatesContent>('associates'));
  readonly page = signal<AssociatePage | null>(null);
  readonly message = signal<string | null>(null);
  readonly display = displayValue;

  constructor() {
    this.load(this.vendorService.associateAt(0));
  }

  go(position: number): void {
    const page = this.page();
    if (page && position >= 0 && position < page.total && position !== page.position) {
      this.load(this.vendorService.associateAt(position));
    }
  }

  search(event: Event, idNumber: string): void {
    event.preventDefault();
    if (idNumber.trim()) {
      this.load(this.vendorService.findAssociate(idNumber.replace(/\D/g, '')), true);
    }
  }

  back(): void {
    this.navigation.goTo(this.content()?.backRoute);
  }

  private load(request: Observable<AssociatePage>, searching = false): void {
    this.message.set(null);
    request.subscribe({
      next: (page) => this.page.set(page),
      error: () => this.message.set(searching ? (this.content()?.messages.notFound ?? null) : (this.content()?.messages.error ?? null)),
    });
  }
}
