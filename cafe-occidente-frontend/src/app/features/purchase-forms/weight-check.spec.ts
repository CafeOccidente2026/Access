import { signal } from '@angular/core';
import { describe, expect, it } from 'vitest';

import { checkWeight, weightProblem } from './weight-check';

const messages = {
  grossNotPositive: 'Los Kilos Brutos deben ser mayores que 0',
  tareNotBelowGross: 'El destare no puede ser mayor o igual que los Kilos Brutos',
  almondNotPositive: 'Peso Almendra debe ser mayor que 0',
  almondOverSample: 'Peso Almendra no puede ser mayor que la muestra ({sample} g)',
};

/** Hallazgos #5 y #10 (16-nov): los pesos se revisan al confirmarlos, con las reglas del servidor. */
describe('weight-check', () => {
  it('Kilos Brutos must be over 0 and Destare under Kilos Brutos', () => {
    expect(weightProblem('grossKg', { grossKg: '0' })).toBe('grossNotPositive');
    expect(weightProblem('grossKg', { grossKg: '2' })).toBeNull();
    // Hallazgo #5: Sacos 500, Brutos 2, Destare 2 dejaba Kilos Netos en 0.
    expect(weightProblem('tare', { grossKg: '2', tare: '2' })).toBe('tareNotBelowGross');
    expect(weightProblem('tare', { grossKg: '2', tare: '3' })).toBe('tareNotBelowGross');
    expect(weightProblem('tare', { grossKg: '2', tare: '1,5' })).toBeNull();
    expect(weightProblem('bags', { bags: '0' })).toBeNull();
  });

  it('Peso Almendra must be over 0 and within the sample', () => {
    expect(weightProblem('almondWeight', { almondWeight: '0' }, 250)).toBe('almondNotPositive');
    expect(weightProblem('almondWeight', { almondWeight: '260' }, 250)).toBe('almondOverSample');
    expect(weightProblem('almondWeight', { almondWeight: '250' }, 250)).toBeNull();
    expect(weightProblem('almondWeight', { almondWeight: '260' }, null)).toBeNull();
  });

  it('a rejected value is cleared and unlocked, the warning shown and the field kept for the focus', () => {
    const host = { model: { grossKg: '2', tare: '2' } as Record<string, string>, errorMessage: signal<string | null>(null) };
    const locked = new Set(['grossKg', 'tare']);

    expect(checkWeight(host, locked, 'tare', messages)).toBe('tare');
    expect(host.model['tare']).toBe('');
    expect(locked.has('tare')).toBe(false);
    expect(host.errorMessage()).toBe(messages.tareNotBelowGross);

    host.model['almondWeight'] = '300';
    checkWeight(host, locked, 'almondWeight', messages, 250);
    expect(host.errorMessage()).toBe('Peso Almendra no puede ser mayor que la muestra (250 g)');
    expect(checkWeight(host, locked, 'grossKg', messages)).toBeNull();
  });
});
