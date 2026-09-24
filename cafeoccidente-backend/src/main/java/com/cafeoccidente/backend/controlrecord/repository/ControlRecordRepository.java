package com.cafeoccidente.backend.controlrecord.repository;

import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ControlRecordRepository extends JpaRepository<ControlRecord, Long> {
    Optional<ControlRecord> findByAgencyIdAndActiveTrue(Long agencyId);

    boolean existsByAgencyIdAndActiveTrue(Long agencyId);

    /** Agencias habilitadas para comprar hoy (ver fan-out de anuncios en AnnouncementServiceImpl). */
    List<ControlRecord> findByActiveTrue();
}
