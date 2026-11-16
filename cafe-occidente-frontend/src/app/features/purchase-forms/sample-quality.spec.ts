import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { applyQualityPercentages, loadSampleQuality } from './sample-quality';
import { DryCoffeePurchaseService } from '../../core/services/dry-coffee-purchase.service';

describe('sample-quality', () => {
  it('asks the waste with the total and the factor with both weights (Q2), then fills the form', () => {
    const q = { wastePercentage: 20, defectivePercentage: 4, healthyStoredWeight: 190, healthyPercentage: 92.11 };
    const qualityPercentages = vi.fn(() => of(q));
    const service = { qualityPercentages } as unknown as DryCoffeePurchaseService;
    const model: Record<string, string> = { totalStoredWeight: '200', totalHuskWeight: '10' };
    const locked = new Set<string>();
    const done = vi.fn();

    loadSampleQuality(service, model, locked, 'totalStoredWeight', done);
    loadSampleQuality(service, model, locked, 'totalHuskWeight', done);

    expect(qualityPercentages.mock.calls).toEqual([[200], [200, 10]]);
    expect(model).toMatchObject({ wastePercentage: '20', huskPercentage: '4', healthyStoredWeight: '190', factor: '92.11' });
    expect(done).toHaveBeenCalledTimes(2);
  });

  it('16-nov: a weight the server rejects (over the sample) clears what it fed and reports the error', () => {
    const err = new HttpErrorResponse({ status: 400, error: { message: 'Peso Tot Alm no puede ser mayor que la muestra (250 g)' } });
    const service = { qualityPercentages: vi.fn(() => throwError(() => err)) } as unknown as DryCoffeePurchaseService;
    const model: Record<string, string> = { totalStoredWeight: '850', wastePercentage: '20' };
    const locked = new Set<string>(['wastePercentage']);
    const done = vi.fn();
    const onError = vi.fn();

    loadSampleQuality(service, model, locked, 'totalStoredWeight', done, onError);

    expect(onError).toHaveBeenCalledWith(err);
    expect(done).not.toHaveBeenCalled();
    expect(model['wastePercentage']).toBe('');
    expect(locked.has('wastePercentage')).toBe(false);
  });

  it('writes and locks the read-only fields, empty when the server sends nothing', () => {
    const model: Record<string, string> = {};
    const locked = new Set<string>();

    applyQualityPercentages(model, locked, 'totalHuskWeight', {
      wastePercentage: null, defectivePercentage: 4, healthyStoredWeight: null, healthyPercentage: 92.11,
    });

    expect(model).toEqual({ huskPercentage: '4', healthyStoredWeight: '', factor: '92.11' });
    expect([...locked]).toEqual(['huskPercentage', 'healthyStoredWeight', 'factor']);
  });
});
