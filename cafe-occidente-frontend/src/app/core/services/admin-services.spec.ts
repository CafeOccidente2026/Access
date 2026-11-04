import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';

import { API_BASE_URL } from '../config/api.config';
import { AgencyService } from './agency.service';
import { UserService } from './user.service';

/** HU-08 / HU-09: las llamadas nuevas de Usuarios y Agencias. */
describe('AgencyService and UserService', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  it('creates an agency with POST /agencies', () => {
    TestBed.inject(AgencyService).create('Nueva').subscribe();
    const req = http.expectOne(`${API_BASE_URL}/agencies`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ name: 'Nueva' });
    req.flush({ id: 50, name: 'Nueva' });
  });

  it('edits a user with PUT /users/{id}', () => {
    const body = { roleId: 2, agencyId: 4, active: false, password: null, idNumber: null };
    TestBed.inject(UserService).update(7, body).subscribe();
    const req = http.expectOne(`${API_BASE_URL}/users/7`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(body);
    req.flush({});
  });
});
