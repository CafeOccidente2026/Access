package com.cafeoccidente.backend.purchases.future.repository;

import com.cafeoccidente.backend.purchases.future.dto.PendingAnnouncementRow;
import com.cafeoccidente.backend.purchases.future.entity.FuturePurchase;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FuturePurchaseRepository extends JpaRepository<FuturePurchase, Long> {

    /** Ventana de Facturar Compras Anunciadas (RN-21.5.2): las de la cedula con saldo, de la agencia. */
    List<FuturePurchase> findByAgencyIdAndIdNumberAndRemainingKgGreaterThanOrderByIdAsc(
            Long agencyId, String idNumber, BigDecimal remainingKg);

    /** Anunciadas Hoy (HU-31, P2): las de la agencia con saldo, de cualquier fecha, sumadas por anuncio. */
    @Query("""
            SELECT new com.cafeoccidente.backend.purchases.future.dto.PendingAnnouncementRow(
                f.announcementNumber, f.announcementDate, f.specialType, f.healthyUnitPrice,
                SUM(f.announcedKg), SUM(f.remainingKg), COUNT(f))
            FROM FuturePurchase f
            WHERE f.agency.id = :agencyId AND f.remainingKg > 0
            GROUP BY f.announcementNumber, f.announcementDate, f.specialType, f.healthyUnitPrice
            ORDER BY f.announcementDate DESC, f.announcementNumber DESC
            """)
    List<PendingAnnouncementRow> pendingByAnnouncement(@Param("agencyId") Long agencyId);

    /** Restaurar Compras Anunciadas: todas las de la cedula en la agencia, con saldo o sin el. */
    List<FuturePurchase> findByAgencyIdAndIdNumberOrderByIdAsc(Long agencyId, String idNumber);

    /** Las mismas, bloqueadas mientras se recalcula su saldo. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM FuturePurchase f WHERE f.agency.id = :agencyId AND f.idNumber = :idNumber ORDER BY f.id")
    List<FuturePurchase> lockByAgencyIdAndIdNumber(@Param("agencyId") Long agencyId, @Param("idNumber") String idNumber);

    /** SELECT ... FOR UPDATE: dos facturas contra la misma compra anunciada se turnan para gastar el saldo. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM FuturePurchase f WHERE f.id = :id")
    Optional<FuturePurchase> lockById(@Param("id") Long id);
}
