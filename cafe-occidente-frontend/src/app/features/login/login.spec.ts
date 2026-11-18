import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';

import loginContent from '../../../../public/assets/data/login.json';
import shellContent from '../../../../public/assets/data/shell.json';
import { AuthService } from '../../core/services/auth.service';
import { ContentService } from '../../core/services/content.service';
import { LoginComponent } from './login';

function loginFailingWith(body: unknown, status = 401): LoginComponent {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      { provide: ContentService, useValue: { loadJson: (name: string) => of(name === 'login' ? loginContent : shellContent) } },
      { provide: AuthService, useValue: { login: () => throwError(() => new HttpErrorResponse({ status, error: body })) } },
    ],
  });
  const component = TestBed.createComponent(LoginComponent).componentInstance;
  component.onAccept();
  return component;
}

describe('LoginComponent', () => {
  beforeEach(() => TestBed.resetTestingModule());

  it('tells a disabled user to contact the Administrator', () => {
    const component = loginFailingWith({ code: 'USER_DISABLED', message: 'x' });
    expect(component.errorMessage()).toBe('Su usuario está desactivado. Comuníquese con el Administrador.');
  });

  it('a wrong password keeps the usual message', () => {
    const component = loginFailingWith({ message: 'Usuario o contrasena invalidos' });
    expect(component.errorMessage()).toBe('Usuario o contraseña incorrectos');
  });

  it('S4: after too many attempts it says how long to wait, as the server says', () => {
    const message = 'Demasiados intentos fallidos. Intente de nuevo en 12 minutos.';
    const component = loginFailingWith({ code: 'LOGIN_LOCKED', message }, 429);
    expect(component.errorMessage()).toBe(message);
  });
});
