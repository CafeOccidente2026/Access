import { FormFieldDefinition } from '../../core/models/form-field.model';
import { PurchaseFormContent } from '../../core/models/purchase-form-content.model';
import { PurchaseQueryDetail } from '../../core/models/purchase-query.model';
import { formatThousands, stripAnnouncementPrefix } from '../../shared/utils/number-format';

type Value = string | number | null | undefined;

/**
 * Los numeros van crudos y las fechas en ISO: el campo de solo lectura los formatea segun su tipo
 * (2 decimales cortados, o enteros con "integer" en el JSON). Lo que cambia entre modulos: el verde
 * trae Kilos Verdes y su muestra fija; la pasilla su almendra (Peso Alm Sana y su % en Factor) y el
 * precio base de carga en otro campo. Lo que no aplica queda vacio.
 */
function moduleValues(detail: PurchaseQueryDetail): Record<string, Value> {
  switch (detail.module) {
    case 'DRY': {
      const p = detail.purchase;
      return {
        basePriceDryLoad: p.basePriceLoad, healthyStoredPrice: p.healthyUnitPrice,
        defectStoredPrice: p.defectiveUnitPrice, bonus: p.bonus, penalty: p.penalty,
        totalStoredWeight: p.totalStoredWeight, wastePercentage: p.wastePercentage,
        totalHuskWeight: p.defectiveStoredWeight, huskPercentage: p.defectivePercentage,
        healthyStoredWeight: p.healthyStoredWeight, factor: p.healthyPercentage,
        greenKg: '', shrinkageDiscount: p.freightDiscount,
      };
    }
    case 'GREEN': {
      const p = detail.purchase;
      const s = p.sample;
      return {
        basePriceDryLoad: p.basePriceLoad, healthyStoredPrice: p.healthyUnitPrice,
        defectStoredPrice: p.defectiveUnitPrice, bonus: p.bonus, penalty: p.penalty,
        totalStoredWeight: s?.totalStoredWeight, wastePercentage: s?.wastePercentage,
        totalHuskWeight: s?.huskWeight, huskPercentage: s?.huskPercentage,
        healthyStoredWeight: s?.healthyStoredWeight, factor: s?.factor,
        greenKg: p.greenKg, shrinkageDiscount: p.shrinkageDiscount,
      };
    }
    case 'HUSK': {
      const p = detail.purchase;
      return {
        basePriceDryLoad: p.basePriceDryLoad, healthyStoredWeight: p.almondWeight,
        factor: p.almondPercentage, greenKg: '', shrinkageDiscount: p.shrinkageDiscount,
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
    purchaseDate: p.purchaseDate,
    announcement: stripAnnouncementPrefix(p.announcementNumber),
    announcementDate: p.announcementDate,
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
    costs: p.costs,
    bags: p.bagsCount,
    grossKg: p.grossKg,
    tare: p.tareKg,
    netKg: p.netKg,
    kgPrice: p.unitPrice,
    grossValue: p.grossValue,
    netToPay: p.netToPay,
    associateContribution: p.associateContribution,
    coopDiscount: p.cooperativeDiscount,
    withholding: p.withholding,
    otherDiscounts: p.otherDiscounts,
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
