package com.cafeoccidente.backend.inventory.repository;

import com.cafeoccidente.backend.inventory.entity.Remission;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RemissionRepository extends JpaRepository<Remission, Long> {

    @Query("SELECT MAX(r.remissionNumber) FROM Remission r WHERE r.agency.id = :agencyId")
    Integer findMaxRemissionNumber(@Param("agencyId") Long agencyId);

    List<Remission> findByAgencyIdOrderByRemissionNumberDesc(Long agencyId);

    Optional<Remission> findByAgencyIdAndRemissionNumber(Long agencyId, Integer remissionNumber);

    Optional<Remission> findByAgencyIdAndDisplayNumberIgnoreCase(Long agencyId, String displayNumber);

    /** Anular Remision: bloqueada hasta el fin de la transaccion, asi dos anulaciones a la vez no se cruzan. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Remission r WHERE r.id = :id")
    Optional<Remission> findForUpdate(@Param("id") Long id);
}
