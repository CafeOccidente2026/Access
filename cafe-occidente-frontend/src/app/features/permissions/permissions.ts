import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';

import { Screen } from '../../core/models';
import { Permission } from '../../core/models/permission.model';
import { ContentService } from '../../core/services/content.service';
import { PermissionService } from '../../core/services/permission.service';
import { UserService } from '../../core/services/user.service';
import { AccessWindowComponent, AppButtonComponent } from '../../shared/ui';
import { PermissionsContent } from './permissions.model';

/**
 * Pantalla "Permisos" (Acceso Principal, solo Administrador; RF-02, formulario PermisosSA de Access):
 * elige un usuario, marca las pantallas que se le conceden y retira las que ya no necesita. El permiso
 * vale hasta que se retire (RN-02.3).
 */
@Component({
  selector: 'app-permissions',
  standalone: true,
  imports: [CommonModule, AccessWindowComponent, AppButtonComponent],
  templateUrl: './permissions.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PermissionsComponent {
  private readonly permissionService = inject(PermissionService);

  readonly page = toSignal(inject(ContentService).loadJson<PermissionsContent>('permissions'));
  /** Al Administrador no se le concede nada: ya tiene todas las pantallas. */
  readonly users = toSignal(
    inject(UserService).list().pipe(map((users) => users.filter((user) => user.roleName !== 'ADMINISTRADOR'))),
    { initialValue: [] },
  );
  readonly userId = signal<number | null>(null);
  readonly current = signal<Permission[]>([]);
  readonly selected = signal<ReadonlySet<Screen>>(new Set());
  readonly message = signal<string | null>(null);
  readonly error = signal(false);

  private readonly user = computed(() => this.users().find((user) => user.id === this.userId()));

  /** RN-02.1: el catalogo, sin lo que el rol ya tiene (Operaciones ya entra a Actualizar Anuncios). */
  readonly catalog = computed(() => {
    const screens = Object.keys(this.page()?.screens ?? {}) as Screen[];
    return this.user()?.roleName === 'OPERACIONES' ? screens.filter((s) => s !== 'ACTUALIZAR_ANUNCIOS') : screens;
  });

  onUserChange(value: string): void {
    const id = value ? Number(value) : null;
    this.userId.set(id);
    this.selected.set(new Set());
    this.current.set([]);
    this.message.set(null);
    this.error.set(false);
    if (id !== null) {
      this.permissionService.list(id).subscribe((permissions) => this.current.set(permissions));
    }
  }

  isGranted(screen: Screen): boolean {
    return this.current().some((permission) => permission.screen === screen);
  }

  toggle(screen: Screen, checked: boolean): void {
    const next = new Set(this.selected());
    if (checked) {
      next.add(screen);
    } else {
      next.delete(screen);
    }
    this.selected.set(next);
  }

  /** RN-02.2: concede las pantallas marcadas. */
  save(): void {
    const id = this.userId();
    if (id === null || this.selected().size === 0) {
      return;
    }
    this.permissionService.grant(id, [...this.selected()]).subscribe({
      next: (permissions) => {
        this.current.set(permissions);
        this.selected.set(new Set());
        this.done(this.page()?.savedMessage);
      },
      error: () => this.error.set(true),
    });
  }

  /** RN-02.3: el usuario vuelve a su acceso normal segun su rol. */
  revoke(screen: Screen): void {
    const id = this.userId();
    if (id === null) {
      return;
    }
    this.permissionService.revoke(id, screen).subscribe({
      next: () => {
        this.current.set(this.current().filter((permission) => permission.screen !== screen));
        this.done(this.page()?.revokedMessage);
      },
      error: () => this.error.set(true),
    });
  }

  private done(message: string | undefined): void {
    this.error.set(false);
    this.message.set(message ?? null);
  }
}
