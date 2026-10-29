package com.cafeoccidente.backend.purchases.drycoffee.repository;

import com.cafeoccidente.backend.purchases.drycoffee.entity.DryCoffeePurchase;
import java.math.BigDecimal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DryCoffeePurchaseRepository extends JpaRepository<DryCoffeePurchase, Long> {



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
