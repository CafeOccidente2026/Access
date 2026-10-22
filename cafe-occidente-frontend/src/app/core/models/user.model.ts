export interface User {
  readonly id: number;
  readonly username: string;
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

/** Sesion del servidor (GET /api/me): rol y agencia de trabajo (RF-05.1). */
export interface SessionResponse {
  readonly username: string;
  readonly roleName: string;
  readonly agencyId: number;
  readonly agencyName: string;
}

export interface LoginResponse extends SessionResponse {
  readonly token: string;
}

export interface CreateUserRequest {
  readonly username: string;
  readonly password: string;
  readonly roleId: number;
  readonly agencyId: number;
}
