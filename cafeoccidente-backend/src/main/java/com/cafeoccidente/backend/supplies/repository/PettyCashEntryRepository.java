package com.cafeoccidente.backend.supplies.repository;

import com.cafeoccidente.backend.supplies.entity.PettyCashEntry;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PettyCashEntryRepository extends JpaRepository<PettyCashEntry, Long> {

    /** agencyId null = todas las agencias (ADMIN sin filtro). */
    @Query("SELECT e FROM PettyCashEntry e WHERE (:agencyId IS NULL OR e.agency.id = :agencyId)"
            + " AND e.entryDate BETWEEN :from AND :to")
    List<PettyCashEntry> findInPeriod(@Param("agencyId") Long agencyId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
