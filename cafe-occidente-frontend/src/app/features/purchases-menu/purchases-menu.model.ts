import { MenuOption } from '../../core/models';

export interface PurchasesMenuContent {
  readonly windowTitle: string;
  readonly sideOptions: MenuOption[];
  readonly options: MenuOption[];
}
