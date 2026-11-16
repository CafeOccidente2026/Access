import { Location } from '@angular/common';
import { inject, signal } from '@angular/core';
import { Observable, forkJoin, map } from 'rxjs';

import { ControlRecordService } from '../../core/services/control-record.service';

/**
 * Cierre de un formulario de compra con datos sin imprimir: la X (onClose) y las salidas por menú o atrás
 * (canDeactivate, del guard de la ruta) piden confirmación; sin datos, cierran de una.
 * Hallazgo #6 (16-nov): si mientras se digitaba cambió el anuncio de ese café o el Registro de Control, el aviso lo dice.
 */
export abstract class UnsavedCloseForm {
  protected readonly location = inject(Location);
  private readonly controlRecords = inject(ControlRecordService);
  abstract model: Record<string, string>;
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
