/** Representa un boton de navegacion dentro de un menu tipo panel. */
export interface MenuOption {
  readonly label: string;
  readonly route?: string;
  readonly emphasis?: boolean;
  /** Si es true, solo el rol ADMIN ve esta opcion (ej: Usuarios, Actualizar Anuncio). */
  readonly adminOnly?: boolean;
}

export interface MenuOptionGroup {
  readonly primary: MenuOption[];
  readonly secondary?: MenuOption[];
}
