export type VendorKind = 'association' | 'person';

/** Campo de "Vendedores" / "Asociaciones1". "agency" usa app-agency-picker; besideSex pone el combo
 *  Sexo en la misma fila (como en el formulario de Access). */
export interface VendorField {
  readonly key: string;
  readonly label: string;
  readonly required?: boolean;
  readonly wide?: boolean;
  readonly besideSex?: string;
}

export interface VendorEntryContent {
  readonly dialog: {
    readonly windowTitle: string;
    readonly question: string;
    readonly options: { readonly value: VendorKind; readonly label: string }[];
    readonly defaultOption: VendorKind;
  };
  readonly windowTitle: string;
  readonly affiliationDateLabel: string;
  readonly typeLabel: string;
  readonly typeValue: string;
  readonly sexOptions: string[];
  readonly acceptLabel: string;
  readonly cancelLabel: string;
  readonly saveLabel: string;
  readonly backLabel: string;
  readonly backRoute: string;
  readonly forms: Record<VendorKind, VendorField[]>;
  readonly messages: Record<'saved' | 'required' | 'email' | 'chooseAgency' | 'duplicate' | 'error', string>;
}
