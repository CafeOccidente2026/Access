export interface ControlRecordResponse {
  readonly id: number;
  readonly agencyId: number;
  readonly agencyName: string;
  readonly active: boolean;
  readonly controlNumber: number;
  readonly baseFactor: number;
  readonly baseWithholding: number;
  readonly baseLoad: number;
  readonly withholdingPercentage: number;
  readonly baseHusk: number;
  readonly avgHuskPercentage: number;
  readonly purchasePoint: string;
  readonly prefix: string;
  readonly costs: number;
  readonly sampleSize: number;
  readonly excelsoKg: number;
  readonly greenCoffeePercentage: number;
  readonly associatePercentage: number;
  readonly nonAssociateDiscount: number;
  readonly trustedId: string;
  readonly dianResolution: string;
  readonly resolutionDate: string;
  readonly resolutionFrom: number;
  readonly resolutionTo: number;
  readonly validity: number;
  readonly specialtyThreshold: number;
  readonly greenTotalStoredWeight: number;
  readonly greenTotalHuskWeight: number;
  readonly greenHealthyStoredWeight: number;
  readonly greenHealthyUnitPrice: number;
  readonly greenDefectiveUnitPrice: number;
}

export type ControlRecordRequest = Omit<ControlRecordResponse, 'id' | 'agencyId' | 'agencyName' | 'active'>;
