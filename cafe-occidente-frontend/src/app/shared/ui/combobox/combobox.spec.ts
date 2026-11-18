import { TestBed } from '@angular/core/testing';
import { describe, expect, it, vi } from 'vitest';

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

describe('ComboboxComponent on leaving', () => {
  it('does not send the same value again (Asignar Cupo cleared Cupo when that late echo arrived, prueba del 9-nov)', () => {
    vi.useFakeTimers();
    const fixture = TestBed.createComponent(ComboboxComponent);
    fixture.componentRef.setInput('options', ['SDTA-3491 · RN · 2450000']);
    fixture.componentRef.setInput('value', 'SDTA-3491 · RN · 2450000');
    fixture.detectChanges();
    const values: string[] = [];
    let commits = 0;
    fixture.componentInstance.valueChange.subscribe((v: string) => values.push(v));
    fixture.componentInstance.committed.subscribe(() => commits++);

    fixture.componentInstance.onBlur();
    vi.runAllTimers();

    expect(values).toEqual([]);
    expect(commits).toBe(1);
    vi.useRealTimers();
  });
});

describe('ComboboxComponent flow-next', () => {
  function render() {
    const fixture = TestBed.createComponent(ComboboxComponent);
    fixture.componentRef.setInput('options', ['RP', 'LF']);
    fixture.detectChanges();
    const input = (fixture.nativeElement as HTMLElement).querySelector('input')!;
    const announced: number[] = [];
    input.addEventListener('flow-next', () => announced.push(1));
    return { fixture, input, announced };
  }

  it('announces flow-next when an option is chosen with the mouse', () => {
    const { fixture, announced } = render();
    fixture.componentInstance.onOptionMouseDown(new MouseEvent('mousedown'), 'RP');
    expect(announced).toHaveLength(1);
  });

  it('announces flow-next on Enter over the exact text, but not on text that is no option', () => {
    const { fixture, announced } = render();
    fixture.componentInstance.inputText = 'lf';
    fixture.componentInstance.onKeydown(new KeyboardEvent('keydown', { key: 'Enter' }));
    expect(announced).toHaveLength(1);

    fixture.componentInstance.inputText = 'XX';
    fixture.componentInstance.onKeydown(new KeyboardEvent('keydown', { key: 'Enter' }));
    expect(announced).toHaveLength(1);
  });
});
