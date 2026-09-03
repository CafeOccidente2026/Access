import { MenuOption } from '../../core/models';

export interface MainMenuContent {
  readonly windowTitle: string;
  readonly heading: string;
  readonly logo: { readonly src: string; readonly alt: string };
  readonly options: MenuOption[];
}
