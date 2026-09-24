import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, ElementRef, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';

/** Misma regla que UserRequest.password en el backend (Pattern): minimo 4 caracteres, letras y numeros. */
const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d).{4,}$/;

/** Orden de captura y de avance de foco al presionar Enter. */
const FOCUS_ORDER: readonly string[] = ['username', 'password', 'roleId', 'agencyId'];

/** Ruta a la que vuelve la pantalla al crear el usuario con exito (misma que closeRoute del access-window). */
const CLOSE_ROUTE = '/menu-principal';

import { Agency } from '../../core/models/agency.model';
import { Role } from '../../core/models/role.model';
import { AgencyService } from '../../core/services/agency.service';
import { ContentService } from '../../core/services/content.service';
import { NavigationService } from '../../core/services/navigation.service';
import { RoleService } from '../../core/services/role.service';
import { UserService } from '../../core/services/user.service';
import { AccessWindowComponent, AppButtonComponent } from '../../shared/ui';
import { UserManagementContent } from './user-management.model';

/** Pantalla ADMIN: alta de usuarios (con Rol y Agencia). */
@Component({
  selector: 'app-user-management',
  standalone: true,
  imports: [CommonModule, FormsModule, AccessWindowComponent, AppButtonComponent],
  templateUrl: './user-management.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserManagementComponent {
  private readonly content = inject(ContentService);
  private readonly agencyService = inject(AgencyService);
  private readonly roleService = inject(RoleService);
  private readonly userService = inject(UserService);
  private readonly navigation = inject(NavigationService);
  private readonly elementRef = inject(ElementRef);

  readonly page = toSignal(this.content.loadJson<UserManagementContent>('user-management'));
  readonly roles = signal<Role[]>([]);
  readonly agencies = signal<Agency[]>([]);

  username = '';
  password = '';
  roleId: number | null = null;
  agencyId: number | null = null;
  readonly userError = signal<string | null>(null);

  constructor() {
    this.roleService.list().subscribe((roles) => this.roles.set(roles));
    this.agencyService.list().subscribe((agencies) => this.agencies.set(agencies));
    // Foco en el primer campo apenas la pantalla termina de cargar su contenido.
    effect(() => {
      if (this.page()) {
        this.focusField(FOCUS_ORDER[0]);
      }
    });
  }

  /** Enter avanza al siguiente campo; en el ultimo (Agencia) dispara la creacion. */
  onEnter(field: string): void {
    const idx = FOCUS_ORDER.indexOf(field);
    if (idx === -1) {
      return;
    }
    if (idx === FOCUS_ORDER.length - 1) {
      this.createUser();
      return;
    }
    this.focusField(FOCUS_ORDER[idx + 1]);
  }

  private focusField(key: string): void {
    setTimeout(() => {
      const el = this.elementRef.nativeElement.querySelector(`[data-field-key="${key}"]`) as HTMLElement | null;
      el?.focus();
    });
  }

  createUser(): void {
    this.userError.set(null);
    if (!this.roleId || !this.agencyId) {
      return;
    }
    if (!PASSWORD_PATTERN.test(this.password)) {
      this.userError.set(this.page()?.passwordInvalidMessage ?? null);
      return;
    }
    this.userService
      .create({
        username: this.username,
        password: this.password,
        roleId: this.roleId,
        agencyId: this.agencyId,
      })
      .subscribe({
        next: () => this.navigation.goTo(CLOSE_ROUTE),
        error: (err: HttpErrorResponse) => {
          // Si el backend rechazo por una validacion de campo especifica (password, username, etc.),
          // mostrarsela al admin en vez del mensaje generico - "Ocurrio un error" no dice que corregir.
          const fieldError = err.error?.fieldErrors && Object.values(err.error.fieldErrors)[0];
          this.userError.set((fieldError as string | undefined) ?? this.page()?.errorMessage ?? null);
        },
      });
  }
}
