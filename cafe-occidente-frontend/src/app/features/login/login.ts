import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, ElementRef, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';

import { AuthService } from '../../core/services/auth.service';
import { ContentService } from '../../core/services/content.service';
import { NavigationService } from '../../core/services/navigation.service';
import { AppButtonComponent, WindowShellComponent } from '../../shared/ui';
import { LoginContent, ShellContent } from './login.model';

/** Orden de captura y de avance de foco al presionar Enter. */
const FOCUS_ORDER: readonly string[] = ['username', 'password'];

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
  private readonly elementRef = inject(ElementRef);

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

  constructor() {
    // Foco en el campo Usuario apenas la pantalla termina de cargar su contenido.
    effect(() => {
      if (this.page()) {
        this.focusField(FOCUS_ORDER[0]);
      }
    });
  }

  togglePasswordVisibility(): void {
    this.showPassword.update((value) => !value);
  }

  /** Enter avanza al siguiente campo; en el ultimo (Contraseña) dispara el login. */
  onEnter(field: string): void {
    const idx = FOCUS_ORDER.indexOf(field);
    if (idx === -1) {
      return;
    }
    if (idx === FOCUS_ORDER.length - 1) {
      this.onAccept();
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

  onAccept(): void {
    this.errorMessage.set(null);
    this.auth.login({ username: this.username, password: this.password }).subscribe({
      // El rol ADMIN pasa por "Acceso Principal"; el resto de roles va directo a "Menu Principal".
      next: () =>
        this.navigation.goTo(
          this.auth.isAdmin() ? this.page()?.form.successRoute : this.page()?.form.userSuccessRoute,
        ),
      error: () => this.errorMessage.set(this.page()?.form.invalidCredentialsError ?? null),
    });
  }

  onCancel(): void {
    this.username = '';
    this.password = '';
    this.errorMessage.set(null);
  }
}
