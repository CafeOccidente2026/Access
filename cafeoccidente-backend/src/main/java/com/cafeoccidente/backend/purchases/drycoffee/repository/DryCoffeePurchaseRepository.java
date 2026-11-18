package com.cafeoccidente.backend.purchases.drycoffee.repository;

import com.cafeoccidente.backend.purchases.drycoffee.entity.DryCoffeePurchase;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DryCoffeePurchaseRepository extends JpaRepository<DryCoffeePurchase, Long> {



    /**
     * "Entregados" del cupo: kilos netos de las compras hechas por Facturar Anuncios con Cupos con ese anuncio (regla
     * del area, 18-nov: las compras normales con el mismo anuncio no cuentan). Las anuladas ya estan en 0.
     * announcementNumber es el numero de display ("PREFIJO-numero", ver AnnouncementServiceImpl.
     * toResponse), no el id interno de AgencyAnnouncementNumber.
     */
    @Query("""
            SELECT COALESCE(SUM(p.netKg), 0) FROM DryCoffeePurchase p
            WHERE p.agency.id = :agencyId AND p.announcementNumber = :displayAnnouncementNumber AND p.quotaPurchase = true
            """)
    BigDecimal sumQuotaNetKgByAgencyAndAnnouncementNumber(
            @Param("agencyId") Long agencyId, @Param("displayAnnouncementNumber") String displayAnnouncementNumber);

    /** Lo mismo para todos los anuncios de la agencia en una sola consulta: filas [numero de display, kilos]. */
    @Query("""
            SELECT p.announcementNumber, COALESCE(SUM(p.netKg), 0) FROM DryCoffeePurchase p
            WHERE p.agency.id = :agencyId AND p.quotaPurchase = true
            GROUP BY p.announcementNumber
            """)
    List<Object[]> sumQuotaNetKgByAnnouncementNumber(@Param("agencyId") Long agencyId);

    /** Kilos netos entregados contra una compra anunciada, sin las anuladas (Restaurar Compras Anunciadas). */
    @Query(value = """
            SELECT COALESCE(SUM(kilos_netos), 0) FROM compras_cafe_seco
            WHERE compra_anunciada_id = :futurePurchaseId AND estado <> 'ANULADA'
            """, nativeQuery = true)
    BigDecimal sumNetKgOfValidByFuturePurchase(@Param("futurePurchaseId") Long futurePurchaseId);
}
