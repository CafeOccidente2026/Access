package com.cafeoccidente.backend.purchases.future.repository;

import com.cafeoccidente.backend.purchases.future.entity.AnnouncementQuota;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnnouncementQuotaRepository extends JpaRepository<AnnouncementQuota, Long> {

    Optional<AnnouncementQuota> findByAgencyAnnouncementNumberId(Long agencyAnnouncementNumberId);

    List<AnnouncementQuota> findByAgencyAnnouncementNumberAgencyId(Long agencyId);
}
