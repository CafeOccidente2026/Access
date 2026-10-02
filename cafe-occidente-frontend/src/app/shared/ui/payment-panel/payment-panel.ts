import { CommonModule } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  OnChanges,
  Output,
  SimpleChanges,
  inject,
  signal,
} from '@angular/core';

import { PaymentKey, PaymentPanelDefinition, PurchasePayment } from '../../../core/models';
import { formatThousands } from '../../utils/number-format';
import { PaymentState, PAYMENT_ORDER, commitPayment, initialPayment, readyPayment } from './payment-cascade';

const KEY_OF: Record<PaymentKey | 'checkNumber', string> = {
  cash: 'payCash',
  check: 'payCheck',
  checkNumber: 'payCheckNumber',
  transfer: 'payTransfer',
  card: 'payCard',
};

/**
 * Panel "FORMAS DE PAGO" de los formularios de compra: la cascada FPef / FPch + NumCheque / FPtx /
 * FPdat de Access (ver payment-cascade.ts). Emite el pago cuadrado, o null mientras no cuadra.
 */
@Component({
  selector: 'app-payment-panel',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './payment-panel.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PaymentPanelComponent implements OnChanges {
  @Input({ required: true }) definition!: PaymentPanelDefinition;
  /** Neto a Pagar ya calculado; null mientras falta la liquidacion (el panel queda vacio). */
  @Input() netToPay: number | null = null;
  /** Despues de imprimir la factura ya no se cambia. */
  @Input() locked = false;
  @Output() readonly paymentChange = new EventEmitter<PurchasePayment | null>();

  private readonly host: HTMLElement = inject(ElementRef).nativeElement;
  readonly state = signal<PaymentState | null>(null);
  readonly unbalanced = signal(false);
  readonly keyOf = KEY_OF;
  readonly order = PAYMENT_ORDER;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['netToPay']) {
      // Nueva liquidacion: todo en efectivo otra vez (OtrosDescuentos_LostFocus).
      this.state.set(this.netToPay == null ? null : initialPayment(this.netToPay));
      this.unbalanced.set(false);
      // Fuera del ciclo de cambios del formulario padre, que es quien guarda el pago.
      queueMicrotask(() => this.emit());
    }
  }

  display(value: number): string {
    return formatThousands(String(value));
  }

  total(): string {
    const s = this.state();
    return s ? this.display(PAYMENT_ORDER.reduce((sum, k) => sum + s.amounts[k], 0)) : '';
  }

  missingCheckNumber(): boolean {
    const s = this.state();
    return !!s && s.amounts.check > 0 && this.netToPay != null && readyPayment(this.netToPay, s) == null
      && PAYMENT_ORDER.reduce((sum, k) => sum + s.amounts[k], 0) === this.netToPay;
  }

  onAmount(key: PaymentKey, input: HTMLInputElement): void {
    const digits = input.value.replace(/\D/g, '');
    input.value = formatThousands(digits);
    this.state.update((s) => s && { ...s, amounts: { ...s.amounts, [key]: Number(digits || '0') } });
    this.unbalanced.set(false);
    this.emit();
  }

  onCheckNumber(input: HTMLInputElement): void {
    input.value = input.value.replace(/\D/g, '');
    this.state.update((s) => s && { ...s, checkNumber: input.value });
    this.emit();
  }

  /** Enter: confirma el campo como el LostFocus de Access y pasa al que sigue. */
  commit(field: PaymentKey | 'checkNumber', event: Event): void {
    event.preventDefault();
    const s = this.state();
    if (!s || this.netToPay == null) {
      return;
    }
    const step = commitPayment(this.netToPay, s, field);
    this.state.set(step.state);
    this.unbalanced.set(step.unbalanced);
    this.emit();
    if (step.next) {
      setTimeout(() => this.host.querySelector<HTMLInputElement>(`[data-field-key="${KEY_OF[step.next!]}"]`)?.select());
    }
  }

  private emit(): void {
    const s = this.state();
    this.paymentChange.emit(s && this.netToPay != null ? readyPayment(this.netToPay, s) : null);
  }
}
