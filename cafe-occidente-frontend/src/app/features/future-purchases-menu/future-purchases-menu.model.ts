import { MenuOption } from '../../core/models';

export interface FuturePurchasesMenuContent {
  readonly windowTitle: string;
  readonly leftOptions: MenuOption[];
  readonly leftStandaloneOption: MenuOption;
  readonly rightOptions: MenuOption[];
}
