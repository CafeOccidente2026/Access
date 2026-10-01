import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';

import { FormFieldComponent } from './form-field';
import { FormFieldDefinition } from '../../../core/models';

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
