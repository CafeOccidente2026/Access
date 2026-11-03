package com.cafeoccidente.backend.purchases.greencoffee.repository;

import com.cafeoccidente.backend.purchases.greencoffee.entity.GreenCoffeePurchase;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GreenCoffeePurchaseRepository extends JpaRepository<GreenCoffeePurchase, Long> {
}
