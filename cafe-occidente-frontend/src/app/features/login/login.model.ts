export interface LoginContent {
  readonly windowTitle: string;
  readonly userLabel: string;
  readonly passwordLabel: string;
  readonly acceptLabel: string;
  readonly cancelLabel: string;
  readonly successRoute: string;
  readonly userSuccessRoute: string;
  readonly showPasswordLabel: string;
  readonly hidePasswordLabel: string;
  readonly invalidCredentialsError: string;
  readonly userDisabledError: string;
}

export interface ShellContent {
  readonly appTitle: string;
}
