package com.cafeoccidente.backend.inventory.repository;

import com.cafeoccidente.backend.inventory.entity.RemissionLine;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RemissionLineRepository extends JpaRepository<RemissionLine, Long> {

    List<RemissionLine> findByRemissionId(Long remissionId);
}
