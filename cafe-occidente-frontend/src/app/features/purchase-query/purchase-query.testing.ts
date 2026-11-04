import { DryCoffeePurchaseResponse } from '../../core/models/dry-coffee-purchase.model';
import { GreenCoffeePurchaseResponse } from '../../core/models/green-coffee-purchase.model';
import { HuskPurchaseResponse } from '../../core/models/husk-purchase.model';
import { PurchaseQueryDetail } from '../../core/models/purchase-query.model';

/** Compras de ejemplo para las pruebas de Consultar Compras (una por modulo). */
const header = {
  purchaseDate: '2026-10-29', agencyName: 'Buesaco 1', fundCode: 'RP', productCode: '0110002000002',
  announcementNumber: 'SDBU-4', announcementDate: '2026-10-29', idNumber: '990006003', firstName: 'PRUEBA',
  lastName: 'CONSULTA', growerType: 'C', address: 'VEREDA', cellphone: '3001112233', paymentMethod: 'MIXTO',
  checkNumber: '4321', purchasePoint: 'BUESACO', prefix: 'SDBU', dianResolution: '1', resolutionDate: '2026-01-01',
  resolutionFrom: 1, resolutionTo: 99999, validity: 12, bagsCount: 5, grossKg: 1000, tareKg: 0, costs: 0,
  associateContribution: 0, cooperativeDiscount: 99336, withholding: 62085, otherDiscounts: 0,
};

export const DRY = {
  ...header, id: 1225, invoiceNumber: 27869, agencyId: 1, fundId: 1, specialType: 'RN', basePriceLoad: 1546900,
  netKg: 1000, totalStoredWeight: 200, wastePercentage: 20, defectiveStoredWeight: 10, defectivePercentage: 5,
  healthyStoredWeight: 184.22, healthyPercentage: 92.11, healthyUnitPrice: 12000, defectiveUnitPrice: 6000, bonus: 80000,
  penalty: 0, unitPrice: 12417, grossValue: 12417000, inventoryValue: 12417000, withholdingExempt: false,
  freightDiscount: 0, netToPay: 12255579, createdByUserId: 1, productName: 'CARE R.P. RN',
} as DryCoffeePurchaseResponse;

export const GREEN = {
  ...header, id: 44, invoiceNumber: 27875, specialType: 'CV', basePriceLoad: 1546900, greenKg: 1000, netKg: 500,
  healthyUnitPrice: 0, defectiveUnitPrice: 6000, bonus: 80000, penalty: 0, compKgPrice: 12000, unitPrice: 24000,
  grossValue: 12000000, inventoryValue: 12000000, shrinkageDiscount: 0, netToPay: 11844000,
  sample: { totalStoredWeight: 140, wastePercentage: 44, huskWeight: 12.5, huskPercentage: 5, healthyStoredWeight: 186.18, factor: 94 },
} as GreenCoffeePurchaseResponse;

export const HUSK = {
  ...header, id: 24, invoiceNumber: 27872, specialType: 'PASILLA', basePriceDryLoad: 1546900, pointPrice: 5000,
  almondWeight: 200, almondPercentage: 48, netKg: 1000, unitPrice: 4108, grossValue: 4108000, inventoryValue: 4108000,
  shrinkageDiscount: 0, netToPay: 4000000,
} as HuskPurchaseResponse;

export const MIXED = { cashAmount: 11655579, checkAmount: 300000, transferAmount: 200000, cardAmount: 100000, checkNumber: '4321' };

export function detail(over: Partial<PurchaseQueryDetail> = {}): PurchaseQueryDetail {
  return { module: 'DRY', purchase: DRY, status: 'VALIDA', payment: MIXED, remissions: [], ...over } as PurchaseQueryDetail;
}
