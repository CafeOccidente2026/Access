export interface Agency {
  readonly id: number;
  readonly name: string;
  /** Sin Registro de Control la agencia todavia no puede operar (fase 3, C6). */
  readonly hasControlRecord?: boolean;
  /** Prefijo que traia de Access, para prellenar su Registro de Control (D2). */
  readonly suggestedPrefix?: string | null;
}
