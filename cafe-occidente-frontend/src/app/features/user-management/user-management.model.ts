import { UserRole } from '../../core/models';

export interface UserManagementContent {
  readonly windowTitle: string;
  readonly listHeaders: {
    readonly username: string;
    readonly fullName: string;
    readonly role: string;
    readonly active: string;
  };
  readonly activeLabel: string;
  readonly inactiveLabel: string;
  readonly deactivateLabel: string;
  readonly createSectionTitle: string;
  readonly usernameLabel: string;
  readonly passwordLabel: string;
  readonly fullNameLabel: string;
  readonly roleLabel: string;
  readonly roleOptions: readonly { readonly value: UserRole; readonly label: string }[];
  readonly createButtonLabel: string;
  readonly errorLabel: string;
  readonly closeRoute: string;
}

export interface UserRecord {
  readonly id: number;
  readonly username: string;
  readonly fullName: string;
  readonly role: UserRole;
  readonly active: boolean;
}
