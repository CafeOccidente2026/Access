import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';

import { UserRole } from '../../core/models';
import { ContentService } from '../../core/services/content.service';
import { AccessWindowComponent, AppButtonComponent } from '../../shared/ui';
import { UserManagementContent, UserRecord } from './user-management.model';
import { UserService } from './user.service';

/** Pantalla "Usuarios": lista de usuarios y alta de usuarios nuevos (solo ADMIN). */
@Component({
  selector: 'app-user-management',
  standalone: true,
  imports: [CommonModule, FormsModule, AccessWindowComponent, AppButtonComponent],
  templateUrl: './user-management.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserManagementComponent {
  private readonly content = inject(ContentService);
  private readonly userService = inject(UserService);

  readonly page = toSignal(this.content.loadJson<UserManagementContent>('user-management'));
  readonly users = signal<UserRecord[]>([]);
  readonly errorVisible = signal(false);

  username = '';
  password = '';
  fullName = '';
  role: UserRole = 'PURCHASE_AGENT';

  constructor() {
    this.loadUsers();
  }

  roleLabel(role: UserRole): string {
    return this.page()?.roleOptions.find((option) => option.value === role)?.label ?? role;
  }

  onCreate(): void {
    this.errorVisible.set(false);
    this.userService
      .create({ username: this.username, password: this.password, fullName: this.fullName, role: this.role })
      .subscribe({
        next: () => {
          this.resetForm();
          this.loadUsers();
        },
        error: () => this.errorVisible.set(true),
      });
  }

  onDeactivate(user: UserRecord): void {
    this.userService.deactivate(user.id).subscribe({
      next: () => this.loadUsers(),
      error: () => this.errorVisible.set(true),
    });
  }

  private loadUsers(): void {
    this.userService.list().subscribe((users) => this.users.set(users));
  }

  private resetForm(): void {
    this.username = '';
    this.password = '';
    this.fullName = '';
    this.role = 'PURCHASE_AGENT';
  }
}
