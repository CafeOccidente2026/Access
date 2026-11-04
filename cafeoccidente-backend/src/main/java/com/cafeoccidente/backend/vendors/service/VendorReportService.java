package com.cafeoccidente.backend.vendors.service;

import com.cafeoccidente.backend.vendors.dto.BeneficiaryRow;
import com.cafeoccidente.backend.vendors.dto.NessQuotaBalanceRow;
import com.cafeoccidente.backend.vendors.dto.NessQuotaPage;
import java.time.LocalDate;
import java.util.List;

/** Informes y consultas de "MENUS VENDEDORES". agencyId ya resuelto por rol: null = todas (solo ADMIN). */
public interface VendorReportService {

    /** "Beneficiario" (from/to null) o "Beneficiario resumen" (ambas fechas, inclusivas). */
    List<BeneficiaryRow> beneficiary(Long agencyId, String idNumberPrefix, LocalDate from, LocalDate to);

    /** "Consulta Cupos Ness": tabla NESS, solo lectura, paginada. */
    NessQuotaPage nessQuotas(String idNumberPrefix, int page);

    /** "Consulta Saldos Cupos Ness" (CalCuposSaldoNess -> NESSYRAINSALDO). */
    List<NessQuotaBalanceRow> nessQuotaBalances(Long agencyId);
}
