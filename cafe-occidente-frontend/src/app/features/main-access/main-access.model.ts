import { MenuOption } from '../../core/models';

export interface MainAccessContent {
  readonly windowTitle: string;
  readonly welcomeLabel: string;
  readonly userName: string;
  readonly options: MenuOption[];
  readonly exitLabel: string;
}
