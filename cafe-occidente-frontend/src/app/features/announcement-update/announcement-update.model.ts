export interface AnnouncementUpdateContent {
  readonly windowTitle: string;
  readonly sectionTitle: string;
  readonly basePriceLoadLabel: string;
  readonly defectiveUnitPriceLabel: string;
  readonly specialSurchargeLabel: string;
  readonly specialTypeLabel: string;
  readonly updateButton: string;
  readonly successMessage: string;
  readonly errorMessage: string;
  readonly specialOptions: readonly string[];
}
