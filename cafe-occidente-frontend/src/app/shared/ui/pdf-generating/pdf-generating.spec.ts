import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { describe, expect, it } from 'vitest';

import content from '../../../../../public/assets/data/pdf.json';
import { ContentService } from '../../../core/services/content.service';
import { PdfGeneratingComponent } from './pdf-generating';

describe('PdfGeneratingComponent (R3)', () => {
  function render(active: boolean): HTMLElement {
    TestBed.configureTestingModule({
      providers: [{ provide: ContentService, useValue: { loadJson: () => of(content) } }],
    });
    const fixture = TestBed.createComponent(PdfGeneratingComponent);
    fixture.componentRef.setInput('active', active);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('says "Generando PDF…" while the PDF is being built', () => {
    expect(render(true).textContent?.trim()).toBe('Generando PDF…');
  });

  it('shows nothing otherwise', () => {
    TestBed.resetTestingModule();
    expect(render(false).textContent?.trim()).toBe('');
  });
});
