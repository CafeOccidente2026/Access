package com.cafeoccidente.backend.inventory.repository;

import com.cafeoccidente.backend.inventory.entity.Remission;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RemissionRepository extends JpaRepository<Remission, Long> {
}
