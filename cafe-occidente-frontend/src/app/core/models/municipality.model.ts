export interface Municipality {
  readonly id: number;
  readonly name: string;
  readonly active: boolean;
}

export interface CreateMunicipalityRequest {
  readonly name: string;
}
