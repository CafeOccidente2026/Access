package com.cafeoccidente.backend.purchases.future.repository;

import com.cafeoccidente.backend.purchases.future.entity.AgencyAnnouncementNumber;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgencyAnnouncementNumberRepository extends JpaRepository<AgencyAnnouncementNumber, Long> {

    /** Ultimo numero ya asignado a la agencia (para reservar el siguiente al hacer fan-out). */
    @Query(
            "SELECT MAX(n.announcementNumber) FROM AgencyAnnouncementNumber n WHERE n.agency.id = :agencyId")
    Integer findMaxNumber(@Param("agencyId") Long agencyId);

    /** El anuncio vigente (mas reciente) para esa agencia/fondo/especial - findLatest() de compras. */
    @Query("""
            SELECT n FROM AgencyAnnouncementNumber n
            WHERE n.agency.id = :agencyId
              AND n.masterAnnouncement.fund.id = :fundId
              AND n.masterAnnouncement.specialType = :specialType
              AND n.masterAnnouncement.active = true
            ORDER BY n.id DESC
            """)
    List<AgencyAnnouncementNumber> findLatestCandidates(
            @Param("agencyId") Long agencyId, @Param("fundId") Long fundId, @Param("specialType") String specialType);

    default Optional<AgencyAnnouncementNumber> findLatest(Long agencyId, Long fundId, String specialType) {
        return findLatestCandidates(agencyId, fundId, specialType).stream().findFirst();
    }

    List<AgencyAnnouncementNumber> findByAgencyIdOrderByAssignedAtDescIdDesc(Long agencyId);
}
