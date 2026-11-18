package com.cafeoccidente.backend.purchases.future.repository;

import com.cafeoccidente.backend.purchases.future.dto.PendingAnnouncementRow;
import com.cafeoccidente.backend.purchases.future.entity.FuturePurchase;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Instant;
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

    /**
     * Anunciadas de Hoy, sumadas por anuncio: con {@code withBalance} las que tienen saldo, de cualquier fecha (P2);
     * sin el, las registradas desde {@code from} hasta antes de {@code to}, con saldo o sin el. R5 (17-nov): por la
     * fecha en que se registro la compra a futuro, no por la del anuncio (Access filtraba por Fecha_Anuncio).
     */
    @Query("""
            SELECT new com.cafeoccidente.backend.purchases.future.dto.PendingAnnouncementRow(
                f.announcementNumber, f.announcementDate, f.specialType, f.healthyUnitPrice,
                SUM(f.announcedKg), SUM(f.remainingKg), COUNT(f))
            FROM FuturePurchase f
            WHERE f.agency.id = :agencyId
              AND ((:withBalance = true AND f.remainingKg > 0)
                OR (:withBalance = false AND f.createdAt >= :from AND f.createdAt < :to))
            GROUP BY f.announcementNumber, f.announcementDate, f.specialType, f.healthyUnitPrice
            ORDER BY f.announcementDate DESC, f.announcementNumber DESC
            """)
    List<PendingAnnouncementRow> pendingByAnnouncement(@Param("agencyId") Long agencyId,
            @Param("withBalance") boolean withBalance, @Param("from") Instant from, @Param("to") Instant to);

    /** Hallazgo #14: la ultima compra a futuro de la cedula en la agencia. */
    Optional<FuturePurchase> findFirstByAgencyIdAndIdNumberOrderByIdDesc(Long agencyId, String idNumber);

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
