export type AnnulmentModule = 'DRY' | 'OTHER' | 'GREEN' | 'HUSK' | 'FERTI';

/** Ficha de "CONSULTA COMPRAS PARA ANULAR" (AnnulmentCandidate del backend). */
export interface AnnulmentCandidate {
  readonly module: AnnulmentModule;
  readonly id: number;
  readonly agencyId: number;
  readonly agencyName: string;
  readonly prefix: string | null;
  readonly invoiceNumber: number;
  readonly purchaseDate: string;
  readonly fundCode: string;
  readonly idNumber: string;
  readonly firstName: string;
  readonly lastName: string | null;
  readonly specialType: string | null;
  readonly netKg: number;
  readonly netToPay: number;
  readonly paymentMethod: string;
  readonly status: 'VALIDA' | 'ANULADA';
  readonly exported: boolean;
  readonly annulledAt: string | null;
}
