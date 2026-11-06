package com.cafeoccidente.backend.purchases.future.repository;

import com.cafeoccidente.backend.purchases.future.entity.AnnouncementQuota;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnnouncementQuotaRepository extends JpaRepository<AnnouncementQuota, Long> {

    Optional<AnnouncementQuota> findByAgencyAnnouncementNumberId(Long agencyAnnouncementNumberId);

    List<AnnouncementQuota> findByAgencyAnnouncementNumberAgencyId(Long agencyId);

    /** SELECT ... FOR UPDATE: dos compras con el mismo anuncio se turnan para leer y gastar el saldo. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT q FROM AnnouncementQuota q
            WHERE q.agencyAnnouncementNumber.agency.id = :agencyId
              AND q.agencyAnnouncementNumber.announcementNumber = :announcementNumber
            """)
    Optional<AnnouncementQuota> lockByAgencyAndNumber(
            @Param("agencyId") Long agencyId, @Param("announcementNumber") Integer announcementNumber);
}
