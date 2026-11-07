import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Subject, of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import content from '../../../../public/assets/data/vendor-entry.json';
import { VendorEntryComponent } from './vendor-entry';
import { ContentService } from '../../core/services/content.service';
import { NavigationService } from '../../core/services/navigation.service';
import { VendorService } from '../../core/services/vendor.service';

describe('VendorEntryComponent', () => {
  function setup() {
    const response = new Subject<{ idNumber: string }>();
    const create = vi.fn(() => response);
    const goTo = vi.fn();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ContentService, useValue: { loadJson: () => of(content) } },
        { provide: VendorService, useValue: { create } },
        { provide: NavigationService, useValue: { goTo } },
      ],
    });
    const fixture = TestBed.createComponent(VendorEntryComponent);
    fixture.detectChanges();
    return { c: fixture.componentInstance, fixture, create, goTo, response };
  }

  function choose(c: VendorEntryComponent, kind: 'legal' | 'natural') {
    c.choice.set(kind);
    c.accept(new Event('submit'));
  }

  it('HU-50 CA-1: Persona Natural is the default and opens the natural person form', () => {
    const { c } = setup();
    c.accept(new Event('submit'));
    expect(c.kind()).toBe('natural');
    expect(c.fields().map((f) => f.label)).toContain('1er Nombre');
  });

  it('HU-51 CA-1: Persona Jurídica opens the form with Razón Social and Clasificación', () => {
    const { c, fixture } = setup();
    choose(c, 'legal');
    fixture.detectChanges();
    const classification: HTMLSelectElement = fixture.nativeElement.querySelector('select[data-field-key="classification"]');
    expect(c.fields().map((f) => f.label)).toContain('Razón Social');
    expect([...classification.options].map((o) => o.textContent?.trim())).toEqual(['', 'Asociación', 'Fundación']);
  });

  it('a missing required field is warned and nothing is sent', () => {
    const { c, create } = setup();
    choose(c, 'legal');
    c.set('idNumber', '900723205');
    c.set('businessName', 'ASOPEMA');
    c.save();
    expect(c.message()).toBe('Falta: Clasificación');
    expect(create).not.toHaveBeenCalled();
  });

  it('two clicks on Guardar send one request, and the form is cleared after saving', () => {
    const { c, create, response } = setup();
    choose(c, 'legal');
    c.set('idNumber', '900723205');
    c.set('businessName', 'ASOPEMA');
    c.set('classification', 'ASOCIACION');

    c.save();
    c.save();
    expect(create).toHaveBeenCalledTimes(1);
    expect(c.saving()).toBe(true);

    response.next({ idNumber: '900723205' });
    response.complete();
    expect(c.saving()).toBe(false);
    expect(c.values()).toEqual({});
    expect(c.message()).toBe('Tercero 900723205 guardado. Puede ingresar el siguiente.');
  });

  it('a duplicate NIT says so', () => {
    const { c, response } = setup();
    choose(c, 'natural');
    c.set('idNumber', '59124124');
    c.set('firstName', 'AURA');
    c.save();
    response.error({ error: { message: 'Ya existe un asociado o vendedor con esa cedula' } });
    expect(c.error()).toBe(true);
    expect(c.message()).toBe(content.messages.duplicate);
    expect(c.saving()).toBe(false);
  });

  it('Cancelar goes back to the Vendedores menu', () => {
    const { c, goTo } = setup();
    c.back();
    expect(goTo).toHaveBeenCalledWith('/vendedores');
  });
});
