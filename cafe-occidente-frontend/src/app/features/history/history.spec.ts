import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import content from '../../../../public/assets/data/history.json';
import { ChangeHistoryEntry } from '../../core/models/history.model';
import { routes } from '../../app.routes';
import { API_BASE_URL } from '../../core/config/api.config';
import { AgencyService } from '../../core/services/agency.service';
import { ContentService } from '../../core/services/content.service';
import { HistoryService } from '../../core/services/history.service';
import { UserService } from '../../core/services/user.service';
import { HistoryComponent } from './history';

const reassigned: ChangeHistoryEntry = {
  id: 9, at: '2026-10-23T10:15:00-05:00', username: 'admin', entity: 'USUARIO', entityId: 5, action: 'REASIGNAR',
  agencyName: 'El Tambo', before: { agencyName: 'Buesaco', active: 'true' }, after: { agencyName: 'El Tambo', active: 'false', password: 'cambiada' },
};
const created: ChangeHistoryEntry = {
  id: 8, at: '2026-10-23T09:00:00-05:00', username: 'admin', entity: 'AGENCIA', entityId: 50, action: 'CREAR',
  agencyName: 'Nueva', before: null, after: { name: 'Nueva' },
};

describe('HistoryComponent', () => {
  let search: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    search = vi.fn(() => of({ items: [reassigned, created], total: 2 }));
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ContentService, useValue: { loadJson: () => of(content) } },
        { provide: AgencyService, useValue: { list: () => of([{ id: 4, name: 'El Tambo' }]) } },
        { provide: UserService, useValue: { list: () => of([{ id: 1, username: 'admin' }]) } },
        { provide: HistoryService, useValue: { search } },
      ],
    });
  });

  it('opens with the latest changes and shows who, what, the action and before → after', async () => {
    const fixture = TestBed.createComponent(HistoryComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    const text = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="history"]')!.textContent!.replace(/\s+/g, ' ');
    expect(search).toHaveBeenCalledWith(expect.objectContaining({ page: 0 }));
    expect(text).toContain('Reasignar');
    expect(text).toContain('Agencia: Buesaco → El Tambo');
    expect(text).toContain('Activo: Sí → No');
    expect(text).toContain('Contraseña: cambiada');
    expect(text).toContain('Nombre: Nueva');
    expect(text).not.toContain('→ Nueva');
  });

  it('sends the filters chosen by the Administrator and shows an error if the query fails', () => {
    const component = TestBed.createComponent(HistoryComponent).componentInstance;
    Object.assign(component, { entity: 'USUARIO', agencyId: 4, userId: 1, from: '2026-10-01', to: '2026-10-23' });
    component.search(2);
    expect(search).toHaveBeenLastCalledWith({ entity: 'USUARIO', agencyId: 4, userId: 1, from: '2026-10-01', to: '2026-10-23', page: 2 });
    expect(component.pageIndex()).toBe(2);

    search.mockReturnValue(throwError(() => new Error('500')));
    component.search();
    expect(component.error()).toBe(true);
  });

  it('RF-02: with the screen granted, a non administrator only filters by their own agency, without users', () => {
    localStorage.setItem('cafeoccidente.role', 'USUARIO');
    localStorage.setItem('cafeoccidente.agencyId', '1');
    localStorage.setItem('cafeoccidente.agencyName', 'Buesaco 1');
    const fixture = TestBed.createComponent(HistoryComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.agencies()).toEqual([{ id: 1, name: 'Buesaco 1' }]);
    expect(fixture.componentInstance.users()).toEqual([]);
    expect((fixture.nativeElement as HTMLElement).querySelectorAll('select')).toHaveLength(2); // Que y Agencia, sin Usuario
    localStorage.clear();
  });

  it('only the Administrator can open the route', () => {
    const shell = routes.find((r) => r.children?.some((c) => c.path === 'historial'));
    expect(shell?.children?.find((c) => c.path === 'historial')?.data?.['roles']).toEqual(['ADMINISTRADOR']);
    expect(shell?.children?.find((c) => c.path === 'historial')?.data?.['screens']).toEqual(['HISTORIAL']);
  });
});

describe('HistoryService', () => {
  it('sends only the filters that have a value', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    TestBed.inject(HistoryService).search({ entity: 'ANUNCIO', agencyId: null, from: '', page: 0 }).subscribe();
    const req = TestBed.inject(HttpTestingController).expectOne((r) => r.url === `${API_BASE_URL}/history`);
    expect(req.request.params.keys()).toEqual(['entity', 'page']);
    expect(req.request.params.get('entity')).toBe('ANUNCIO');
    req.flush({ items: [], total: 0 });
  });
});
