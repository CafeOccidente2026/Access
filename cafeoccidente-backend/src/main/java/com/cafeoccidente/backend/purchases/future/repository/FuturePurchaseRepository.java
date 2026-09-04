package com.cafeoccidente.backend.purchases.future.repository;

import com.cafeoccidente.backend.purchases.future.entity.FuturePurchase;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FuturePurchaseRepository extends JpaRepository<FuturePurchase, Long> {
}
