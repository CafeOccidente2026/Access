package com.cafeoccidente.backend.supplies.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cafeoccidente.backend.purchases.shared.entity.Agency;
import com.cafeoccidente.backend.purchases.shared.entity.Fund;
import com.cafeoccidente.backend.supplies.dto.SuppliesReportRow;
import com.cafeoccidente.backend.supplies.entity.CashEntry;
import com.cafeoccidente.backend.supplies.entity.CheckRelationMark;
import com.cafeoccidente.backend.supplies.entity.LedgerEntry;
import com.cafeoccidente.backend.supplies.entity.SupplyEntry;
import com.cafeoccidente.backend.supplies.repository.CashEntryRepository;
import com.cafeoccidente.backend.supplies.repository.CheckRelationMarkRepository;
import com.cafeoccidente.backend.supplies.repository.GrowerName;
import com.cafeoccidente.backend.supplies.repository.PackagingEntryRepository;
import com.cafeoccidente.backend.supplies.repository.PettyCashEntryRepository;
import com.cafeoccidente.backend.supplies.repository.PurchasePayment;
import com.cafeoccidente.backend.supplies.repository.SuppliesReadRepository;
import com.cafeoccidente.backend.supplies.repository.SupplyEntryRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Caja y Suministros armados al leer, como quedaban en Access tras sus consultas de copia. */
class SuppliesReportServiceImplTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 30);
    private static final LocalDate DAY1 = LocalDate.of(2026, 9, 1);
    private static final LocalDate DAY2 = LocalDate.of(2026, 9, 2);

    private final CashEntryRepository cash = mock(CashEntryRepository.class);
    private final SupplyEntryRepository supplies = mock(SupplyEntryRepository.class);
    private final CheckRelationMarkRepository marks = mock(CheckRelationMarkRepository.class);
    private final SuppliesReadRepository reads = mock(SuppliesReadRepository.class);
    private final SuppliesReportServiceImpl service = new SuppliesReportServiceImpl(cash,
            mock(PettyCashEntryRepository.class), supplies, mock(PackagingEntryRepository.class), marks, reads);

    private static <T extends LedgerEntry> T entry(T e, long id, LocalDate date, String inflow, String outflow,
            String method, Integer check) {
        Agency agency = new Agency();
        agency.setName("EL TAMBO");
        Fund fund = new Fund();
        fund.setCode("RP");
        e.setId(id);
        e.setAgency(agency);
        e.setFund(fund);
        e.setTransactionId("T" + id);
        e.setEntryDate(date);
        e.setIdNumber("87304051");
        e.setInflow(new BigDecimal(inflow));
        e.setOutflow(new BigDecimal(outflow));
        e.setPaymentMethod(method);
        e.setCheckNumber(check);
        e.setCreatedAt(Instant.ofEpochSecond(id));
        return e;
    }

    private static PurchasePayment purchase(long id, LocalDate date, String net, String method, Integer check) {
        return new PurchasePayment("DRY", id, 43000 + (int) id, 4L, "EL TAMBO", "RP", date, "87304051",
                new BigDecimal(net), method, check, Instant.ofEpochSecond(id));
    }

    @BeforeEach
    void setUp() {
        // Caja suministros con cheque (Entradas 1000), Ajustes Caja (Salidas 100)
        when(cash.findInPeriod(any(), any(), any())).thenReturn(List.of(
                entry(new CashEntry(), 1, DAY1, "1000", "0", "CHEQUE", 5320),
                entry(new CashEntry(), 2, DAY2, "0", "100", "EFECTIVO", null)));
        // Ingresar Suministros (Valor 5000) y Cheque Girado (300, cheque 5400)
        when(supplies.findInPeriod(any(), any(), any())).thenReturn(List.of(
                entry(new SupplyEntry(), 3, DAY1, "5000", "0", null, null),
                entry(new SupplyEntry(), 4, DAY2, "0", "300", "CHEQUE", 5400)));
        // Compra en efectivo (200) y con cheque (400, cheque 5377)
        when(reads.purchasePayments(any(), any(), any())).thenReturn(List.of(
                purchase(5, DAY1, "200", "EFECTIVO", null),
                purchase(6, DAY2, "400", "CHEQUE", 5377)));
        when(reads.growerNames(anyCollection())).thenReturn(List.of(new GrowerName("87304051", "WILSON TOBIAS", "LEGARDA")));
    }

    @Test
    void cashMovementIsCajaInCashWithRunningBalance() {
        List<SuppliesReportRow> rows = service.cashMovement(null, "RP", TODAY);

        // Entrada con cheque queda EFECTIVO (CAMBIO A EFECTIVO); cheque girado y compra con cheque no.
        assertThat(rows).extracting(SuppliesReportRow::transactionId).containsExactly("T1", "43005", "T2");
        assertThat(rows).extracting(SuppliesReportRow::balance)
                .containsExactly(new BigDecimal("1000"), new BigDecimal("800"), new BigDecimal("700"));
    }

    @Test
    void suppliesBookAddsCheckEntriesAndCheckPurchasesAsExpenses() {
        List<SuppliesReportRow> rows = service.supplies(null, "RP", TODAY);

        assertThat(rows).extracting(SuppliesReportRow::transactionId).containsExactly("T1", "T3", "T4", "43006");
        assertThat(rows.get(0).outflow()).isEqualByComparingTo("1000"); // Efectivo con cheques
        assertThat(rows.get(3).balance()).isEqualByComparingTo("3300"); // 5000 - 1000 - 300 - 400
    }

    @Test
    @SuppressWarnings("unchecked")
    void checkRelationShowsUnmarkedChecksAndMarksThem() {
        when(marks.findAllById(anyIterable())).thenReturn(List.of(new CheckRelationMark("CASH", 1L)));

        List<SuppliesReportRow> rows = service.checkRelation(null, null, null, TODAY);

        // Cheque 5320 ya relacionado; quedan el cheque girado (Por Comp) y la compra con cheque.
        assertThat(rows).extracting(SuppliesReportRow::checkNumber).containsExactly(5400, 5377);
        assertThat(rows.get(0).firstNames()).isEqualTo("WILSON TOBIAS");
        ArgumentCaptor<List<CheckRelationMark>> saved = ArgumentCaptor.forClass(List.class);
        verify(marks).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(CheckRelationMark::getSourceId).containsExactly(4L, 6L);
    }

    @Test
    void paymentMethodsFiltersByMethodAndUsesFullNameAsDetail() {
        List<SuppliesReportRow> rows = service.paymentMethods(null, DAY1, DAY2, "CHEQUE");

        assertThat(rows).extracting(SuppliesReportRow::transactionId).containsExactly("43006", "T4");
        assertThat(rows.get(0).detail()).isEqualTo("WILSON TOBIAS LEGARDA");
        verify(reads).purchasePayments(null, DAY1, DAY2.plusDays(1));
    }

    @Test
    void purchasesInZeroNeverReachCajaNorSuministros() {
        // ActualizaCaja/Ch y ActualizaSuministros: WHERE FPef > 0 / FPch > 0 (facturas 43296, 43297... en $0).
        when(reads.purchasePayments(any(), any(), any())).thenReturn(List.of(
                purchase(5, DAY1, "200", "EFECTIVO", null),
                purchase(7, DAY1, "0", "EFECTIVO", null),
                purchase(8, DAY2, "0.00", "CHEQUE", 5378)));

        assertThat(service.cashMovement(null, "RP", TODAY)).extracting(SuppliesReportRow::transactionId)
                .containsExactly("T1", "43005", "T2");
        assertThat(service.paymentMethods(null, DAY1, DAY2, "*")).extracting(SuppliesReportRow::transactionId)
                .doesNotContain("43007", "43008");
        assertThat(service.supplies(null, "RP", TODAY)).extracting(SuppliesReportRow::transactionId)
                .doesNotContain("43008");
    }
}
