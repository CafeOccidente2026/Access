package com.cafeoccidente.backend.purchases.future.repository;

import com.cafeoccidente.backend.purchases.future.entity.Announcement;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    Optional<Announcement> findFirstByAgencyIdAndFundIdAndSpecialTypeAndActiveTrueOrderByAnnouncementDateDescIdDesc(
            Long agencyId, Long fundId, String specialType);

    List<Announcement> findByAgencyIdOrderByAnnouncementDateDescIdDesc(Long agencyId);

    /** Ultimo numero de anuncio ya usado por la agencia, para reservar el siguiente (MAX+1 global,
     * sin filtrar por fondo/especial: ver macro "Para actualizar No anuncios CORRF"). */
    @Query(
            value = "SELECT MAX(CAST(announcement_number AS integer)) FROM announcement WHERE agency_id = :agencyId",
            nativeQuery = true)
    Integer findMaxAnnouncementNumber(@Param("agencyId") Long agencyId);
}
