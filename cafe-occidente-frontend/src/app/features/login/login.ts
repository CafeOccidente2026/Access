import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';

import { AuthService } from '../../core/services/auth.service';
import { ContentService } from '../../core/services/content.service';
import { NavigationService } from '../../core/services/navigation.service';
import { AppButtonComponent, WindowShellComponent } from '../../shared/ui';
import { LoginContent, ShellContent } from './login.model';

/** Pantalla de autenticacion, replica del dialogo "AplicCompras Login". */
@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, WindowShellComponent, AppButtonComponent],
  templateUrl: './login.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginComponent {
  private readonly content = inject(ContentService);
  private readonly navigation = inject(NavigationService);
  private readonly auth = inject(AuthService);

  readonly page = toSignal(
    forkJoin({
      shell: this.content.loadJson<ShellContent>('shell'),
      form: this.content.loadJson<LoginContent>('login'),
    }),
  );

  username = '';
  password = '';
  readonly showPassword = signal(false);
  readonly errorMessage = signal<string | null>(null);

  togglePasswordVisibility(): void {
    this.showPassword.update((value) => !value);
  }

  onAccept(): void {
    this.errorMessage.set(null);
    this.auth.login({ username: this.username, password: this.password }).subscribe({
      next: () => this.navigation.goTo(this.page()?.form.successRoute),
      error: () => this.errorMessage.set(this.page()?.form.invalidCredentialsError ?? null),
    });
  }

  onCancel(): void {
    this.username = '';
    this.password = '';
    this.errorMessage.set(null);
  }
}
