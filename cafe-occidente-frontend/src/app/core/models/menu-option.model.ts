/** Representa un boton de navegacion dentro de un menu tipo panel. */
export interface MenuOption {
  readonly label: string;
  readonly route?: string;
  readonly emphasis?: boolean;
}

export interface MenuOptionGroup {
  readonly primary: MenuOption[];
  readonly secondary?: MenuOption[];
}
