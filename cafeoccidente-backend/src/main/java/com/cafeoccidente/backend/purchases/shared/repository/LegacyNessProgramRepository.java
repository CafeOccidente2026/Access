package com.cafeoccidente.backend.purchases.shared.repository;

import com.cafeoccidente.backend.purchases.shared.entity.LegacyNessProgram;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LegacyNessProgramRepository extends JpaRepository<LegacyNessProgram, Long> {

    /** Cedula en staging_legacy_ness trae sufijo ".0" (artefacto del CSV); se normaliza en SQL para cruzar. */
    @Query(
            value = "SELECT * FROM staging_legacy_ness WHERE regexp_replace(cedula, '\\.0+$', '') = :idNumber",
            nativeQuery = true)
    List<LegacyNessProgram> findByNormalizedCedula(@Param("idNumber") String idNumber);
}
