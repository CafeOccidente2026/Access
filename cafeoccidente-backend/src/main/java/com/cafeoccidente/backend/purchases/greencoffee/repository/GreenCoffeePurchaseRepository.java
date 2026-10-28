package com.cafeoccidente.backend.purchases.greencoffee.repository;

import com.cafeoccidente.backend.purchases.greencoffee.entity.GreenCoffeePurchase;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GreenCoffeePurchaseRepository extends JpaRepository<GreenCoffeePurchase, Long> {


}
