package com.cafeoccidente.backend.controlrecord.repository;

import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ControlRecordRepository extends JpaRepository<ControlRecord, Long> {
    Optional<ControlRecord> findByActiveTrue();
}
