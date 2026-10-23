import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
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
    Object.assign(component, { username: 'fiel1', password: 'ab12', roleId: 1, agencyId: 1 });

    component.createUser();
    expect(userService.create).not.toHaveBeenCalled();
    expect(component.userError()).toBe(content.idNumberRequiredMessage);

    component.idNumber = '1061699481';
    component.createUser();
    expect(userService.create).toHaveBeenCalledWith({ username: 'fiel1', idNumber: '1061699481', password: 'ab12', roleId: 1, agencyId: 1 });
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

  it('creating an agency is optional: an empty name does nothing', () => {
    const component = TestBed.createComponent(UserManagementComponent).componentInstance;
    component.createAgency();
    expect(createAgency).not.toHaveBeenCalled();
  });
});
