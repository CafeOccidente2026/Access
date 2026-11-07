import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import content from '../../../../public/assets/data/future-report.json';
import menu from '../../../../public/assets/data/future-purchases-menu.json';
import { FutureReportComponent } from './future-report';
import { ContentService } from '../../core/services/content.service';
import { NavigationService } from '../../core/services/navigation.service';
import { PurchaseReportService } from '../../core/services/purchase-report.service';

describe('FutureReportComponent', () => {
  let announcementSpy: ReturnType<typeof vi.fn>;
  let balanceSpy: ReturnType<typeof vi.fn>;
  let goTo: ReturnType<typeof vi.fn>;

  function setup(view: 'announcement' | 'balance') {
    announcementSpy = vi.fn(() => of([]));
    balanceSpy = vi.fn(() => of([]));
    goTo = vi.fn();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ContentService, useValue: { loadJson: () => of(content) } },
        { provide: PurchaseReportService, useValue: { futureAnnouncement: announcementSpy, futureBalance: balanceSpy } },
        { provide: NavigationService, useValue: { goTo } },
        { provide: ActivatedRoute, useValue: { snapshot: { data: { view } } } },
      ],
    });
    const fixture = TestBed.createComponent(FutureReportComponent);
    fixture.detectChanges();
    return { c: fixture.componentInstance, fixture };
  }

  it('RN-21.6.1: Anuncios a Futuro asks for the announcement and both dates before Aceptar', () => {
    const { c } = setup('announcement');
    c.announcement.set('3443');
    expect(c.canAccept()).toBe(false);
    c.fromDate.set('2026-05-01');
    c.toDate.set('2026-05-31');

    c.onAccept();
    TestBed.tick();

    expect(c.canAccept()).toBe(true);
    expect(announcementSpy).toHaveBeenCalledWith(3443, '2026-05-01', '2026-05-31');
  });

  it('From after To is warned and Aceptar does nothing', () => {
    const { c } = setup('announcement');
    c.announcement.set('3443');
    c.fromDate.set('2026-06-01');
    c.toDate.set('2026-05-01');

    c.onAccept();

    expect(c.message()).toBe(content.messages.fromAfterTo);
    expect(announcementSpy).not.toHaveBeenCalled();
  });

  it('RN-21.7.1: Saldo Anuncios only asks "Digite el número de anuncio a imprimir"', () => {
    const { c, fixture } = setup('balance');
    expect(fixture.nativeElement.textContent).toContain('Digite el número de anuncio a imprimir');
    expect(fixture.nativeElement.querySelector('[data-testid="from"]')).toBeNull();
    c.announcement.set('abc');
    expect(c.canAccept()).toBe(false);
    c.announcement.set(' 3443 ');

    c.onAccept();
    TestBed.tick();

    expect(balanceSpy).toHaveBeenCalledWith(3443);
    expect(c.message()).toBe(content.messages.empty);
  });

  it('a failed report says so', () => {
    const { c } = setup('balance');
    balanceSpy.mockReturnValue(throwError(() => new Error('500')));
    c.announcement.set('3443');

    c.onAccept();
    TestBed.tick();

    expect(c.message()).toBe(content.messages.error);
  });

  it('Cancelar goes back to the Compras a Futuro menu', () => {
    const { c } = setup('balance');
    c.onCancel();
    expect(goTo).toHaveBeenCalledWith('/compras/futuro');
  });

  it('the menu opens both reports', () => {
    const options = menu.rightOptions;
    expect(options.find((o) => o.label === 'Generar Informe Anuncios a Futuro')?.route).toBe('/compras/futuro/informe-anuncios');
    expect(options.find((o) => o.label === 'Generar Informe Saldo Anuncios')?.route).toBe('/compras/futuro/informe-saldo');
  });
});
