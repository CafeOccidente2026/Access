import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';

import { Municipality } from '../../core/models/municipality.model';
import { Role } from '../../core/models/role.model';
import { ContentService } from '../../core/services/content.service';
import { MunicipalityService } from '../../core/services/municipality.service';
import { RoleService } from '../../core/services/role.service';
import { UserService } from '../../core/services/user.service';
import { AccessWindowComponent, AppButtonComponent } from '../../shared/ui';
import { UserManagementContent } from './user-management.model';

/** Pantalla ADMIN: alta de usuarios (con Rol y Municipio) y alta de municipios. */
@Component({
  selector: 'app-user-management',
  standalone: true,
  imports: [CommonModule, FormsModule, AccessWindowComponent, AppButtonComponent],
  templateUrl: './user-management.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserManagementComponent {
  private readonly content = inject(ContentService);
  private readonly municipalityService = inject(MunicipalityService);
  private readonly roleService = inject(RoleService);
  private readonly userService = inject(UserService);

  readonly page = toSignal(this.content.loadJson<UserManagementContent>('user-management'));
  readonly roles = signal<Role[]>([]);
  readonly municipalities = signal<Municipality[]>([]);

  username = '';
  password = '';
  roleId: number | null = null;
  municipalityId: number | null = null;
  readonly userMessage = signal<string | null>(null);
  readonly userError = signal<string | null>(null);

  municipioName = '';
  readonly municipioMessage = signal<string | null>(null);
  readonly municipioError = signal<string | null>(null);

  constructor() {
    this.loadRoles();
    this.loadMunicipalities();
  }

  private loadRoles(): void {
    this.roleService.list().subscribe((roles) => this.roles.set(roles));
  }

  private loadMunicipalities(): void {
    this.municipalityService.list().subscribe((municipalities) => this.municipalities.set(municipalities));
  }

  createUser(): void {
    this.userMessage.set(null);
    this.userError.set(null);
    if (!this.roleId || !this.municipalityId) {
      return;
    }
    this.userService
      .create({
        username: this.username,
        password: this.password,
        roleId: this.roleId,
        municipalityId: this.municipalityId,
      })
      .subscribe({
        next: () => {
          this.userMessage.set(this.page()?.userCreatedMessage ?? null);
          this.username = '';
          this.password = '';
          this.roleId = null;
          this.municipalityId = null;
        },
        error: () => this.userError.set(this.page()?.errorMessage ?? null),
      });
  }

  createMunicipio(): void {
    this.municipioMessage.set(null);
    this.municipioError.set(null);
    this.municipalityService.create({ name: this.municipioName }).subscribe({
      next: () => {
        this.municipioMessage.set(this.page()?.municipioCreatedMessage ?? null);
        this.municipioName = '';
        this.loadMunicipalities();
      },
      error: () => this.municipioError.set(this.page()?.errorMessage ?? null),
    });
  }
}
