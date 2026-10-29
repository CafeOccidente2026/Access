import { FormFieldDefinition } from '../../core/models/form-field.model';
import { PurchaseFormContent } from '../../core/models/purchase-form-content.model';
import { PurchaseQueryDetail } from '../../core/models/purchase-query.model';
import { formatDisplayNumber, formatThousands, stripAnnouncementPrefix } from '../../shared/utils/number-format';
import { shortDate } from '../conductor-form/conductor-form';

type Value = string | number | null | undefined;

/** Kilos, pesos de la muestra y porcentajes: 2 decimales cortados (Q3); vacio si no aplica. */
const decimals = (v: number | null | undefined): string => (v == null ? '' : formatDisplayNumber(v, 'currency'));
/** Precios por unidad en pesos enteros. */
const pesos = (v: number | null | undefined): string => (v == null ? '' : formatDisplayNumber(v, 'count'));

/**
 * Lo que cambia entre modulos: el verde trae Kilos Verdes y su muestra fija; la pasilla su almendra
 * (Peso Alm Sana y su % en Factor) y el precio base de carga en otro campo. Lo que no aplica queda vacio.
 */
function moduleValues(detail: PurchaseQueryDetail): Record<string, Value> {
  switch (detail.module) {
    case 'DRY': {
      const p = detail.purchase;
      return {
        basePriceDryLoad: pesos(p.basePriceLoad), healthyStoredPrice: pesos(p.healthyUnitPrice),
        defectStoredPrice: pesos(p.defectiveUnitPrice), bonus: pesos(p.bonus), penalty: pesos(p.penalty),
        totalStoredWeight: decimals(p.totalStoredWeight), wastePercentage: decimals(p.wastePercentage),
        totalHuskWeight: decimals(p.defectiveStoredWeight), huskPercentage: decimals(p.defectivePercentage),
        healthyStoredWeight: decimals(p.healthyStoredWeight), factor: decimals(p.healthyPercentage),
        greenKg: '', shrinkageDiscount: decimals(p.freightDiscount),
      };
    }
    case 'GREEN': {
      const p = detail.purchase;
      const s = p.sample;
      return {
        basePriceDryLoad: pesos(p.basePriceLoad), healthyStoredPrice: pesos(p.healthyUnitPrice),
        defectStoredPrice: pesos(p.defectiveUnitPrice), bonus: pesos(p.bonus), penalty: pesos(p.penalty),
        totalStoredWeight: decimals(s?.totalStoredWeight), wastePercentage: decimals(s?.wastePercentage),
        totalHuskWeight: decimals(s?.huskWeight), huskPercentage: decimals(s?.huskPercentage),
        healthyStoredWeight: decimals(s?.healthyStoredWeight), factor: decimals(s?.factor),
        greenKg: decimals(p.greenKg), shrinkageDiscount: decimals(p.shrinkageDiscount),
      };
    }
    case 'HUSK': {
      const p = detail.purchase;
      return {
        basePriceDryLoad: pesos(p.basePriceDryLoad), healthyStoredWeight: decimals(p.almondWeight),
        factor: decimals(p.almondPercentage), greenKg: '', shrinkageDiscount: decimals(p.shrinkageDiscount),
      };
    }
  }
}

/**
 * "Consultar Compras" (Cns_Compras, RF-26): la ficha de solo lectura con todos los datos de la compra
 * (RN-26.4), su ESTADO (RN-26.5) y sus formas de pago guardadas, incluida la transferencia.
 */
export function fillQueryContent(content: PurchaseFormContent, detail: PurchaseQueryDetail): PurchaseFormContent {
  const p = detail.purchase;
  const values: Record<string, Value> = {
    agency: p.agencyName,
    purchaseDate: shortDate(p.purchaseDate),
    announcement: stripAnnouncementPrefix(p.announcementNumber),
    announcementDate: shortDate(p.announcementDate),
    remission: detail.remissions.join(', '),
    fund: p.fundCode,
    invoice: p.prefix,
    invoiceNumber: String(p.invoiceNumber),
    productCode: p.productCode,
    idNumber: /^\d+$/.test(p.idNumber) ? formatThousands(p.idNumber) : p.idNumber,
    firstName: p.firstName,
    lastName: p.lastName,
    idType: p.growerType,
    address: p.address,
    cellphone: p.cellphone,
    special: p.specialType,
    costs: pesos(p.costs),
    bags: pesos(p.bagsCount),
    grossKg: decimals(p.grossKg),
    tare: decimals(p.tareKg),
    netKg: decimals(p.netKg),
    kgPrice: pesos(p.unitPrice),
    grossValue: decimals(p.grossValue),
    netToPay: decimals(p.netToPay),
    associateContribution: decimals(p.associateContribution),
    coopDiscount: decimals(p.cooperativeDiscount),
    withholding: decimals(p.withholding),
    otherDiscounts: decimals(p.otherDiscounts),
    status: detail.status,
    ...moduleValues(detail),
  };
  const fill = (f: FormFieldDefinition): FormFieldDefinition => ({ ...f, readonly: true, value: values[f.key] ?? '' });
  const all = (fields?: FormFieldDefinition[]) => fields?.map(fill);
  return {
    ...content,
    topFields: content.topFields.map(fill),
    identificationFields: content.identificationFields.map(fill),
    contactFields: all(content.contactFields),
    qualityFields: all(content.qualityFields),
    weightFields: all(content.weightFields),
    netWeightFields: all(content.netWeightFields),
    settlementFields: all(content.settlementFields),
    additionalDiscountFields: all(content.additionalDiscountFields),
    discountField: content.discountField && fill(content.discountField),
    statusField: content.statusField && fill(content.statusField),
    paymentSaved: detail.payment,
    paymentLocked: true,
  };
}
