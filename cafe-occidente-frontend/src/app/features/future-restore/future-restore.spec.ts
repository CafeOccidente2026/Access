import { Location } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import content from '../../../../public/assets/data/future-restore.json';
import menu from '../../../../public/assets/data/future-purchases-menu.json';
import { FutureRestoreComponent } from './future-restore';
import { FuturePurchaseRestoreRow } from '../../core/models/future-purchase.model';
import { AnnouncementQuotaService } from '../../core/services/announcement-quota.service';
import { ContentService } from '../../core/services/content.service';
import { FuturePurchaseService } from '../../core/services/future-purchase.service';

describe('FutureRestoreComponent', () => {
  const wrong: FuturePurchaseRestoreRow = {
    id: 151, announcementNumber: 'SDBU-1', announcementDate: '2026-11-05', specialType: 'ESTANDAR',
    announcedKg: 1500, deliveredKg: 1000, currentBalance: 100, recalculatedBalance: 500,
  };
  const pending = { announcementNumber: 'SDBU-4', announcementDate: '2026-11-02', specialType: 'NESPRESSO - FTUSA',
    healthyUnitPrice: 9100, announcedKg: 1500, remainingKg: 1200, growers: 2 };
  const quota = { id: 9, agencyId: 1, announcementNumber: 4, announcementDate: '2026-11-02', specialType: 'RN',
    assignedQuota: 2500, deliveredKg: 1000, balance: 1500 };

  let previewSpy: ReturnType<typeof vi.fn>;
  let restoreSpy: ReturnType<typeof vi.fn>;
  let listSpy: ReturnType<typeof vi.fn>;
  let pendingSpy: ReturnType<typeof vi.fn>;

  function setup(view: 'future' | 'quota' | 'pending', pendingFails = false) {
    previewSpy = vi.fn(() => of([wrong]));
    restoreSpy = vi.fn(() => of([wrong]));
    listSpy = vi.fn(() => of([quota]));
    pendingSpy = vi.fn(() => (pendingFails ? throwError(() => new Error('500')) : of([pending])));
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ContentService, useValue: { loadJson: () => of(content) } },
        { provide: FuturePurchaseService, useValue: { restorePreview: previewSpy, restore: restoreSpy, pending: pendingSpy } },
        { provide: AnnouncementQuotaService, useValue: { list: listSpy } },
        { provide: ActivatedRoute, useValue: { snapshot: { data: { view } } } },
        { provide: Location, useValue: { back: vi.fn() } },
      ],
    });
    const fixture = TestBed.createComponent(FutureRestoreComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  const search = (c: FutureRestoreComponent, idNumber: string) => c.search(new Event('submit'), idNumber);

  it('Restaurar Compras Anunciadas first shows the stored and the recalculated balance, without writing', () => {
    const c = setup('future');
    search(c, '123.456');

    expect(previewSpy).toHaveBeenCalledWith('123456');
    expect(c.rows()).toEqual([['SDBU-1', '2026-11-05', 'ESTANDAR', '1.500,00', '1.000,00', '100,00', '500,00']]);
    expect(restoreSpy).not.toHaveBeenCalled();
    expect(c.canRestore()).toBe(true);
  });

  it('two clicks on Restaurar send a single request, and then the balance is the recalculated one', () => {
    const c = setup('future');
    search(c, '123456');
    const pending = new Subject<FuturePurchaseRestoreRow[]>();
    restoreSpy.mockReturnValue(pending);

    c.restore();
    c.restore();

    expect(restoreSpy).toHaveBeenCalledTimes(1);
    pending.next([wrong]);
    pending.complete();
    expect(c.futureRows()![0].currentBalance).toBe(500);
    expect(c.canRestore()).toBe(false);
    expect(c.message()).toBe('Saldo restaurado en 1 compra(s) anunciada(s).');
  });

  it('prueba del 11-nov (punto 4): a failed restore shows the reason of the server', () => {
    const c = setup('future');
    search(c, '99197019');
    restoreSpy.mockReturnValue(throwError(() => ({ error: { message: 'Solo el Administrador puede restaurar' } })));
    c.restore();
    expect(c.failed()).toBe('Solo el Administrador puede restaurar');
  });

  it('with the balances already right there is nothing to restore', () => {
    const c = setup('future');
    previewSpy.mockReturnValue(of([{ ...wrong, currentBalance: 500 }]));
    search(c, '123456');

    c.restore();

    expect(c.canRestore()).toBe(false);
    expect(restoreSpy).not.toHaveBeenCalled();
  });

  it('an empty idNumber asks nothing, and a failed search says so', () => {
    const c = setup('future');
    search(c, '  ');
    expect(previewSpy).not.toHaveBeenCalled();

    previewSpy.mockReturnValue(throwError(() => new Error('500')));
    search(c, '123456');
    expect(c.failed()).toBeTruthy();
  });

  it('Restaurar Anuncios con Cupos only shows each quota with its recalculated balance (P5)', () => {
    const c = setup('quota');

    expect(listSpy).toHaveBeenCalledTimes(1);
    expect(c.rows()).toEqual([['4', '2026-11-02', 'RN', '2.500,00', '1.000,00', '1.500,00']]);
    expect(restoreSpy).not.toHaveBeenCalled();
  });

  it('HU-31: Anunciadas de Hoy shows each announcement with its growers, kilos, delivered and balance, read only', () => {
    const c = setup('pending');

    expect(pendingSpy).toHaveBeenCalledTimes(1);
    expect(listSpy).not.toHaveBeenCalled();
    expect(c.rows()).toEqual([['SDBU-4', '2026-11-02', 'NESPRESSO - FTUSA', '9.100,00', '2', '1.500,00', '300,00', '1.200,00']]);
    expect(c.canRestore()).toBe(false);
  });

  it('a failed Anunciadas de Hoy says so and shows no table', () => {
    const c = setup('pending', true);

    expect(c.failed()).toBeTruthy();
    expect(c.rows()).toBeNull();
  });

  it('the menu opens Anunciadas de Hoy for everyone (prueba del 11-nov: antes "Anunciadas Hoy")', () => {
    expect(menu.leftStandaloneOption).toEqual({ label: 'Anunciadas de Hoy', route: '/compras/futuro/anunciadas-hoy' });
    expect(JSON.stringify(content)).not.toContain('Anunciadas Hoy');
  });

  it('the menu opens both screens, only for the Administrator', () => {
    const options = [...menu.leftOptions, ...menu.rightOptions];
    expect(options.find((o) => o.label === 'Restaurar Anuncios con Cupos')).toMatchObject({
      route: '/compras/futuro/restaurar-cupos', roles: ['ADMINISTRADOR'] });
    expect(options.find((o) => o.label === 'Restaurar Compras Anunciadas')).toMatchObject({
      route: '/compras/futuro/restaurar-anunciadas', roles: ['ADMINISTRADOR'] });
  });
});
