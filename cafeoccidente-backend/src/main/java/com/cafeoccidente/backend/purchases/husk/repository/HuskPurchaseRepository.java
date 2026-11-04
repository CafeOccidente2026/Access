package com.cafeoccidente.backend.purchases.husk.repository;

import com.cafeoccidente.backend.purchases.husk.entity.HuskPurchase;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HuskPurchaseRepository extends JpaRepository<HuskPurchase, Long> {
}
