package com.cafeoccidente.backend.purchases.shared.service.impl;

import com.cafeoccidente.backend.purchases.drycoffee.repository.DryCoffeePurchaseRepository;
import com.cafeoccidente.backend.purchases.future.repository.FertiFuturoPurchaseRepository;
import com.cafeoccidente.backend.purchases.greencoffee.repository.GreenCoffeePurchaseRepository;
import com.cafeoccidente.backend.purchases.husk.repository.HuskPurchaseRepository;
import com.cafeoccidente.backend.purchases.othercoffee.repository.OtherCoffeePurchaseRepository;
import com.cafeoccidente.backend.purchases.shared.service.PurchaseInvoiceNumberService;
import java.util.Arrays;
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

    /** El mayor numero usado en los 5 modulos de compra; null si la agencia aun no facturo nada.
     *  Bucle explicito (en vez de Stream + Comparator) para que el analisis de null de JDT lo
     *  entienda sin @SuppressWarnings: mismo resultado. */
    @Override
    public Integer findMaxUsed(Long agencyId) {
        Integer max = null;
        for (Integer used : Arrays.asList(
                dryCoffeePurchaseRepository.findMaxInvoiceNumber(agencyId),
                greenCoffeePurchaseRepository.findMaxInvoiceNumber(agencyId),
                huskPurchaseRepository.findMaxInvoiceNumber(agencyId),
                otherCoffeePurchaseRepository.findMaxInvoiceNumber(agencyId),
                fertiFuturoPurchaseRepository.findMaxInvoiceNumber(agencyId))) {
            if (used != null && (max == null || used > max)) {
                max = used;
            }
        }
        return max;
    }
}
