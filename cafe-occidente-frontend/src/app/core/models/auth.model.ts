export type UserRole = 'ADMIN' | 'PURCHASE_AGENT';

export interface AuthSession {
  readonly username: string;
  readonly role: UserRole;
}

/** Forma exacta de la respuesta de /auth/login y /auth/refresh en el backend. */
export interface LoginResponseDto {
  readonly accessToken: string;
  readonly refreshToken: string;
  readonly username: string;
  readonly role: UserRole;
}
