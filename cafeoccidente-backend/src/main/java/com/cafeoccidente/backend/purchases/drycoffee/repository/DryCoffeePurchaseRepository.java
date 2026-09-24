package com.cafeoccidente.backend.purchases.drycoffee.repository;

import com.cafeoccidente.backend.purchases.drycoffee.entity.DryCoffeePurchase;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DryCoffeePurchaseRepository extends JpaRepository<DryCoffeePurchase, Long> {

    /**
     * Suma el Vr_Bruto y la Retefuente de todas las compras ya registradas para una cedula dentro
     * del rango de fechas dado. Se llama antes de guardar la compra en curso, asi que esta
     * naturalmente excluida (todavia no tiene fila). COALESCE devuelve 0 cuando no hay compras.
     */
    @Query("""
            SELECT new com.cafeoccidente.backend.purchases.drycoffee.repository.MonthlyGrowerTotals(
                COALESCE(SUM(p.grossValue), 0),
                COALESCE(SUM(p.withholding), 0))
            FROM DryCoffeePurchase p
            WHERE p.idNumber = :idNumber
              AND p.purchaseDate BETWEEN :monthStart AND :monthEnd
            """)
    MonthlyGrowerTotals sumMonthlyTotalsByIdNumber(
            @Param("idNumber") String idNumber,
            @Param("monthStart") LocalDate monthStart,
            @Param("monthEnd") LocalDate monthEnd);

    /**
     * Ultima factura ya usada por esta agencia, para reservar la siguiente (paso "Fondo"). Null si
     * no hay ninguna. Debe filtrar por agencia: cada agencia tiene su propio rango de resolucion
     * DIAN (ver ControlRecord), y mezclar el maximo entre agencias agota el rango de una con
     * facturas de otra.
     */
    @Query("SELECT MAX(p.invoiceNumber) FROM DryCoffeePurchase p WHERE p.agency.id = :agencyId")
    Integer findMaxInvoiceNumber(@Param("agencyId") Long agencyId);

    /**
     * "Entregados" (Asignar Cupo): kilos netos ya comprados contra un anuncio puntual. La
     * pantalla "Facturar Compras Anunciadas"/"Compras Cupos" reusa este mismo backend (ver
     * QuotaPurchaseFormComponent/QuotaBillingFormComponent), asi que sus compras ya quedan aca.
     * announcementNumber es el numero de display ("PREFIJO-numero", ver AnnouncementServiceImpl.
     * toResponse), no el id interno de AgencyAnnouncementNumber.
     */
    @Query("""
            SELECT COALESCE(SUM(p.netKg), 0) FROM DryCoffeePurchase p
            WHERE p.agency.id = :agencyId AND p.announcementNumber = :displayAnnouncementNumber
            """)
    BigDecimal sumNetKgByAgencyAndAnnouncementNumber(
            @Param("agencyId") Long agencyId, @Param("displayAnnouncementNumber") String displayAnnouncementNumber);
}
