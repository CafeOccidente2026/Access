package com.cafeoccidente.backend.inventory.repository;

import com.cafeoccidente.backend.inventory.entity.Driver;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverRepository extends JpaRepository<Driver, Long> {
}
