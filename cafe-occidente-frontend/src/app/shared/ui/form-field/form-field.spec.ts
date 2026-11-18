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

  // Hallazgo #1 (16-nov): dinero sin decimales, kilos sin ",00" si son enteros y la muestra como se digitó.
  it('formats money, kilos and typed values the same in every purchase screen', () => {
    const shown = (display: 'money' | 'kilos' | 'typed', value: string) =>
      setup({ key: 'x', label: 'X', type: 'currency', display, value }).inputValue;
    expect(shown('money', '1546900.00')).toBe('1.546.900');
    expect(shown('kilos', '500')).toBe('500');
    expect(shown('kilos', '101.5')).toBe('101,50');
    expect(shown('typed', '7,1')).toBe('7,1');
    const keys = (data: object) => JSON.stringify(data).match(/"key":"(netToPay|netKg|wastePercentage)"[^}]*"display":"\w+"/g) ?? [];
    expect(keys(dry)).toHaveLength(3);
    expect(keys(quotaPurchase)).toHaveLength(3);
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

describe('FormFieldComponent fast typing', () => {
  it('a late echo of what was typed does not overwrite the following digits (Asignar Cupo, prueba del 9-nov)', () => {
    const component = TestBed.createComponent(FormFieldComponent).componentInstance;
    component.field = { key: 'quota', label: 'Cupo', type: 'number', value: '' };
    component.ngOnChanges();
    component.onFocus();

    component.onInput('4');
    component.onInput('40');
    component.field = { key: 'quota', label: 'Cupo', type: 'number', value: '4' };
    component.ngOnChanges();

    expect(component.inputValue).toBe('40');

    // Un valor distinto que manda la pantalla (p. ej. al limpiar) si se aplica.
    component.field = { key: 'quota', label: 'Cupo', type: 'number', value: '' };
    component.ngOnChanges();
    expect(component.inputValue).toBe('');
  });
});

describe('FormFieldComponent liveThousands con un valor guardado', () => {
  it('a saved value with decimals (Registro de Control: 8379840,00) shows its whole pesos with thousands', () => {
    const fixture = TestBed.createComponent(FormFieldComponent);
    fixture.componentRef.setInput('field', { key: 'baseWithholding', label: 'Base Rete Fuente', type: 'currency', liveThousands: true, value: '8379840,00' });
    fixture.detectChanges();
    expect(fixture.componentInstance.inputValue).toBe('8.379.840');
  });
});

describe('FormFieldComponent liveThousands en Castigo y al borrar', () => {
  const penalty = { key: 'penalty', label: 'Castigo', type: 'currency' as const, liveThousands: true, negativeOnly: true };

  it('Castigo shows the thousands while typing and turns negative when confirmed', () => {
    const fixture = TestBed.createComponent(FormFieldComponent);
    fixture.componentRef.setInput('field', penalty);
    fixture.detectChanges();
    const emitted: (string | number)[] = [];
    fixture.componentInstance.valueChange.subscribe((v) => emitted.push(v));
    const input = (fixture.nativeElement as HTMLElement).querySelector('input')!;

    input.value = '2300';
    input.dispatchEvent(new Event('input'));
    expect(input.value).toBe('2.300');
    expect(emitted.at(-1)).toBe('2300');

    fixture.componentInstance.commit();
    expect(emitted.at(-1)).toBe('-2300');
    expect(fixture.componentInstance.inputValue).toBe('-2.300');

    // Ya confirmado (solo lectura) se sigue viendo con puntos.
    fixture.componentRef.setInput('field', { ...penalty, readonly: true, value: '-2300' });
    fixture.detectChanges();
    expect(fixture.componentInstance.inputValue).toBe('-2.300');
  });

  it('Backspace right after a dot deletes the digit, not the dot; other fields are not touched', () => {
    const fixture = TestBed.createComponent(FormFieldComponent);
    fixture.componentRef.setInput('field', { key: 'otherDiscounts', label: 'Otros Dscts', type: 'currency', liveThousands: true, value: '45690' });
    fixture.detectChanges();
    const input = (fixture.nativeElement as HTMLElement).querySelector('input')!;
    input.value = '45.690';
    input.setSelectionRange(3, 3);
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Backspace' }));
    expect(input.selectionStart).toBe(2);

    fixture.componentRef.setInput('field', { key: 'grossKg', label: 'Kilos Brutos', type: 'number' });
    fixture.detectChanges();
    input.value = '1.250';
    input.setSelectionRange(2, 2);
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Backspace' }));
    expect(input.selectionStart).toBe(2);
  });
});
