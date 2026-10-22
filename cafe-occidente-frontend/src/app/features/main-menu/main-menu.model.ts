import { MenuOption } from '../../core/models';

export interface MainMenuContent {
  readonly windowTitle: string;
  readonly heading: string;
  readonly logo: { readonly src: string; readonly alt: string };
  /** Boton Agencias (RF-05.1), solo para el Administrador. */
  readonly agencies: { readonly label: string; readonly errorMessage: string };
  readonly options: MenuOption[];
}
