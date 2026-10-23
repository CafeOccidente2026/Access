import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, ElementRef, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';

/** Misma regla que UserRequest.password en el backend (Pattern): minimo 4 caracteres, letras y numeros. */
const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d).{4,}$/;

/** Orden de captura y de avance de foco al presionar Enter. */
const FOCUS_ORDER: readonly string[] = ['username', 'idNumber', 'password', 'roleId', 'agencyId'];

/** La cedula solo acepta numeros (RF-07): se limpia el input mismo para que la letra no quede visible. */
export function digitsOnly(input: HTMLInputElement): string {
  input.value = input.value.replace(/\D/g, '');
  return input.value;
}

/** Mensaje del backend: el de la validacion de campo o el de la regla de negocio. */
function apiError(err: HttpErrorResponse): string | undefined {
  const fieldError = err.error?.fieldErrors && Object.values(err.error.fieldErrors)[0];
  return (fieldError as string | undefined) ?? err.error?.message;
}

/** Ruta a la que vuelve la pantalla al crear el usuario con exito (misma que closeRoute del access-window). */
const CLOSE_ROUTE = '/menu-principal';

import { Agency } from '../../core/models/agency.model';
import { Role } from '../../core/models/role.model';
import { User } from '../../core/models/user.model';
import { AgencyService } from '../../core/services/agency.service';
import { ContentService } from '../../core/services/content.service';
import { NavigationService } from '../../core/services/navigation.service';
import { RoleService } from '../../core/services/role.service';
import { UserService } from '../../core/services/user.service';
import { AccessWindowComponent, AppButtonComponent } from '../../shared/ui';
import { UserManagementContent } from './user-management.model';

/** Pantalla ADMIN: alta de usuarios (HU-08), alta de agencias (HU-09) y edicion de usuarios (fase 3, C4). */
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

  readonly digitsOnly = digitsOnly;
  readonly users = signal<User[]>([]);

  username = '';
  idNumber = '';
  password = '';
  roleId: number | null = null;
  agencyId: number | null = null;
  readonly userError = signal<string | null>(null);

  /** Usuario en edicion y sus valores (fase 3, C4). */
  readonly editing = signal<User | null>(null);
  editRoleId: number | null = null;
  editAgencyId: number | null = null;
  editActive = true;
  editPassword = '';
  editIdNumber = '';
  readonly editError = signal<string | null>(null);
  readonly editNotice = signal<string | null>(null);

  newAgencyName = '';
  readonly agencyNotice = signal<string | null>(null);
  readonly agencyError = signal<string | null>(null);

  constructor() {
    this.roleService.list().subscribe((roles) => this.roles.set(roles));
    this.agencyService.list().subscribe((agencies) => this.agencies.set(agencies));
    this.userService.list().subscribe((users) => this.users.set(users));
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

  /** HU-09: la agencia nueva entra de inmediato a la lista y queda elegida para el usuario. */
  createAgency(): void {
    this.agencyNotice.set(null);
    this.agencyError.set(null);
    const name = this.newAgencyName.trim();
    if (!name) {
      return;
    }
    this.agencyService.create(name).subscribe({
      next: (agency) => {
        this.agencies.update((list) => [...list, agency]);
        this.agencyId = agency.id;
        this.newAgencyName = '';
        this.agencyNotice.set(this.page()?.agencyCreatedNotice ?? null);
      },
      error: (err: HttpErrorResponse) => this.agencyError.set(apiError(err) ?? this.page()?.errorMessage ?? null),
    });
  }

  createUser(): void {
    this.userError.set(null);
    if (!this.roleId || !this.agencyId) {
      return;
    }
    if (!this.idNumber) {
      this.userError.set(this.page()?.idNumberRequiredMessage ?? null);
      return;
    }
    if (!PASSWORD_PATTERN.test(this.password)) {
      this.userError.set(this.page()?.passwordInvalidMessage ?? null);
      return;
    }
    this.userService
      .create({
        username: this.username,
        idNumber: this.idNumber,
        password: this.password,
        roleId: this.roleId,
        agencyId: this.agencyId,
      })
      .subscribe({
        next: () => this.navigation.goTo(CLOSE_ROUTE),
        // El mensaje del backend (validacion de campo o regla) dice que corregir; el generico no.
        error: (err: HttpErrorResponse) => this.userError.set(apiError(err) ?? this.page()?.errorMessage ?? null),
      });
  }

  startEdit(user: User): void {
    this.editError.set(null);
    this.editNotice.set(null);
    this.editRoleId = user.roleId;
    this.editAgencyId = user.agencyId;
    this.editActive = user.active;
    this.editPassword = '';
    this.editIdNumber = '';
    this.editing.set(user);
  }

  cancelEdit(): void {
    this.editing.set(null);
  }

  /** Las reglas del ultimo Administrador y de no desactivarse a si mismo las aplica el servidor. */
  saveEdit(): void {
    const user = this.editing();
    if (!user || !this.editRoleId || !this.editAgencyId) {
      return;
    }
    this.editError.set(null);
    if (this.editPassword && !PASSWORD_PATTERN.test(this.editPassword)) {
      this.editError.set(this.page()?.passwordInvalidMessage ?? null);
      return;
    }
    this.userService
      .update(user.id, {
        roleId: this.editRoleId,
        agencyId: this.editAgencyId,
        active: this.editActive,
        password: this.editPassword || null,
        idNumber: this.editIdNumber || null,
      })
      .subscribe({
        next: (saved) => {
          this.users.update((list) => list.map((u) => (u.id === saved.id ? saved : u)));
          this.editing.set(null);
          this.editNotice.set(this.page()?.userSavedMessage ?? null);
        },
        error: (err: HttpErrorResponse) => this.editError.set(apiError(err) ?? this.page()?.errorMessage ?? null),
      });
  }
}
