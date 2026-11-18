import { Location } from '@angular/common';
import { WritableSignal, inject, signal } from '@angular/core';
import { Observable, forkJoin, map } from 'rxjs';

import { ControlRecordService } from '../../core/services/control-record.service';
import { WeightMessages, checkWeight } from './weight-check';

/** Mensajes que comparten los formularios de compra (aviso, guardado y cierre). */
export interface PurchaseMessages {
  readonly acceptLabel: string;
  readonly idNumberNotFound: string;
  readonly saveError: string;
  readonly savedNotice: string;
  readonly invoiceError: string;
  readonly closeWarning: string;
  /** Hallazgo #6: se suma al aviso si el precio cambió mientras se digitaba. */
  readonly priceChangedWarning?: string;
  readonly closeConfirm: string;
  readonly closeCancel: string;
}

/**
 * Cierre de un formulario de compra con datos sin imprimir: la X (onClose) y las salidas por menú o atrás
 * (canDeactivate, del guard de la ruta) piden confirmación; sin datos, cierran de una.
 * Hallazgo #6 (16-nov): si mientras se digitaba cambió el anuncio de ese café o el Registro de Control, el aviso lo dice.
 */
export abstract class UnsavedCloseForm {
  protected readonly location = inject(Location);
  private readonly controlRecords = inject(ControlRecordService);
  abstract model: Record<string, string>;
  abstract readonly errorMessage: WritableSignal<string | null>;
  /** Campo al que vuelve el foco al cerrar el aviso de un valor rechazado (hallazgos #5 y #10). */
  protected retryKey: string | null = null;
  /** Señal: el guard de la ruta la cambia fuera de un evento de la pantalla y la app no usa zone.js. */
  readonly pendingClose = signal(false);
  /** El precio (anuncio o Registro de Control) cambió desde que empezó la compra. */
  readonly priceChanged = signal(false);
  private closeResolver: ((value: boolean) => void) | null = null;
  private priceAtStart: string | null = null;

  protected abstract hasUnsavedInput(): boolean;

  /** El anuncio vigente ahora para lo que está en pantalla; null mientras la pantalla no tiene anuncio. */
  protected currentAnnouncement(): Observable<string> | null {
    return null;
  }

  /** Hallazgos #5 y #10: un peso mal digitado se borra y se avisa al momento. true si se rechazó. */
  protected rejectedWeight(key: string, locked: Set<string>, messages?: WeightMessages, sampleSize?: number | null): boolean {
    this.retryKey = checkWeight(this, locked, key, messages, sampleSize);
    return this.retryKey !== null;
  }

  /** Al cerrar el aviso, el foco vuelve al campo rechazado. */
  protected refocusRetry(focus: (key: string) => void): void {
    if (this.retryKey) {
      focus(this.retryKey);
    }
    this.retryKey = null;
  }

  /** El número de un anuncio que trae el servidor; null si la pantalla todavía no tiene anuncio. */
  protected announcementNumber(info$: Observable<{ announcementNumber: string }> | null): Observable<string> | null {
    return info$ && info$.pipe(map((i) => i.announcementNumber));
  }

  /** El aviso de cierre, con la línea del precio si cambió. */
  closeMessage(m: PurchaseMessages): string {
    return this.priceChanged() ? `${m.closeWarning} ${m.priceChangedWarning ?? ''}` : m.closeWarning;
  }

  /** Se llama cuando la pantalla carga el anuncio con que empieza la compra. */
  protected rememberPrice(): void {
    this.priceStamp()?.subscribe({ next: (stamp) => (this.priceAtStart = stamp), error: () => undefined });
  }

  private priceStamp(): Observable<string> | null {
    const announcement = this.currentAnnouncement();
    return announcement && forkJoin([announcement, this.controlRecords.stamp()]).pipe(map(([a, rc]) => `${a}|${rc.stamp}`));
  }

  private askToClose(): void {
    this.priceChanged.set(false);
    this.pendingClose.set(true);
    const start = this.priceAtStart;
    if (start !== null) {
      this.priceStamp()?.subscribe({ next: (now) => this.priceChanged.set(now !== start), error: () => undefined });
    }
  }

  /** La X del formulario: si hay datos sin imprimir pide confirmación; si no, cierra. */
  onClose(): void {
    if (this.hasUnsavedInput()) {
      this.askToClose();
    } else {
      this.location.back();
    }
  }

  /** Guard CanDeactivate: cubre las salidas que no pasan por la X (menú, back, etc.). */
  canDeactivate(): boolean | Promise<boolean> {
    if (!this.hasUnsavedInput()) {
      return true;
    }
    this.askToClose();
    return new Promise<boolean>((resolve) => (this.closeResolver = resolve));
  }

  confirmClose(): void {
    this.pendingClose.set(false);
    if (this.closeResolver) {
      this.closeResolver(true);
      this.closeResolver = null;
      return;
    }
    // Descarta los datos antes de navegar: si no, canDeactivate() vuelve a encontrar
    // input sin guardar y reabre este mismo dialogo, obligando a un segundo clic.
    this.model = {};
    this.location.back();
  }

  cancelClose(): void {
    this.pendingClose.set(false);
    this.closeResolver?.(false);
    this.closeResolver = null;
  }
}
