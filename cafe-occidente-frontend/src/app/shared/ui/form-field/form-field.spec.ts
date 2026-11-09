import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';

import { FormFieldComponent } from './form-field';
import { FormFieldDefinition } from '../../../core/models';
import dry from '../../../../../public/assets/data/purchase-form-dry.json';
import future from '../../../../../public/assets/data/purchase-form-future.json';
import green from '../../../../../public/assets/data/purchase-form-green.json';
import husk from '../../../../../public/assets/data/purchase-form-husk.json';
import quotaPurchase from '../../../../../public/assets/data/purchase-form-quota-purchase.json';
import query from '../../../../../public/assets/data/purchase-query.json';

describe('FormFieldComponent formatWhenIdle', () => {
  function setup(field: FormFieldDefinition) {
    const component = TestBed.createComponent(FormFieldComponent).componentInstance;
    component.formatWhenIdle = true;
    component.field = field;
    component.ngOnChanges();
    return component;
  }

  it('shows the Colombian format while idle and the raw value while editing', () => {
    const component = setup({ key: 'baseWithholding', label: 'Base', type: 'currency', value: '8379840' });
    expect(component.inputValue).toBe('8.379.840,00');

    component.onFocus();
    expect(component.inputValue).toBe('8379840');

    // Mientras tiene el foco, el valor que vuelve del padre no reescribe el input (moveria el cursor).
    component.field = { ...component.field, value: '837984' };
    component.inputValue = '837984';
    component.ngOnChanges();
    expect(component.inputValue).toBe('837984');

    component.onBlur();
    expect(component.inputValue).toBe('837.984,00');
  });

  it('leaves rawDisplay and text fields untouched', () => {
    expect(setup({ key: 'resolutionTo', label: 'Hasta', type: 'number', rawDisplay: true, value: '48753' }).inputValue).toBe('48753');
    expect(setup({ key: 'prefix', label: 'Prefijo', type: 'text', value: 'SDTA' }).inputValue).toBe('SDTA');
  });

  it('keeps the purchase-form behavior when the flag is off: raw while editable', () => {
    const component = TestBed.createComponent(FormFieldComponent).componentInstance;
    component.field = { key: 'kg', label: 'Kg', type: 'count', value: '70' };
    component.ngOnChanges();
    expect(component.inputValue).toBe('70');
  });
});

describe('FormFieldComponent numeric input (HU-07 CA-3)', () => {
  it('a letter typed after a valid digit does not stay visible in the input', async () => {
    const fixture = TestBed.createComponent(FormFieldComponent);
    fixture.componentRef.setInput('field', { key: 'baseLoad', label: 'Base Carga', type: 'number', value: '' });
    fixture.detectChanges();
    await fixture.whenStable();
    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    for (const typed of ['1', '1a', '12', '12b', '125']) {
      input.value = typed;
      input.dispatchEvent(new Event('input'));
      fixture.detectChanges();
      await fixture.whenStable();
      expect(input.value, `after typing "${typed}"`).toBe(typed.replace(/[^0-9]/g, ''));
    }
  });
});

describe('FormFieldComponent negativeOnly (Castigo, Q1)', () => {
  it('turns a positive penalty negative before announcing the commit, on blur and on Enter', () => {
    const component = TestBed.createComponent(FormFieldComponent).componentInstance;
    component.field = { key: 'penalty', label: 'Castigo', type: 'currency', negativeOnly: true };
    const emitted: (string | number)[] = [];
    let commits = 0;
    component.valueChange.subscribe((v) => emitted.push(v));
    component.committed.subscribe(() => commits++);

    component.inputValue = '23';
    component.onBlur();
    component.inputValue = '';
    component.commit();

    expect(emitted).toEqual(['-23', '0']);
    expect(commits).toBe(2);
  });

  it('does not touch other fields', () => {
    const component = TestBed.createComponent(FormFieldComponent).componentInstance;
    component.field = { key: 'tare', label: 'Destare', type: 'currency' };
    const emitted: (string | number)[] = [];
    component.valueChange.subscribe((v) => emitted.push(v));
    component.inputValue = '23';
    component.commit();

    expect(emitted).toEqual([]);
  });
});

describe('FormFieldComponent labelAbove', () => {
  it('puts the whole label above the box, without cutting it', () => {
    const fixture = TestBed.createComponent(FormFieldComponent);
    fixture.componentRef.setInput('field', { key: 'fullName', label: 'Nombres y Apellidos', type: 'text' });
    fixture.componentRef.setInput('labelAbove', true);
    fixture.detectChanges();
    const label = (fixture.nativeElement as HTMLElement).querySelector('label')!;
    expect(label.classList).toContain('flex-col');
    expect(label.querySelector('span')!.classList).not.toContain('truncate');
  });
});

describe('FormFieldComponent accessible name', () => {
  function input(field: FormFieldDefinition): HTMLInputElement {
    const fixture = TestBed.createComponent(FormFieldComponent);
    fixture.componentRef.setInput('field', field);
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).querySelector('input')!;
  }

  it('names each box with its label, or with ariaLabel when the label is hidden like in Access', () => {
    expect(input({ key: 'totalStoredWeight', label: 'Peso Tot Alm', type: 'count' }).getAttribute('aria-label'))
      .toBe('Peso Tot Alm');
    expect(input({ key: 'firstName', label: '', ariaLabel: 'Nombres', type: 'text' }).getAttribute('aria-label'))
      .toBe('Nombres');
  });

  it('every field of the purchase screens has a name to be found and typed in', () => {
    const screens = [dry, green, husk, future, quotaPurchase, query];
    const unnamed = screens
      .flatMap((screen) => Object.values(screen).filter(Array.isArray).flat())
      .filter((f): f is FormFieldDefinition => typeof f === 'object' && f !== null && 'key' in f && 'label' in f)
      .filter((f) => !f.label && !f.ariaLabel)
      .map((f) => f.key);
    expect(unnamed).toEqual([]);
  });
});

describe('FormFieldComponent full value', () => {
  it('shows the whole value on hover, for an address longer than its box', () => {
    const fixture = TestBed.createComponent(FormFieldComponent);
    const address = 'VEREDA LA PALMA KILOMETRO 12 VIA AL MAR FRENTE A LA ESCUELA RURAL';
    fixture.componentRef.setInput('field', { key: 'address', label: 'Dirección', type: 'text', readonly: true, value: address });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('input')!.title).toBe(address);
  });
});

describe('FormFieldComponent liveThousands', () => {
  it('shows thousand separators while typing a value and passes on only the digits', () => {
    const fixture = TestBed.createComponent(FormFieldComponent);
    fixture.componentRef.setInput('field', { key: 'amount', label: 'Vr. Suministro', type: 'currency', liveThousands: true });
    fixture.detectChanges();
    const emitted: (string | number)[] = [];
    fixture.componentInstance.valueChange.subscribe((v) => emitted.push(v));
    const input = (fixture.nativeElement as HTMLElement).querySelector('input')!;

    input.value = '140000';
    input.dispatchEvent(new Event('input'));

    expect(input.value).toBe('140.000');
    expect(emitted.at(-1)).toBe('140000');

    // El valor que vuelve del padre (solo digitos) se sigue viendo con puntos mientras es editable.
    fixture.componentRef.setInput('field', { key: 'amount', label: 'Vr. Suministro', type: 'currency', liveThousands: true, value: '1400000' });
    fixture.detectChanges();
    expect(fixture.componentInstance.inputValue).toBe('1.400.000');
  });
});
