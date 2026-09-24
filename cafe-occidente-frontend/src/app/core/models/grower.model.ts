export interface Grower {
  readonly id: number;
  readonly idNumber: string;
  readonly firstName: string;
  readonly secondName: string | null;
  readonly lastName: string;
  readonly secondLastName: string | null;
  readonly address: string;
  readonly phone: string;
  readonly growerType: string;
  readonly active: boolean;
  readonly deceased: boolean;
  readonly withdrawn: boolean;
  readonly transportCompany: string | null;
  readonly vehiclePlate: string | null;
}

/** Programa/Cupo informativos (staging_legacy_ness) - ver GrowerService.findProgram. */
export interface GrowerProgram {
  readonly programa: string;
  readonly cupo: string;
}

/** Alta rápida de conductor (Grower sin datos de caficultor) - ver GrowerService.createConductor. */
export interface GrowerCreateRequest {
  readonly idNumber: string;
  readonly firstName: string;
  readonly secondName: string | null;
  readonly lastName: string;
  readonly secondLastName: string | null;
  readonly agencyId: number;
  readonly transportCompany: string | null;
  readonly vehiclePlate: string | null;
}
