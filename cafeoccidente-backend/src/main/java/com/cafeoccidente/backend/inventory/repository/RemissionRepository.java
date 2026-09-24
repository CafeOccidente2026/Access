package com.cafeoccidente.backend.inventory.repository;

import com.cafeoccidente.backend.inventory.entity.Remission;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RemissionRepository extends JpaRepository<Remission, Long> {

    @Query("SELECT MAX(r.remissionNumber) FROM Remission r WHERE r.agency.id = :agencyId")
    Integer findMaxRemissionNumber(@Param("agencyId") Long agencyId);

    List<Remission> findByAgencyIdOrderByRemissionNumberDesc(Long agencyId);

    List<Remission> findByAgencyIdAndExportedFalseOrderByRemissionNumberAsc(Long agencyId);

    Optional<Remission> findByAgencyIdAndRemissionNumber(Long agencyId, Integer remissionNumber);
}
