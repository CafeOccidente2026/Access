import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import content from '../../../../public/assets/data/user-management.json';
import { AgencyService } from '../../core/services/agency.service';
import { ContentService } from '../../core/services/content.service';
import { RoleService } from '../../core/services/role.service';
import { UserService } from '../../core/services/user.service';
import { UserManagementComponent, digitsOnly } from './user-management';

describe('UserManagementComponent', () => {
  let createAgency: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    createAgency = vi.fn(() => of({ id: 50, name: 'Nueva', hasControlRecord: false, suggestedPrefix: null }));
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'menu-principal', children: [] }]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ContentService, useValue: { loadJson: () => of(content) } },
        { provide: RoleService, useValue: { list: () => of([{ id: 1, name: 'ADMINISTRADOR' }]) } },
        { provide: AgencyService, useValue: { list: () => of([{ id: 1, name: 'Buesaco' }]), create: createAgency } },
        { provide: UserService, useValue: { list: () => of([]), create: vi.fn() } },
      ],
    });
  });

  it('HU-09 CA-1: the new agency is in the list right away, selected, with a control record warning', () => {
    const component = TestBed.createComponent(UserManagementComponent).componentInstance;
    component.newAgencyName = '  Nueva  ';
    component.createAgency();

    expect(createAgency).toHaveBeenCalledWith('Nueva');
    expect(component.agencies().map((a) => a.name)).toEqual(['Buesaco', 'Nueva']);
    expect(component.agencyId).toBe(50);
    expect(component.agencyNotice()).toBe(content.agencyCreatedNotice);
  });

  it('shows the server message when the name already exists', () => {
    createAgency.mockReturnValue(throwError(() => new HttpErrorResponse({
      status: 400, error: { message: 'Ya existe una agencia con ese nombre' } })));
    const component = TestBed.createComponent(UserManagementComponent).componentInstance;
    component.newAgencyName = 'Buesaco';
    component.createAgency();
    expect(component.agencyError()).toBe('Ya existe una agencia con ese nombre');
  });

  it('double submit: while saving, creating a user, an agency or saving an edit sends only once', () => {
    const userService = TestBed.inject(UserService) as unknown as { create: ReturnType<typeof vi.fn>; update: ReturnType<typeof vi.fn> };
    const pendingUser = new Subject<unknown>();
    userService.create.mockReturnValue(pendingUser);
    const fixture = TestBed.createComponent(UserManagementComponent);
    const component = fixture.componentInstance;
    Object.assign(component, { username: 'fiel1', idNumber: '9961699481', password: 'clave1234', roleId: 1, agencyId: 1 });

    component.createUser();
    component.createUser();
    component.onEnter('agencyId'); // Enter en el ultimo campo tambien crea
    expect(userService.create).toHaveBeenCalledTimes(1);
    fixture.detectChanges();
    const createButton = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('app-button button'))
      .find((b) => b.textContent?.trim() === content.createUserButton) as HTMLButtonElement;
    expect(createButton.disabled).toBe(true);
    pendingUser.error(new HttpErrorResponse({ status: 500 }));
    fixture.detectChanges();
    expect(createButton.disabled).toBe(false);

    const pendingAgency = new Subject<unknown>();
    createAgency.mockReturnValue(pendingAgency);
    component.newAgencyName = 'Nueva';
    component.createAgency();
    component.createAgency();
    expect(createAgency).toHaveBeenCalledTimes(1);
    pendingAgency.error(new HttpErrorResponse({ status: 500 }));

    const pendingEdit = new Subject<unknown>();
    userService.update = vi.fn(() => pendingEdit);
    component.startEdit({ id: 9, username: 'fiel1', idNumber: '1', active: true, roleId: 1, roleName: 'USUARIO', agencyId: 1, agencyName: 'Buesaco' });
    component.saveEdit();
    component.saveEdit();
    expect(userService.update).toHaveBeenCalledTimes(1);
  });

  it('HU-08: the Cedula only keeps digits, also in the input itself', () => {
    const input = document.createElement('input');
    input.value = '12a3b4';
    expect(digitsOnly(input)).toBe('1234');
    expect(input.value).toBe('1234');
  });

  it('HU-08 CA-1: the user is created with Cedula, Role and Agency; without Cedula it is not sent', () => {
    const userService = TestBed.inject(UserService) as unknown as { create: ReturnType<typeof vi.fn> };
    userService.create.mockReturnValue(of({}));
    const component = TestBed.createComponent(UserManagementComponent).componentInstance;
    Object.assign(component, { username: 'fiel1', password: 'clave1234', roleId: 1, agencyId: 1 });

    component.createUser();
    expect(userService.create).not.toHaveBeenCalled();
    expect(component.userError()).toBe(content.idNumberRequiredMessage);

    component.idNumber = '9961699481';
    component.createUser();
    expect(userService.create).toHaveBeenCalledWith({ username: 'fiel1', idNumber: '9961699481', password: 'clave1234', roleId: 1, agencyId: 1 });
  });

  it('C4: editing sends agency, role, active and only a new password; the list shows the saved user', () => {
    const saved = { id: 7, username: 'ana', idNumber: '55', active: false, roleId: 1, roleName: 'ADMINISTRADOR', agencyId: 1, agencyName: 'Buesaco' };
    const update = vi.fn(() => of(saved));
    const userService = TestBed.inject(UserService) as unknown as { list: ReturnType<typeof vi.fn>; update: typeof update };
    userService.update = update;
    const component = TestBed.createComponent(UserManagementComponent).componentInstance;
    component.users.set([{ ...saved, active: true, idNumber: null }]);

    component.startEdit(component.users()[0]);
    component.editActive = false;
    component.editIdNumber = '55';
    component.saveEdit();

    expect(update).toHaveBeenCalledWith(7, { roleId: 1, agencyId: 1, active: false, password: null, idNumber: '55' });
    expect(component.users()[0].active).toBe(false);
    expect(component.editing()).toBeNull();
    expect(component.editNotice()).toBe(content.userSavedMessage);
  });

  it('S4: Desbloquear lets the user try again before the 15 minutes, only once per click burst', () => {
    const unlock = vi.fn(() => of(undefined));
    (TestBed.inject(UserService) as unknown as { unlock: typeof unlock }).unlock = unlock;
    const component = TestBed.createComponent(UserManagementComponent).componentInstance;
    component.startEdit({ id: 7, username: 'ana', idNumber: '55', active: true, roleId: 1, roleName: 'USUARIO', agencyId: 1, agencyName: 'Buesaco' });

    component.unlock();
    expect(unlock).toHaveBeenCalledExactlyOnceWith(7);
    expect(component.editNotice()).toBe(content.userUnlockedMessage);
    expect(component.editing()).not.toBeNull();
  });

  it('S4: while unlocking, a second click does not send again; an error is shown as it comes', () => {
    const pending = new Subject<void>();
    const unlock = vi.fn(() => pending);
    (TestBed.inject(UserService) as unknown as { unlock: typeof unlock }).unlock = unlock;
    const component = TestBed.createComponent(UserManagementComponent).componentInstance;
    component.unlock();
    expect(unlock).not.toHaveBeenCalled();
    component.startEdit({ id: 7, username: 'ana', idNumber: '55', active: true, roleId: 1, roleName: 'USUARIO', agencyId: 1, agencyName: 'Buesaco' });
    component.unlock();
    component.unlock();
    expect(unlock).toHaveBeenCalledTimes(1);
    pending.error(new HttpErrorResponse({ status: 404, error: { message: 'Usuario no encontrado' } }));
    expect(component.editError()).toBe('Usuario no encontrado');
  });

  it('C4: the server rules (last Administrator, own account) are shown as they come', () => {
    const userService = TestBed.inject(UserService) as unknown as { update: ReturnType<typeof vi.fn> };
    userService.update = vi.fn(() => throwError(() => new HttpErrorResponse({
      status: 400, error: { message: 'Debe quedar al menos un Administrador activo' } })));
    const component = TestBed.createComponent(UserManagementComponent).componentInstance;
    component.startEdit({ id: 1, username: 'admin', idNumber: null, active: true, roleId: 1, roleName: 'ADMINISTRADOR', agencyId: 1, agencyName: 'Buesaco' });
    component.editActive = false;
    component.saveEdit();
    expect(component.editError()).toBe('Debe quedar al menos un Administrador activo');
    expect(component.editing()).not.toBeNull();
  });

  it('CA-2: a weak password is not sent, neither when creating nor when editing', () => {
    const userService = TestBed.inject(UserService) as unknown as { create: ReturnType<typeof vi.fn>; update: ReturnType<typeof vi.fn> };
    userService.update = vi.fn();
    const component = TestBed.createComponent(UserManagementComponent).componentInstance;
    // Minimo 8 (13-nov): 7 caracteres con letras y numeros ya no alcanza.
    Object.assign(component, { username: 'fiel1', idNumber: '55', password: 'abc1234', roleId: 1, agencyId: 1 });
    component.createUser();
    expect(userService.create).not.toHaveBeenCalled();
    expect(component.userError()).toBe(content.passwordInvalidMessage);

    component.startEdit({ id: 7, username: 'ana', idNumber: '55', active: true, roleId: 1, roleName: 'USUARIO', agencyId: 1, agencyName: 'Buesaco' });
    component.editPassword = 'nueva12';
    component.saveEdit();
    expect(userService.update).not.toHaveBeenCalled();
    expect(component.editError()).toBe(content.passwordInvalidMessage);

    component.cancelEdit();
    expect(component.editing()).toBeNull();
  });

  it('Enter on the last field creates the user and the server message is shown', () => {
    const userService = TestBed.inject(UserService) as unknown as { create: ReturnType<typeof vi.fn> };
    userService.create.mockReturnValue(throwError(() => new HttpErrorResponse({
      status: 400, error: { message: 'Ya existe un usuario con esa cedula' } })));
    const component = TestBed.createComponent(UserManagementComponent).componentInstance;
    Object.assign(component, { username: 'fiel1', idNumber: '55', password: 'clave1234', roleId: 1, agencyId: 1 });
    component.onEnter('username');
    component.onEnter('desconocido');
    expect(userService.create).not.toHaveBeenCalled();
    component.onEnter('agencyId');
    expect(userService.create).toHaveBeenCalled();
    expect(component.userError()).toBe('Ya existe un usuario con esa cedula');
  });

  it('prueba del 9-nov (segunda): choosing Rol moves to Agencia, and choosing Agencia moves to Crear Usuario without saving', async () => {
    const userService = TestBed.inject(UserService) as unknown as { create: ReturnType<typeof vi.fn> };
    const fixture = TestBed.createComponent(UserManagementComponent);
    document.body.appendChild(fixture.nativeElement);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const choose = async (key: string) => {
      const select = el.querySelector<HTMLSelectElement>(`[data-field-key="${key}"]`)!;
      select.value = select.options[1].value;
      select.dispatchEvent(new Event('change'));
      fixture.detectChanges();
      await new Promise((resolve) => setTimeout(resolve));
    };

    await choose('roleId');
    expect(document.activeElement?.getAttribute('data-field-key')).toBe('agencyId');

    await choose('agencyId');
    expect(document.activeElement?.textContent?.trim()).toBe(content.createUserButton);
    expect(userService.create).not.toHaveBeenCalled();
    fixture.nativeElement.remove();
  });

  it('decision del 10-nov: an arrow on Rol only changes it; the focus stays until Enter', async () => {
    const fixture = TestBed.createComponent(UserManagementComponent);
    document.body.appendChild(fixture.nativeElement);
    fixture.detectChanges();
    await new Promise((resolve) => setTimeout(resolve)); // foco inicial en Usuario
    const select = (fixture.nativeElement as HTMLElement).querySelector<HTMLSelectElement>('[data-field-key="roleId"]')!;
    select.focus();

    select.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }));
    select.value = select.options[1].value;
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    await new Promise((resolve) => setTimeout(resolve));
    expect(document.activeElement).toBe(select);

    select.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    await new Promise((resolve) => setTimeout(resolve));
    expect(document.activeElement?.getAttribute('data-field-key')).toBe('agencyId');
    fixture.nativeElement.remove();
  });

  it('creating an agency is optional: an empty name does nothing', () => {
    const component = TestBed.createComponent(UserManagementComponent).componentInstance;
    component.createAgency();
    expect(createAgency).not.toHaveBeenCalled();
  });
});
