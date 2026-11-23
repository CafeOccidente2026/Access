import { TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { Remittance } from '../../core/models/remittance.model';
import { ContentService } from '../../core/services/content.service';
import { RemittanceService } from '../../core/services/remittance.service';
import content from '../../../../public/assets/data/remittance-unmark.json';
import { RemittanceUnmarkComponent } from './remittance-unmark';

const OLD: Remittance = {
  id: 9, number: 'TA0968', fundCode: 'RP', specialType: null, packagingQuantity: null, packagingType: null,
  status: 'VIGENTE', createdAt: '2026-01-10T14:00:00Z', purchases: 3, netKg: 500, fromRemission: false,
};

describe('RemittanceUnmarkComponent (Desmarcar remesa sin remisión)', () => {
  function setup(service: Partial<Record<keyof RemittanceService, unknown>>) {
    TestBed.configureTestingModule({
      providers: [
        { provide: RemittanceService, useValue: service },
        { provide: ContentService, useValue: { loadJson: () => of(content) } },
      ],
    });
    return TestBed.createComponent(RemittanceUnmarkComponent).componentInstance;
  }

  it('finds an old remittance and unmarks it once, even with two clicks', () => {
    const pending = new Subject<Remittance>();
    const unmark = vi.fn(() => pending);
    const component = setup({ findByNumber: () => of([OLD]), unmark });

    component.search(' TA0968 ');
    expect(component.canUnmark()).toBe(true);
    expect(component.fill(content.confirm, 'TA0968')).toContain('TA0968');

    component.unmark();
    component.unmark();
    expect(unmark).toHaveBeenCalledTimes(1);
    pending.next({ ...OLD, status: 'DESMARCADA' });
    pending.complete();

    expect(component.message()).toBe('Se desmarcó la remesa TA0968.');
    expect(component.canUnmark()).toBe(false);
  });

  it('a remittance that came with a remission is not unmarked here: it says to use Anular Remisión', () => {
    const component = setup({ findByNumber: () => of([{ ...OLD, number: 'SDBU-125', fromRemission: true }]) });

    component.search('SDBU-125');

    expect(component.canUnmark()).toBe(false);
    expect(component.message()).toBe(content.messages.fromRemission);
  });

  it('asks for the number, says when it does not exist and shows the reason of the server', () => {
    const component = setup({
      findByNumber: (n: string) => (n === 'X' ? of([]) : of([OLD])),
      unmark: () => throwError(() => ({ error: { message: 'La remesa TA0968 ya está desmarcada' } })),
    });

    component.search('  ');
    expect(component.message()).toBe(content.messages.missingNumber);
    component.search('X');
    expect(component.message()).toBe(content.messages.notFound);

    component.search('TA0968');
    component.unmark();
    expect(component.message()).toBe('La remesa TA0968 ya está desmarcada');
  });
});
