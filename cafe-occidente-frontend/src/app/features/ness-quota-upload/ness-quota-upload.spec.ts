import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';

import { API_BASE_URL } from '../../core/config/api.config';
import { NessQuotaUploadRecord, NessQuotaUploadResult } from '../../core/models/vendor.model';
import { ContentService } from '../../core/services/content.service';
import content from '../../../../public/assets/data/ness-quota-upload.json';
import { NessQuotaUploadComponent } from './ness-quota-upload';

const URL = `${API_BASE_URL}/vendors/ness-quotas`;
const FILE = new File(['xlsx'], 'NESS 2026.xlsx');

const result = (over: Partial<NessQuotaUploadResult> = {}): NessQuotaUploadResult => ({
  errors: [], warnings: [], rows: 3, added: 1, changed: 1, unchanged: 1, removed: 1, ...over,
});
const RECORD: NessQuotaUploadRecord = {
  id: 1, date: '2026-11-04T10:30:00-05:00', username: 'admin', fileName: 'NESS 2025.xlsx', rows: 17535,
  added: 0, changed: 0, unchanged: 17535, removed: 0,
};

describe('NessQuotaUploadComponent (HU-54, RF-28.4)', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ContentService, useValue: { loadJson: () => of(content) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  async function settle(): Promise<void> {
    await Promise.resolve();
    TestBed.tick();
    await Promise.resolve();
  }

  /** Abre la pantalla y responde el historial. */
  async function render(history: NessQuotaUploadRecord[] = [RECORD]) {
    const fixture = TestBed.createComponent(NessQuotaUploadComponent);
    fixture.detectChanges();
    await settle();
    http.expectOne(`${URL}/uploads`).flush(history);
    await settle();
    fixture.detectChanges();
    return { fixture, component: fixture.componentInstance, text: () => fixture.nativeElement.textContent as string };
  }

  /** Cargar archivo: manda el archivo a la vista previa y responde. */
  async function choose(component: NessQuotaUploadComponent, preview: NessQuotaUploadResult) {
    component.choose(FILE);
    await settle();
    const request = http.expectOne((r) => r.url === `${URL}/upload/preview`);
    expect(request.request.method).toBe('POST');
    expect(request.request.params.get('fileName')).toBe('NESS 2026.xlsx');
    expect(request.request.body).toBe(FILE);
    request.flush(preview);
    await settle();
  }

  it('the file input has an accessible name', async () => {
    const { fixture } = await render();

    const input = fixture.nativeElement.querySelector('[data-testid="file"]') as HTMLInputElement;
    expect(input.getAttribute('aria-label')).toBe(content.loadLabel);
  });

  it('shows the previous loads, newest first as the server sends them', async () => {
    const { text } = await render();

    expect(text()).toContain('NESS 2025.xlsx');
    expect(text()).toContain('admin');
  });

  it('previews how many quotas are added, changed, unchanged and removed before applying', async () => {
    const { fixture, component, text } = await render();

    await choose(component, result({ added: 4, changed: 3, unchanged: 2, removed: 5, rows: 9 }));
    fixture.detectChanges();

    expect(component.preview()).toMatchObject({ added: 4, changed: 3, unchanged: 2, removed: 5 });
    expect(component.canApply()).toBe(true);
    expect(text()).toContain(content.counts.removed);
    http.expectNone(`${URL}/upload`);
  });

  it('CA-2: lists the errors (for example, the missing column) and does not let apply', async () => {
    const { fixture, component, text } = await render();

    await choose(component, result({ errors: ['Falta la columna PROGRAMA'], rows: 0 }));
    fixture.detectChanges();

    expect(text()).toContain('Falta la columna PROGRAMA');
    expect(component.canApply()).toBe(false);
    component.apply();
    http.expectNone(`${URL}/upload`);
  });

  it('shows the warnings of repeated cedulas', async () => {
    const { fixture, component, text } = await render();

    await choose(component, result({ warnings: ['Filas 2 y 3: la cédula 98376216 se repite en NESPRESSO'] }));
    fixture.detectChanges();

    expect(text()).toContain('Filas 2 y 3: la cédula 98376216 se repite en NESPRESSO');
    expect(component.canApply()).toBe(true);
  });

  it('CA-1: Aplicar sends the same file once even with two clicks, shows the RF message and reloads the history',
    async () => {
      const { component } = await render();
      await choose(component, result());

      component.apply();
      component.apply();
      expect(component.saving()).toBe(true);
      const requests = http.match((r) => r.url === `${URL}/upload`);
      expect(requests).toHaveLength(1);
      expect(requests[0].request.params.get('fileName')).toBe('NESS 2026.xlsx');
      expect(requests[0].request.body).toBe(FILE);
      requests[0].flush(result());
      await settle();

      expect(component.saving()).toBe(false);
      expect(component.message()).toBe(content.messages.applied);
      expect(component.file()).toBeUndefined();
      http.expectOne(`${URL}/uploads`).flush([RECORD, RECORD]);
      await settle();
      expect(component.history()).toHaveLength(2);
    });

  it('if the server rejects the file on apply, shows its errors and keeps the file', async () => {
    const { component } = await render();
    await choose(component, result());

    component.apply();
    http.expectOne((r) => r.url === `${URL}/upload`).flush(result({ errors: ['Fila 5: el cupo «x» no es un número válido'] }));
    await settle();

    expect(component.errors()).toEqual(['Fila 5: el cupo «x» no es un número válido']);
    expect(component.message()).toBeNull();
    expect(component.file()).toBe(FILE);
    http.expectNone(`${URL}/uploads`);
  });

  it('shows a message when the request fails', async () => {
    const { component } = await render();
    await choose(component, result());

    component.apply();
    http.expectOne((r) => r.url === `${URL}/upload`).flush(null, { status: 500, statusText: 'Error' });
    await settle();

    expect(component.message()).toBe(content.messages.error);
    expect(component.saving()).toBe(false);
  });

  it('Cancelar discards the file without calling the server', async () => {
    const { component } = await render();
    await choose(component, result());

    component.cancel();
    await settle();

    expect(component.file()).toBeUndefined();
    expect(component.preview()).toBeUndefined();
    http.expectNone(`${URL}/upload`);
  });
});
