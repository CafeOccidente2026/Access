package com.cafeoccidente.backend.purchases.drycoffee.repository;

import com.cafeoccidente.backend.purchases.drycoffee.entity.DryCoffeePurchase;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DryCoffeePurchaseRepository extends JpaRepository<DryCoffeePurchase, Long> {
}
