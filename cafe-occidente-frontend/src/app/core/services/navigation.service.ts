import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';

/** Centraliza la navegacion entre pantallas para no acoplar los
 *  componentes de presentacion directamente al Router. */
@Injectable({ providedIn: 'root' })
export class NavigationService {
  private readonly router = inject(Router);

  goTo(route: string | undefined): void {
    if (!route) {
      return;
    }
    void this.router.navigateByUrl(route);
  }
}
