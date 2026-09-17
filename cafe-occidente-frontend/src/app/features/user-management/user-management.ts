import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';

/** Misma regla que UserRequest.password en el backend (Pattern): minimo 4 caracteres, letras y numeros. */
const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d).{4,}$/;

import { Agency } from '../../core/models/agency.model';
import { Role } from '../../core/models/role.model';
import { AgencyService } from '../../core/services/agency.service';
import { ContentService } from '../../core/services/content.service';
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

  readonly page = toSignal(this.content.loadJson<UserManagementContent>('user-management'));
  readonly roles = signal<Role[]>([]);
  readonly agencies = signal<Agency[]>([]);

  username = '';
  password = '';
  roleId: number | null = null;
  agencyId: number | null = null;
  readonly userMessage = signal<string | null>(null);
  readonly userError = signal<string | null>(null);

  constructor() {
    this.roleService.list().subscribe((roles) => this.roles.set(roles));
    this.agencyService.list().subscribe((agencies) => this.agencies.set(agencies));
  }

  createUser(): void {
    this.userMessage.set(null);
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
        next: () => {
          this.userMessage.set(this.page()?.userCreatedMessage ?? null);
          this.username = '';
          this.password = '';
          this.roleId = null;
          this.agencyId = null;
        },
        error: (err: HttpErrorResponse) => {
          // Si el backend rechazo por una validacion de campo especifica (password, username, etc.),
          // mostrarsela al admin en vez del mensaje generico - "Ocurrio un error" no dice que corregir.
          const fieldError = err.error?.fieldErrors && Object.values(err.error.fieldErrors)[0];
          this.userError.set((fieldError as string | undefined) ?? this.page()?.errorMessage ?? null);
        },
      });
  }
}
