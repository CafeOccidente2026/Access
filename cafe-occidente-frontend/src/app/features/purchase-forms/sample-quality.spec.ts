import { describe, expect, it } from 'vitest';

import { applyQualityPercentages, qualityRequestArgs } from './sample-quality';

describe('sample-quality', () => {
  it('asks the waste with the total and the factor with both weights (Q2)', () => {
    const model = { totalStoredWeight: '200', totalHuskWeight: '10' };
    expect(qualityRequestArgs(model, 'totalStoredWeight')).toEqual([200]);
    expect(qualityRequestArgs(model, 'totalHuskWeight')).toEqual([200, 10]);
  });

  it('writes and locks the read-only fields of each weight', () => {
    const model: Record<string, string> = {};
    const locked = new Set<string>();
    const q = { wastePercentage: 20, defectivePercentage: 4, healthyStoredWeight: 190, healthyPercentage: 92.11 };

    applyQualityPercentages(model, locked, 'totalStoredWeight', q);
    expect(model).toEqual({ wastePercentage: '20' });

    applyQualityPercentages(model, locked, 'totalHuskWeight', { ...q, healthyStoredWeight: null });
    expect(model).toMatchObject({ huskPercentage: '4', healthyStoredWeight: '', factor: '92.11' });
    expect([...locked]).toEqual(['wastePercentage', 'huskPercentage', 'healthyStoredWeight', 'factor']);
  });
});
