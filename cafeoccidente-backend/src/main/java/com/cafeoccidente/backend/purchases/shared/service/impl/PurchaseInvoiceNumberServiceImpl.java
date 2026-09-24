package com.cafeoccidente.backend.purchases.shared.service.impl;

import com.cafeoccidente.backend.purchases.drycoffee.repository.DryCoffeePurchaseRepository;
import com.cafeoccidente.backend.purchases.future.repository.FertiFuturoPurchaseRepository;
import com.cafeoccidente.backend.purchases.greencoffee.repository.GreenCoffeePurchaseRepository;
import com.cafeoccidente.backend.purchases.husk.repository.HuskPurchaseRepository;
import com.cafeoccidente.backend.purchases.othercoffee.repository.OtherCoffeePurchaseRepository;
import com.cafeoccidente.backend.purchases.shared.service.PurchaseInvoiceNumberService;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class PurchaseInvoiceNumberServiceImpl implements PurchaseInvoiceNumberService {

    private final DryCoffeePurchaseRepository dryCoffeePurchaseRepository;
    private final GreenCoffeePurchaseRepository greenCoffeePurchaseRepository;
    private final HuskPurchaseRepository huskPurchaseRepository;
    private final OtherCoffeePurchaseRepository otherCoffeePurchaseRepository;
    private final FertiFuturoPurchaseRepository fertiFuturoPurchaseRepository;

    public PurchaseInvoiceNumberServiceImpl(
            DryCoffeePurchaseRepository dryCoffeePurchaseRepository,
            GreenCoffeePurchaseRepository greenCoffeePurchaseRepository,
            HuskPurchaseRepository huskPurchaseRepository,
            OtherCoffeePurchaseRepository otherCoffeePurchaseRepository,
            FertiFuturoPurchaseRepository fertiFuturoPurchaseRepository) {
        this.dryCoffeePurchaseRepository = dryCoffeePurchaseRepository;
        this.greenCoffeePurchaseRepository = greenCoffeePurchaseRepository;
        this.huskPurchaseRepository = huskPurchaseRepository;
        this.otherCoffeePurchaseRepository = otherCoffeePurchaseRepository;
        this.fertiFuturoPurchaseRepository = fertiFuturoPurchaseRepository;
    }

    // El analisis de null de JDT no logra propagar el filter(Objects::nonNull) de abajo al tipo
    // del Stream, y marca el Comparator de mas() como advertencia aunque no puede llegar null ahi.
    @SuppressWarnings("null")
    @Override
    public Integer findMaxUsed(Long agencyId) {
        return Stream.of(
                        dryCoffeePurchaseRepository.findMaxInvoiceNumber(agencyId),
                        greenCoffeePurchaseRepository.findMaxInvoiceNumber(agencyId),
                        huskPurchaseRepository.findMaxInvoiceNumber(agencyId),
                        otherCoffeePurchaseRepository.findMaxInvoiceNumber(agencyId),
                        fertiFuturoPurchaseRepository.findMaxInvoiceNumber(agencyId))
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }
}
