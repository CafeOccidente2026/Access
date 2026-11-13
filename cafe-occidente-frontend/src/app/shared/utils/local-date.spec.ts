import { localToday } from './local-date';

describe('localToday', () => {
  it('gives the local day as yyyy-mm-dd, not the UTC one (after 7 p. m. in Colombia it was already tomorrow)', () => {
    // 12-nov a las 22:30 hora local: en UTC ya es 13-nov.
    expect(localToday(new Date(2026, 10, 12, 22, 30))).toBe('2026-11-12');
    expect(localToday(new Date(2026, 0, 5, 0, 1))).toBe('2026-01-05');
  });

  it('defaults to now', () => {
    const now = new Date();
    expect(localToday()).toBe(localToday(now));
  });
});
