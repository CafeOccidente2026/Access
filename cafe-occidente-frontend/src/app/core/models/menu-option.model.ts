import { UserRole } from './auth.model';

/** Representa un boton de navegacion dentro de un menu tipo panel. */
export interface MenuOption {
  readonly label: string;
  readonly route?: string;
  readonly emphasis?: boolean;
  /** Roles que ven la opcion (RF-01, RN-05.1). Si falta, la ven todos los que entran a ese menu. */
  readonly roles?: readonly UserRole[];
  /** Boton visible pero inactivo (funcion de Access que no aplica todavia); hint explica por que. */
  readonly disabled?: boolean;
  readonly hint?: string;
}

export interface MenuOptionGroup {
  readonly primary: MenuOption[];
  readonly secondary?: MenuOption[];
}
