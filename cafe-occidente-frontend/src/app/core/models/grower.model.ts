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
}
