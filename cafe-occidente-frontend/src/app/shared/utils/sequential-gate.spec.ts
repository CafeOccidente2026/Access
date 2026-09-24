import { describe, expect, it } from 'vitest';
import { isSequentialFieldEnabled } from './sequential-gate';

describe('isSequentialFieldEnabled', () => {
  const focusOrder = ['fund', 'idPart1', 'special', 'bags', 'grossKg'] as const;

  it('enables a field outside the focus order (never gated)', () => {
    expect(isSequentialFieldEnabled('otherField', focusOrder, new Set())).toBe(true);
  });

  it('enables the first field in the order when nothing is locked yet', () => {
    expect(isSequentialFieldEnabled('fund', focusOrder, new Set())).toBe(true);
  });

  it('blocks fields further down the order until the current one is confirmed', () => {
    expect(isSequentialFieldEnabled('special', focusOrder, new Set())).toBe(false);
  });

  it('enables exactly the next unlocked field once earlier ones are confirmed', () => {
    const locked = new Set(['fund', 'idPart1']);
    expect(isSequentialFieldEnabled('special', focusOrder, locked)).toBe(true);
    expect(isSequentialFieldEnabled('bags', focusOrder, locked)).toBe(false);
  });

  it('keeps a field enabled once it is already locked, even out of turn', () => {
    // Caso real: un dato migrado vino vacio y no se bloqueo (ver dry-coffee-form.ts lookupGrower) -
    // el campo queda habilitado para que el cajero lo complete a mano, aunque no sea "su turno".
    const locked = new Set(['fund']);
    expect(isSequentialFieldEnabled('fund', focusOrder, locked)).toBe(true);
  });

  it('enables every remaining field once the whole order is locked', () => {
    const locked = new Set(focusOrder);
    expect(isSequentialFieldEnabled('grossKg', focusOrder, locked)).toBe(true);
  });
});
