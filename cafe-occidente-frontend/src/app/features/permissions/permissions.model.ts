import { Screen } from '../../core/models';

export interface PermissionsContent {
  readonly windowTitle: string;
  readonly userLabel: string;
  readonly choose: string;
  readonly instructions: string;
  readonly saveButton: string;
  readonly currentHeading: string;
  readonly noneMessage: string;
  readonly revokeButton: string;
  readonly grantedSince: string;
  readonly savedMessage: string;
  readonly revokedMessage: string;
  readonly errorMessage: string;
  /** Catalogo de pantallas que se pueden conceder (RN-02.1), en el orden en que se muestran. */
  readonly screens: Record<Screen, string>;
}
