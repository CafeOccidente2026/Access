package com.cafeoccidente.backend.purchases.shared.repository;

import com.cafeoccidente.backend.purchases.shared.entity.Municipality;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MunicipalityRepository extends JpaRepository<Municipality, Long> {

    List<Municipality> findByActiveTrue();
}
