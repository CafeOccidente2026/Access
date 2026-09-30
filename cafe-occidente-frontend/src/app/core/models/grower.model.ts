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

/** "Ingresar Conductores": conductor (= caficultor con Emp. Transp. o Vehiculo). */
export interface Conductor {
  readonly idNumber: string;
  readonly firstName: string;
  readonly lastName: string;
  readonly agencyName: string;
  readonly address: string;
  /** FechaAfiliacion: se llena al crear y nunca cambia. */
  readonly affiliationDate: string | null;
  /** Ultima actualizacion de Emp. Transp./Vehiculo (fecha y hora). */
  readonly updatedAt: string | null;
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
  /** Opcional: el alta rapida de Registrar Salidas no la envia. */
  readonly address?: string | null;
}
