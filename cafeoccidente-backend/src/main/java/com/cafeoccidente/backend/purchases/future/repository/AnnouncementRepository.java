package com.cafeoccidente.backend.purchases.future.repository;

import com.cafeoccidente.backend.purchases.future.entity.Announcement;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    /** El anuncio maestro vigente de un tipo de cafe en un fondo: el mas reciente (comun a todas las agencias). */
    Optional<Announcement> findFirstByFundIdAndSpecialTypeOrderByIdDesc(Long fundId, String specialType);
}
