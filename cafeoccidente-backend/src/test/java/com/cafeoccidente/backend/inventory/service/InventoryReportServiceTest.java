package com.cafeoccidente.backend.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.inventory.dto.InventoryReportRow;
import com.cafeoccidente.backend.inventory.entity.InventoryMovement;
import com.cafeoccidente.backend.inventory.entity.Remission;
import com.cafeoccidente.backend.inventory.entity.RemissionLine;
import com.cafeoccidente.backend.inventory.repository.InventoryMovementRepository;
import com.cafeoccidente.backend.inventory.repository.RemissionLineRepository;
import com.cafeoccidente.backend.purchases.shared.entity.ProductCode;
import com.cafeoccidente.backend.purchases.shared.repository.ProductCodeRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Reportes INVXCODPROD / INVENTARIO: filtro, saldo con toda la historia (saldo inicial del año) y orden de Access. */
class InventoryReportServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 29);
    private static final LocalDate TO = LocalDate.of(2026, 12, 31);

    private final InventoryMovementRepository movements = mock(InventoryMovementRepository.class);
    private final RemissionLineRepository lines = mock(RemissionLineRepository.class);
    private final InventoryReportService service =
            new InventoryReportService(movements, lines, mock(ProductCodeRepository.class));

    private static InventoryMovement entry(long id, String code, String special, LocalDate date, int invoice, Instant createdAt) {
        ProductCode productCode = new ProductCode();
        productCode.setCode(code);
        InventoryMovement m = new InventoryMovement();
        m.setId(id);
        m.setProductCode(productCode);
        m.setSpecialType(special);
        m.setPurchaseDate(date);
        m.setInvoiceNumber(invoice);
        m.setNetKg(new BigDecimal("100"));
        m.setInventoryValue(new BigDecimal("1000000"));
        m.setHealthyPercentage(new BigDecimal("90"));
        m.setCreatedAt(createdAt);
        return m;
    }

    private static RemissionLine exit(long id, InventoryMovement origin, LocalDate date, int number, Instant createdAt) {
        Remission remission = new Remission();
        remission.setRemissionDate(date);
        remission.setDisplayNumber(String.valueOf(number));
        remission.setCreatedAt(createdAt);
        RemissionLine line = new RemissionLine();
        line.setId(id);
        line.setRemission(remission);
        line.setInventoryMovement(origin);
        line.setQuantity(new BigDecimal("40"));
        line.setOutputValue(new BigDecimal("400000"));
        return line;
    }

    @Test
    void earlierYearsBecomeTheOpeningRowSoJanuaryDoesNotStartAtZero() {
        // Cierre de año, fase 1: nada se borra; lo de 2025 (100 kg - 40 kg) llega como saldo inicial de 2026.
        InventoryMovement old = entry(1, "A", "RN", LocalDate.of(2025, 11, 3), 20, Instant.parse("2025-11-03T10:00:00Z"));
        InventoryMovement now = entry(2, "A", "RN", LocalDate.of(2026, 1, 4), 30, Instant.parse("2026-01-04T10:00:00Z"));
        when(movements.findByAgencyIdAndPurchaseDateLessThanEqual(1L, TO)).thenReturn(List.of(old, now));
        when(lines.findByRemissionAgencyIdAndRemissionRemissionDateLessThanEqual(1L, TO))
                .thenReturn(List.of(exit(9, old, LocalDate.of(2025, 12, 20), 4, Instant.parse("2025-12-20T10:00:00Z"))));

        List<InventoryReportRow> rows = service.report(1L, "A", null, TODAY);

        assertThat(rows).hasSize(2);
        InventoryReportRow opening = rows.get(0);
        // La especial real (para el titulo del reporte) y la etiqueta aparte.
        assertThat(opening.specialType()).isEqualTo("RN");
        assertThat(opening.label()).isEqualTo("Saldo inicial — viene de 2025");
        assertThat(opening.date()).isEqualTo(LocalDate.of(2025, 12, 31));
        assertThat(opening.netKg()).isEqualByComparingTo("60");
        assertThat(opening.inventoryValue()).isEqualByComparingTo("600000");
        // % ponderado que deja el año anterior: (90 x 100 - 40 x 90) / 60 = 90.
        assertThat(opening.healthyPercentage()).isEqualByComparingTo("90");
        assertThat(opening.invoiceNumber()).isNull();
        assertThat(rows.get(1).invoiceNumber()).isEqualTo(30);
    }

    @Test
    void withoutEarlierMovementsThereIsNoOpeningRow() {
        InventoryMovement now = entry(2, "A", "RN", LocalDate.of(2026, 1, 4), 30, Instant.parse("2026-01-04T10:00:00Z"));
        when(movements.findByAgencyIdAndPurchaseDateLessThanEqual(1L, TO)).thenReturn(List.of(now));

        assertThat(service.report(1L, null, "RN", TODAY)).singleElement()
                .satisfies(r -> assertThat(r.invoiceNumber()).isEqualTo(30));
    }

    @Test
    void asksForTheWholeHistoryUpToTheEndOfTheYear() {
        service.report(1L, "A", null, TODAY);
        org.mockito.Mockito.verify(movements).findByAgencyIdAndPurchaseDateLessThanEqual(1L, TO);
        org.mockito.Mockito.verify(lines).findByRemissionAgencyIdAndRemissionRemissionDateLessThanEqual(1L, TO);
    }

    @Test
    void byCodeFiltersTheCodeAndKeepsInsertionOrder() {
        InventoryMovement a1 = entry(1, "A", "RN", LocalDate.of(2026, 3, 5), 20, Instant.parse("2026-03-05T10:00:00Z"));
        InventoryMovement b1 = entry(2, "B", "RN", LocalDate.of(2026, 3, 1), 10, Instant.parse("2026-03-01T10:00:00Z"));
        InventoryMovement a2 = entry(3, "A", "RN", LocalDate.of(2026, 3, 2), 30, Instant.parse("2026-03-09T10:00:00Z"));
        when(movements.findByAgencyIdAndPurchaseDateLessThanEqual(1L, TO)).thenReturn(List.of(a2, b1, a1));
        when(lines.findByRemissionAgencyIdAndRemissionRemissionDateLessThanEqual(1L, TO))
                .thenReturn(List.of(exit(9, a1, LocalDate.of(2026, 3, 6), 4, Instant.parse("2026-03-06T10:00:00Z"))));

        List<InventoryReportRow> rows = service.report(1L, "A", null, TODAY);

        assertThat(rows).extracting(r -> r.invoiceNumber(), r -> r.remissionNumber())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(20, null),
                        org.assertj.core.groups.Tuple.tuple(null, "4"),
                        org.assertj.core.groups.Tuple.tuple(30, null));
        assertThat(rows.get(1).quantity()).isEqualByComparingTo("40");
        assertThat(rows.get(1).exitPercentage()).isEqualByComparingTo("90");
    }

    @Test
    void bySpecialSortsByDateThenInvoiceThenRemissionWithNullsFirst() {
        InventoryMovement e1 = entry(1, "A", "RN", LocalDate.of(2026, 3, 5), 20, Instant.now());
        InventoryMovement e2 = entry(2, "B", "RN", LocalDate.of(2026, 3, 5), 10, Instant.now());
        InventoryMovement other = entry(3, "C", "NESPRESSO - FTUSA", LocalDate.of(2026, 3, 1), 5, Instant.now());
        when(movements.findByAgencyIdAndPurchaseDateLessThanEqual(1L, TO)).thenReturn(List.of(e1, e2, other));
        when(lines.findByRemissionAgencyIdAndRemissionRemissionDateLessThanEqual(1L, TO))
                .thenReturn(List.of(exit(9, e1, LocalDate.of(2026, 3, 5), 4, Instant.now())));

        List<InventoryReportRow> rows = service.report(1L, null, "RN", TODAY);

        // Mismo dia: la salida (Factura Null) va antes que las entradas, como ordena Access.
        assertThat(rows).extracting(r -> r.invoiceNumber(), r -> r.remissionNumber())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(null, "4"),
                        org.assertj.core.groups.Tuple.tuple(10, null),
                        org.assertj.core.groups.Tuple.tuple(20, null));
    }

    @Test
    void needsExactlyOneFilter() {
        assertThatThrownBy(() -> service.report(1L, null, null, TODAY)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.report(1L, "A", "RN", TODAY)).isInstanceOf(BusinessRuleException.class);
    }
}
