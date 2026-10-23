import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthService } from '../services/auth.service';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    localStorage.clear();
    localStorage.setItem('cafeoccidente.token', 'jwt');
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
  });

  it('sends the JWT in the Authorization header', () => {
    http.get('/api/funds').subscribe();
    expect(backend.expectOne('/api/funds').request.headers.get('Authorization')).toBe('Bearer jwt');
  });

  it('does not send a previous JWT with the login', () => {
    http.post('/api/auth/login', {}).subscribe();
    expect(backend.expectOne('/api/auth/login').request.headers.has('Authorization')).toBe(false);
  });

  it('closes the session and goes to the login when the server answers 401', () => {
    http.get('/api/funds').subscribe({ error: () => undefined });
    backend.expectOne('/api/funds').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(TestBed.inject(AuthService).isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith('/login');
  });

  it('a 401 from the login itself is just wrong credentials', () => {
    http.post('/api/auth/login', {}).subscribe({ error: () => undefined });
    backend.expectOne('/api/auth/login').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(TestBed.inject(AuthService).isAuthenticated()).toBe(true);
    expect(navigate).not.toHaveBeenCalled();
  });
});
