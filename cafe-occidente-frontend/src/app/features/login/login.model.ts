export interface LoginContent {
  readonly windowTitle: string;
  readonly userLabel: string;
  readonly passwordLabel: string;
  readonly acceptLabel: string;
  readonly cancelLabel: string;
  readonly successRoute: string;
  readonly errorLabel: string;
}

export interface ShellContent {
  readonly appTitle: string;
}
