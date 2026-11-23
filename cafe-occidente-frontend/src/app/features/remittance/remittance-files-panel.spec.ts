import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { API_BASE_URL } from '../../core/config/api.config';
import { RemittanceFiles } from '../../core/models/remittance.model';
import { ContentService } from '../../core/services/content.service';
import content from '../../../../public/assets/data/remittance-files.json';
import { RemittanceFilesComponent } from './remittance-files-panel';

const FILES: RemittanceFiles = { number: 'SDXX-125', agencyName: 'PUNTO', generatedAt: '2026-11-03T09:00:00', lines: [] };

describe('RemittanceFilesComponent (los 4 archivos de la remesa)', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ContentService, useValue: { loadJson: () => of(content) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  async function render(ready = true) {
    const fixture = TestBed.createComponent(RemittanceFilesComponent);
    fixture.componentRef.setInput('remittanceId', 9);
    fixture.detectChanges();
    await Promise.resolve();
    TestBed.tick();
    const component = fixture.componentInstance;
    const saveFile = vi.fn();
    component.saveFile = saveFile;
    if (ready) {
      http.expectOne(`${API_BASE_URL}/remittances/9/files`).flush(FILES);
      await vi.waitFor(() => expect(component.filesReady()).toBe(true));
    }
    return { fixture, component, saveFile };
  }

  it('each file has its own button: the xlsx is built beforehand and the click downloads it at once', async () => {
    const { component, saveFile } = await render();

    // Sin esperar nada entre el clic y la descarga: Chrome solo deja pasar las que salen del clic.
    component.download(component.books()[3]);

    expect(saveFile).toHaveBeenCalledTimes(1);
    const [name, blob] = saveFile.mock.calls[0] as [string, Blob];
    expect(name).toBe('ParaAlmacafe_SDXX-125.xlsx');
    expect(new TextDecoder().decode(new Uint8Array(await blob.arrayBuffer()).slice(0, 2))).toBe('PK');
  });

  it('Descargar las 4 downloads the 4 prebuilt files in the same click and explains the Chrome prompt', async () => {
    const { fixture, component, saveFile } = await render();
    fixture.detectChanges();
    const labels = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('app-button'))
      .map((b) => b.textContent?.trim());
    expect(labels).toEqual(['Para Operaciones', 'Para Open', 'Genera Plano', 'Para Almacafé', content.downloadAllLabel]);

    component.downloadAll();

    expect(saveFile.mock.calls.map(([name]) => name)).toEqual([
      'ParaOperaciones_SDXX-125.xlsx', 'ParaOpen_SDXX-125.xlsx', 'SDXX-125.xlsx', 'ParaAlmacafe_SDXX-125.xlsx',
    ]);
    expect(component.message()).toBe(content.messages.downloadAllHint);
  });

  it('only warns while the files are not ready', async () => {
    const { component, saveFile } = await render(false);

    component.downloadAll();
    component.download({ fileName: 'x.xlsx', sheet: 'ParaOpen', data: [] });

    expect(saveFile).not.toHaveBeenCalled();
    expect(component.message()).toBe(content.messages.filesError);
  });
});
