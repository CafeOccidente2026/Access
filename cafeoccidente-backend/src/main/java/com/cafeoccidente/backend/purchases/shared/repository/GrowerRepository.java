package com.cafeoccidente.backend.purchases.shared.repository;

import com.cafeoccidente.backend.purchases.shared.entity.Grower;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GrowerRepository extends JpaRepository<Grower, Long> {

    Optional<Grower> findByIdNumber(String idNumber);
}
