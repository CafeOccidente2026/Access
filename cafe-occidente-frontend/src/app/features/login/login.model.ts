export interface LoginContent {
  readonly windowTitle: string;
  readonly userLabel: string;
  readonly passwordLabel: string;
  readonly acceptLabel: string;
  readonly cancelLabel: string;
  readonly successRoute: string;
  readonly showPasswordLabel: string;
  readonly hidePasswordLabel: string;
  readonly invalidCredentialsError: string;
}

export interface ShellContent {
  readonly appTitle: string;
}
