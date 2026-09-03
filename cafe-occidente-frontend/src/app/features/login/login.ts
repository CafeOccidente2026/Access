import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';

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

  readonly page = toSignal(
    forkJoin({
      shell: this.content.loadJson<ShellContent>('shell'),
      form: this.content.loadJson<LoginContent>('login'),
    }),
  );

  username = '';
  password = '';

  onAccept(): void {
    this.navigation.goTo(this.page()?.form.successRoute);
  }

  onCancel(): void {
    this.username = '';
    this.password = '';
  }
}
