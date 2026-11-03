import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';

import { ComboboxComponent } from './combobox';

describe('ComboboxComponent', () => {
  it('gives its input the accessible name it receives (the visible label is outside the component)', () => {
    const fixture = TestBed.createComponent(ComboboxComponent);
    fixture.componentRef.setInput('options', ['RN', 'CV']);
    fixture.componentRef.setInput('ariaLabel', 'Especialidad');
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('input')!.getAttribute('aria-label')).toBe('Especialidad');
  });

  it('without a name it adds no empty aria-label', () => {
    const fixture = TestBed.createComponent(ComboboxComponent);
    fixture.componentRef.setInput('options', []);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('input')!.hasAttribute('aria-label')).toBe(false);
  });
});
