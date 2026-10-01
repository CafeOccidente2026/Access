package com.cafeoccidente.backend.supplies.service;

import com.cafeoccidente.backend.supplies.dto.SuppliesReportRow;
import java.time.LocalDate;
import java.util.List;

/**
 * Informes de "MENUS SUMINISTROS". agencyId ya resuelto por rol: null = todas las agencias (solo
 * ADMIN). Los de movimiento van en el orden del reporte y traen el Saldo acumulado.
 */
public interface SuppliesReportService {

    /** "MovimientoCaja": Caja con Fondo LIKE fund & "*" y Forma_de_Pago = EFECTIVO. */
    List<SuppliesReportRow> cashMovement(Long agencyId, String fundCode, LocalDate today);

    /** "Mov Caja Menor": CajaMenor con Forma_de_Pago = EFECTIVO. */
    List<SuppliesReportRow> pettyCashMovement(Long agencyId, LocalDate today);

    /** "SuministrosRP" / "SuministrosLF". */
    List<SuppliesReportRow> supplies(Long agencyId, String fundCode, LocalDate today);

    /** "Mov Empaque": Empaque con Tipo LIKE type & "*". */
    List<SuppliesReportRow> packaging(Long agencyId, String packagingType, LocalDate today);

    /**
     * "RELACION CHEQUES CAJA": cheques aun no relacionados, y al terminar los marca todos (consulta
     * "Rel cheq caja"). Con from/to es "Rel Cheques Especial": antes desmarca ese rango (UnCheckCheq).
     */
    List<SuppliesReportRow> checkRelation(Long agencyId, LocalDate from, LocalDate to, LocalDate today);

    /** "FormaPago" (Dialogo FormaPago): method null o "*" = todas. */
    List<SuppliesReportRow> paymentMethods(Long agencyId, LocalDate from, LocalDate to, String method);
}
