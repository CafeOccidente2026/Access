package com.cafeoccidente.backend.purchases.future.repository;

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

    /** SELECT ... FOR UPDATE: dos facturas contra la misma compra anunciada se turnan para gastar el saldo. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM FuturePurchase f WHERE f.id = :id")
    Optional<FuturePurchase> lockById(@Param("id") Long id);
}
