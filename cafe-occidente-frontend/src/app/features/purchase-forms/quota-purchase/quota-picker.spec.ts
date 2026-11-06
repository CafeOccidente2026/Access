import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';

import { QuotaPick, QuotaPickerComponent } from './quota-picker';
import { QuotaBalance } from '../../../core/models/announcement-quota.model';

describe('QuotaPickerComponent', () => {
  const row = (number: string, balance: number): QuotaBalance => ({
    announcement: {
      id: 1, announcementNumber: number, announcementDate: '2026-11-02', basePriceLoad: 1546900,
      defectiveUnitPrice: 6000, healthyUnitPrice: 8900.5, bonus: 40, costs: 692, agencyId: 1, fundId: 1,
      specialType: 'RN', qualityIncrement: 50,
    },
    assignedQuota: 2500,
    deliveredKg: 2500 - balance,
    balance,
  });

  function setup(rows: QuotaBalance[]) {
    const fixture = TestBed.createComponent(QuotaPickerComponent);
    const picker = fixture.componentInstance;
    picker.content = {
      title: 'ANUNCIOS CON CUPO', columns: {}, deliveryLabel: 'Esta Entrega',
      overBalance: 'La entrega no debe ser superior al saldo', acceptLabel: 'Aceptar', cancelLabel: 'Cancelar',
    };
    picker.agency = 'Buesaco 1';
    picker.rows = rows;
    const picks: QuotaPick[] = [];
    picker.picked.subscribe((p) => picks.push(p));
    return { picker, picks };
  }

  it('shows the RN-21.2.4 data of each announcement, with the agency of the session', () => {
    const { picker } = setup([row('SDBU-12', 1500)]);
    const r = picker.list[0];

    expect(picker.columns).toHaveLength(13);
    expect(picker.cell(r, 'announcement')).toBe('SDBU-12');
    expect(picker.cell(r, 'agency')).toBe('Buesaco 1');
    expect(picker.cell(r, 'healthyUnitPrice')).toBe('8.900,50');
    expect(picker.cell(r, 'balance')).toBe('1.500,00');
  });

  it('CA-2: a delivery within the balance closes the window with the chosen announcement', () => {
    const { picker, picks } = setup([row('SDBU-12', 1500), row('SDBU-13', 300)]);
    picker.select(picker.list[1]);
    picker.delivery.set('300');

    picker.accept();

    expect(picks).toEqual([{ row: picker.list[1], deliveryKg: 300 }]);
  });

  it('CA-3: a delivery over the balance is not accepted', () => {
    const { picker, picks } = setup([row('SDBU-12', 1500)]);
    picker.delivery.set('1500,01');

    picker.accept();

    expect(picks).toEqual([]);
    expect(picker.error()).toBe('La entrega no debe ser superior al saldo');
    expect(picker.delivery()).toBe('');
  });

  it('typing again clears the previous error', () => {
    const fixture = TestBed.createComponent(QuotaPickerComponent);
    const picker = fixture.componentInstance;
    picker.content = { title: '', columns: {}, deliveryLabel: 'Esta Entrega', overBalance: 'Supera el saldo',
      acceptLabel: 'Aceptar', cancelLabel: 'Cancelar' };
    picker.agency = '';
    picker.rows = [row('SDBU-12', 100)];
    fixture.detectChanges();
    picker.delivery.set('200');
    picker.accept();
    expect(picker.error()).toBe('Supera el saldo');

    const input = fixture.nativeElement.querySelector('[data-field-key="quotaDelivery"]') as HTMLInputElement;
    input.value = '50';
    input.dispatchEvent(new Event('input'));

    expect(picker.error()).toBeNull();
    expect(picker.delivery()).toBe('50');
  });

  it('an empty delivery does nothing', () => {
    const { picker, picks } = setup([row('SDBU-12', 1500)]);
    picker.accept();
    expect(picks).toEqual([]);
    expect(picker.error()).toBeNull();
  });
});
