import { Screen } from './auth.model';

/** Permiso vigente de un usuario (RF-02): dura hasta que el Administrador lo retire. */
export interface Permission {
  readonly screen: Screen;
  readonly grantedAt: string;
}
