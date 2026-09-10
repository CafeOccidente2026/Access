export interface User {
  readonly id: number;
  readonly username: string;
  readonly active: boolean;
  readonly roleId: number;
  readonly roleName: string;
  readonly municipalityId: number;
  readonly municipalityName: string;
}

export interface LoginRequest {
  readonly username: string;
  readonly password: string;
}

export interface LoginResponse {
  readonly token: string;
  readonly username: string;
  readonly roleName: string;
}

export interface CreateUserRequest {
  readonly username: string;
  readonly password: string;
  readonly roleId: number;
  readonly municipalityId: number;
}
