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
import { UserManagementComponent } from './user-management';

describe('UserManagementComponent', () => {
  let createAgency: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    createAgency = vi.fn(() => of({ id: 50, name: 'Nueva', hasControlRecord: false, suggestedPrefix: null }));
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
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

  it('creating an agency is optional: an empty name does nothing', () => {
    const component = TestBed.createComponent(UserManagementComponent).componentInstance;
    component.createAgency();
    expect(createAgency).not.toHaveBeenCalled();
  });
});
