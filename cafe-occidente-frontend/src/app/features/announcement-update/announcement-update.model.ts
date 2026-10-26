export interface AnnouncementUpdateContent {
  readonly windowTitle: string;
  readonly sectionTitle: string;
  readonly fundLabel: string;
  readonly basePriceLoadLabel: string;
  readonly saveButton: string;
  readonly updateButton: string;
  readonly listTitle: string;
  readonly columns: Record<'specialType' | 'basePriceLoad' | 'specialSurcharge' | 'defectiveUnitPrice', string>;
  readonly rowSaveButton: string;
  readonly successMessage: string;
  readonly errorMessage: string;
}

/** Una fila editable del listado "Actualizar": los montos como se ven en el input (con puntos de miles). */
export interface TypeRow {
  readonly specialType: string;
  basePriceLoad: string;
  specialSurcharge: string;
  defectiveUnitPrice: string;
  /** El precio vigente, para saber si el usuario lo cambio (RN-08.6). */
  readonly originalBasePriceLoad: string;
}
