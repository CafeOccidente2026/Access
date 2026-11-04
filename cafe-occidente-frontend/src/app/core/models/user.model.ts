import { Screen } from './auth.model';

export interface User {
  readonly id: number;
  readonly username: string;
  /** Cedula del fiel de compra; null en los usuarios creados antes de V7. */
  readonly idNumber: string | null;
  readonly active: boolean;
  readonly roleId: number;
  readonly roleName: string;
  readonly agencyId: number;
  readonly agencyName: string;
}

export interface LoginRequest {
  readonly username: string;
  readonly password: string;
}

/** Sesion del servidor (GET /api/me): rol, agencia de trabajo (RF-05.1) y pantallas concedidas (RF-02). */
export interface SessionResponse {
  readonly username: string;
  readonly roleName: string;
  readonly agencyId: number;
  readonly agencyName: string;
  readonly screens?: readonly Screen[];
}

export interface LoginResponse extends SessionResponse {
  readonly token: string;
}

export interface CreateUserRequest {
  readonly username: string;
  readonly idNumber: string;
  readonly password: string;
  readonly roleId: number;
  readonly agencyId: number;
}

/** Edicion por el Administrador: password e idNumber vacios o null no cambian nada. */
export interface UpdateUserRequest {
  readonly roleId: number;
  readonly agencyId: number;
  readonly active: boolean;
  readonly password: string | null;
  readonly idNumber: string | null;
}
