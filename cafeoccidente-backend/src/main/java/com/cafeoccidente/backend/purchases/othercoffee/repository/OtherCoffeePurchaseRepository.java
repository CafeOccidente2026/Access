package com.cafeoccidente.backend.purchases.othercoffee.repository;

import com.cafeoccidente.backend.purchases.othercoffee.entity.OtherCoffeePurchase;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OtherCoffeePurchaseRepository extends JpaRepository<OtherCoffeePurchase, Long> {
}
