package com.cafeoccidente.backend.purchases.shared.repository;

import com.cafeoccidente.backend.purchases.shared.entity.Grower;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GrowerRepository extends JpaRepository<Grower, Long> {

    Optional<Grower> findByIdNumber(String idNumber);

    /** Solo conductores (tienen Emp. Transp. o Vehiculo): en Access no hay tabla/consulta aparte de
     *  conductores, es el mismo Asociados - criterio provisional acordado. */
    @Query("SELECT g FROM Grower g WHERE g.idNumber LIKE CONCAT(:prefix, '%')"
            + " AND ((g.transportCompany IS NOT NULL AND TRIM(g.transportCompany) <> '')"
            + " OR (g.vehiclePlate IS NOT NULL AND TRIM(g.vehiclePlate) <> '')) ORDER BY g.idNumber")
    List<Grower> findConductorsByIdNumberPrefix(@Param("prefix") String prefix, Pageable pageable);
}
