package com.cafeoccidente.backend.purchases.shared.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cafeoccidente.backend.purchases.drycoffee.repository.DryCoffeePurchaseRepository;
import com.cafeoccidente.backend.purchases.future.repository.FertiFuturoPurchaseRepository;
import com.cafeoccidente.backend.purchases.greencoffee.repository.GreenCoffeePurchaseRepository;
import com.cafeoccidente.backend.purchases.husk.repository.HuskPurchaseRepository;
import com.cafeoccidente.backend.purchases.othercoffee.repository.OtherCoffeePurchaseRepository;
import org.junit.jupiter.api.Test;

/** Numero de factura mas alto usado por la agencia entre los 5 modulos de compra. */
class PurchaseInvoiceNumberServiceImplTest {

    private final DryCoffeePurchaseRepository dry = mock(DryCoffeePurchaseRepository.class);
    private final GreenCoffeePurchaseRepository green = mock(GreenCoffeePurchaseRepository.class);
    private final HuskPurchaseRepository husk = mock(HuskPurchaseRepository.class);
    private final OtherCoffeePurchaseRepository other = mock(OtherCoffeePurchaseRepository.class);
    private final FertiFuturoPurchaseRepository ferti = mock(FertiFuturoPurchaseRepository.class);
    private final PurchaseInvoiceNumberServiceImpl service =
            new PurchaseInvoiceNumberServiceImpl(dry, green, husk, other, ferti);

    @Test
    void returnsTheHighestAcrossModulesIgnoringModulesWithoutInvoices() {
        when(dry.findMaxInvoiceNumber(1L)).thenReturn(27871);
        when(green.findMaxInvoiceNumber(1L)).thenReturn(null);
        when(husk.findMaxInvoiceNumber(1L)).thenReturn(27900);
        when(other.findMaxInvoiceNumber(1L)).thenReturn(27880);
        when(ferti.findMaxInvoiceNumber(1L)).thenReturn(null);

        assertThat(service.findMaxUsed(1L)).isEqualTo(27900);
    }

    @Test
    void returnsNullWhenTheAgencyHasNoInvoicesYet() {
        // MAX() sin filas devuelve null en la base (Mockito, sin configurar, devolveria 0).
        when(dry.findMaxInvoiceNumber(1L)).thenReturn(null);
        when(green.findMaxInvoiceNumber(1L)).thenReturn(null);
        when(husk.findMaxInvoiceNumber(1L)).thenReturn(null);
        when(other.findMaxInvoiceNumber(1L)).thenReturn(null);
        when(ferti.findMaxInvoiceNumber(1L)).thenReturn(null);

        assertThat(service.findMaxUsed(1L)).isNull();
    }
}
