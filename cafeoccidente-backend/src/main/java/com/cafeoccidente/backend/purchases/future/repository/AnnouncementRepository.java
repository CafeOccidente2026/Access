package com.cafeoccidente.backend.purchases.future.repository;

import com.cafeoccidente.backend.purchases.future.entity.Announcement;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {
    Optional<Announcement> findFirstByAgencyIdAndFundIdAndActiveTrueOrderByAnnouncementDateDesc(
            Long agencyId, Long fundId);
}
