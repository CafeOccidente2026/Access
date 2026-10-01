package com.cafeoccidente.backend.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.security.SecurityUtils;
import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import com.cafeoccidente.backend.controlrecord.service.ControlRecordService;
import com.cafeoccidente.backend.inventory.dto.RemissionLineRequest;
import com.cafeoccidente.backend.inventory.dto.RemissionRequest;
import com.cafeoccidente.backend.inventory.dto.RemissionResponse;
import com.cafeoccidente.backend.inventory.entity.InventoryMovement;
import com.cafeoccidente.backend.inventory.repository.CodeTotals;
import com.cafeoccidente.backend.inventory.repository.InventoryMovementRepository;
import com.cafeoccidente.backend.inventory.repository.RemissionLineRepository;
import com.cafeoccidente.backend.inventory.repository.RemissionRepository;
import com.cafeoccidente.backend.purchases.shared.entity.Agency;
import com.cafeoccidente.backend.purchases.shared.entity.Fund;
import com.cafeoccidente.backend.purchases.shared.entity.ProductCode;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.purchases.shared.repository.GrowerRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Form_EXITS.bas, Cantidad_AfterUpdate: "La salida no puede ser superior al saldo". */
class RemissionServiceImplTest {

    private final RemissionRepository remissionRepository = mock(RemissionRepository.class);
    private final RemissionLineRepository remissionLineRepository = mock(RemissionLineRepository.class);
    private final InventoryMovementRepository inventoryMovementRepository = mock(InventoryMovementRepository.class);
    private final AgencyRepository agencyRepository = mock(AgencyRepository.class);
    private final GrowerRepository growerRepository = mock(GrowerRepository.class);
    private final SecurityUtils securityUtils = mock(SecurityUtils.class);
    private final ControlRecordService controlRecordService = mock(ControlRecordService.class);

    private final RemissionServiceImpl service = new RemissionServiceImpl(
            remissionRepository, remissionLineRepository, inventoryMovementRepository,
            agencyRepository, growerRepository, securityUtils, controlRecordService);

    /** Linea con Sacos y Kilos Brutos de relleno (no intervienen en saldo ni valor). */
    private static RemissionLineRequest line(long movementId, String quantity) {
        return new RemissionLineRequest(movementId, new BigDecimal(quantity), 1, new BigDecimal(quantity));
    }

    private Agency agency() {
        Agency agency = new Agency();
        agency.setId(1L);
        agency.setName("Buesaco");
        return agency;
    }

    private static ProductCode productCode(long fundId, String fundCode) {
        Fund fund = new Fund();
        fund.setId(fundId);
        fund.setCode(fundCode);
        ProductCode productCode = new ProductCode();
        productCode.setFund(fund);
        return productCode;
    }

    private InventoryMovement movement(BigDecimal netKg, BigDecimal remainingKg, BigDecimal inventoryValue) {
        InventoryMovement movement = new InventoryMovement();
        movement.setId(10L);
        movement.setProductCode(productCode(1L, "RP"));
        movement.setPurchaseModule(InventoryMovement.PurchaseModule.DRY_COFFEE);
        movement.setPurchaseId(100L);
        movement.setInvoiceNumber(27867);
        movement.setNetKg(netKg);
        movement.setRemainingKg(remainingKg);
        movement.setInventoryValue(inventoryValue);
        return movement;
    }

    private void mockCommonSaves() {
        when(agencyRepository.findById(1L)).thenReturn(Optional.of(agency()));
        when(remissionRepository.findMaxRemissionNumber(1L)).thenReturn(null);
        ControlRecord controlRecord = new ControlRecord();
        controlRecord.setPrefix("SDBU");
        when(controlRecordService.getActive(1L)).thenReturn(controlRecord);
        // Sld3 por defecto: 1200 kg a 1255/kg y factor 90 en el año, sin salidas previas.
        mockCodeTotals(new CodeTotals(new BigDecimal("1200"), new BigDecimal("1506000"), new BigDecimal("108000")),
                new CodeTotals(null, null, null));
    }

    private void mockCodeTotals(CodeTotals entries, CodeTotals exits) {
        when(inventoryMovementRepository.sumEntries(any(), any(), any(), any())).thenReturn(entries);
        when(remissionLineRepository.sumExits(any(), any(), any(), any())).thenReturn(exits);
        when(remissionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void quantityAboveRemainingKgIsRejected() {
        mockCommonSaves();
        InventoryMovement movement = movement(new BigDecimal("1200.00"), new BigDecimal("100.00"), new BigDecimal("1506000.00"));
        when(inventoryMovementRepository.findById(10L)).thenReturn(Optional.of(movement));

        RemissionRequest request = new RemissionRequest(
                1L, LocalDate.now(), "Bodega central", null,
                java.util.List.of(line(10L, "150.00")));

        assertThatThrownBy(() -> service.create(request)).isInstanceOf(BusinessRuleException.class);

        // Como fallo la validacion de saldo, el movimiento nunca se debe haber tocado.
        verify(inventoryMovementRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void quantityWithinRemainingKgIsValuedAtTheCodeAverage() {
        mockCommonSaves();
        InventoryMovement movement = movement(new BigDecimal("1200.00"), new BigDecimal("1200.00"), new BigDecimal("1506000.00"));
        when(inventoryMovementRepository.findById(10L)).thenReturn(Optional.of(movement));

        RemissionRequest request = new RemissionRequest(
                1L, LocalDate.now(), "Bodega central", null,
                java.util.List.of(line(10L, "500.00")));

        RemissionResponse response = service.create(request);

        // VRUNIT (Sld3) = 1506000 / 1200 = 1255.00 ; Vr_Salida = 1255 * 500 = 627500
        assertThat(response.lines()).hasSize(1);
        assertThat(response.lines().get(0).unitValue()).isEqualByComparingTo("1255.00");
        assertThat(response.lines().get(0).outputValue()).isEqualByComparingTo("627500.00");
    }

    @Test
    void dispatchingDecrementsTheMovementRemainingKg() {
        mockCommonSaves();
        InventoryMovement movement = movement(new BigDecimal("1200.00"), new BigDecimal("1200.00"), new BigDecimal("1506000.00"));
        when(inventoryMovementRepository.findById(10L)).thenReturn(Optional.of(movement));

        RemissionRequest request = new RemissionRequest(
                1L, LocalDate.now(), "Bodega central", null,
                java.util.List.of(line(10L, "500.00")));

        service.create(request);

        assertThat(movement.getRemainingKg()).isEqualByComparingTo("700.00");
        verify(inventoryMovementRepository).save(movement);
    }

    @Test
    void exactlyTheFullRemainingBalanceIsAllowed() {
        mockCommonSaves();
        InventoryMovement movement = movement(new BigDecimal("1200.00"), new BigDecimal("300.00"), new BigDecimal("1506000.00"));
        when(inventoryMovementRepository.findById(10L)).thenReturn(Optional.of(movement));

        RemissionRequest request = new RemissionRequest(
                1L, LocalDate.now(), "Bodega central", null,
                java.util.List.of(line(10L, "300.00")));

        service.create(request);

        assertThat(movement.getRemainingKg()).isEqualByComparingTo("0.00");
    }

    @Test
    void linesFromDifferentFundsAreRejected() {
        mockCommonSaves();
        InventoryMovement rp = movement(new BigDecimal("100.00"), new BigDecimal("100.00"), new BigDecimal("125500.00"));
        InventoryMovement lf = movement(new BigDecimal("100.00"), new BigDecimal("100.00"), new BigDecimal("125500.00"));
        lf.setId(11L);
        lf.setProductCode(productCode(2L, "LF"));
        when(inventoryMovementRepository.findById(10L)).thenReturn(Optional.of(rp));
        when(inventoryMovementRepository.findById(11L)).thenReturn(Optional.of(lf));

        RemissionRequest request = new RemissionRequest(
                1L, LocalDate.now(), "Bodega central", null,
                java.util.List.of(
                        line(10L, "10.00"),
                        line(11L, "10.00")));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("mismo fondo");
        verify(remissionRepository, org.mockito.Mockito.never()).save(any());
        verify(inventoryMovementRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void numbersEachAgencyAndFundWithItsOwnSequence() {
        mockCommonSaves();
        InventoryMovement rp = movement(new BigDecimal("100.00"), new BigDecimal("100.00"), new BigDecimal("125500.00"));
        when(inventoryMovementRepository.findById(10L)).thenReturn(Optional.of(rp));
        // RP (fondo 1) ya va en 947; LF (fondo 2) no se consulta: su serie es otra.
        when(remissionRepository.findMaxSequenceNumber(1L, 1L)).thenReturn(947);

        RemissionResponse response = service.create(new RemissionRequest(
                1L, LocalDate.now(), "ALMACAFE", null, java.util.List.of(line(10L, "10.00"))));

        assertThat(response.displayNumber()).isEqualTo("SDBU-RP-0948");
        verify(remissionRepository).findMaxSequenceNumber(1L, 1L);
        verify(remissionRepository, org.mockito.Mockito.never()).findMaxSequenceNumber(1L, 2L);
    }

    @Test
    void firstRemissionOfAFundStartsAtOne() {
        mockCommonSaves();
        InventoryMovement lf = movement(new BigDecimal("100.00"), new BigDecimal("100.00"), new BigDecimal("125500.00"));
        lf.setProductCode(productCode(2L, "LF"));
        when(inventoryMovementRepository.findById(10L)).thenReturn(Optional.of(lf));

        RemissionResponse response = service.create(new RemissionRequest(
                1L, LocalDate.now(), "ALMACAFE", null, java.util.List.of(line(10L, "10.00"))));

        assertThat(response.displayNumber()).isEqualTo("SDBU-LF-0001");
    }

    @Test
    void conductorWithoutLastNameOrAgencyShowsOnlyTheFirstName() {
        mockCommonSaves();
        InventoryMovement rp = movement(new BigDecimal("100.00"), new BigDecimal("100.00"), new BigDecimal("125500.00"));
        when(inventoryMovementRepository.findById(10L)).thenReturn(Optional.of(rp));
        com.cafeoccidente.backend.purchases.shared.entity.Grower conductor =
                new com.cafeoccidente.backend.purchases.shared.entity.Grower();
        conductor.setIdNumber("900723205");
        conductor.setFirstName("FUNDACION SUYUSAMA");
        when(growerRepository.findByIdNumber("900723205")).thenReturn(Optional.of(conductor));

        RemissionResponse response = service.create(new RemissionRequest(
                1L, LocalDate.now(), "ALMACAFE", "900723205", java.util.List.of(line(10L, "10.00"))));

        assertThat(response.conductorName()).isEqualTo("FUNDACION SUYUSAMA");
    }

    @Test
    void displayNumberPadsToFourDigitsAndGrowsBeyond() {
        assertThat(RemissionServiceImpl.displayNumber("SDTA", "LF", 7)).isEqualTo("SDTA-LF-0007");
        assertThat(RemissionServiceImpl.displayNumber("SDTA", "RP", 9999)).isEqualTo("SDTA-RP-9999");
        assertThat(RemissionServiceImpl.displayNumber("SDTA", "RP", 10000)).isEqualTo("SDTA-RP-10000");
    }

    @Test
    void keepsSacosAndGrossKgOfEachLine() {
        mockCommonSaves();
        InventoryMovement rp = movement(new BigDecimal("400.00"), new BigDecimal("400.00"), new BigDecimal("502000.00"));
        when(inventoryMovementRepository.findById(10L)).thenReturn(Optional.of(rp));

        RemissionResponse response = service.create(new RemissionRequest(1L, LocalDate.now(), "ALMACAFE", null,
                java.util.List.of(new RemissionLineRequest(10L, new BigDecimal("360"), 9, new BigDecimal("365")))));

        assertThat(response.lines().get(0).sacos()).isEqualTo(9);
        assertThat(response.lines().get(0).grossKg()).isEqualByComparingTo("365");
    }

    private RemissionResponse dispatch(String quantity) {
        InventoryMovement movement = movement(new BigDecimal("5000"), new BigDecimal("5000"), new BigDecimal("1"));
        when(inventoryMovementRepository.findById(10L)).thenReturn(Optional.of(movement));
        return service.create(new RemissionRequest(1L, LocalDate.of(2026, 4, 11), "ALMACAFE", null,
                java.util.List.of(line(10L, quantity))));
    }

    @Test
    void reproducesAccessExitTA0436() {
        // inventario_migrar.csv, Cod_Prod 0110002000002 antes de TA0436: 360 kg, $7.471.700, KXFC 34346,31...
        mockCommonSaves();
        mockCodeTotals(new CodeTotals(new BigDecimal("360"), new BigDecimal("7471700"), new BigDecimal("34346.31197334271")),
                new CodeTotals(null, null, null));

        var line = dispatch("360").lines().get(0);

        assertThat(line.outputValue()).isEqualByComparingTo("7471700");     // Access: 7471700
        assertThat(line.unitValue()).isEqualByComparingTo("20754.72");      // Access: 20754.7222...
        assertThat(line.exitPercentage()).isEqualByComparingTo("95.406422"); // Access: 95.40642214817419
    }

    @Test
    void reproducesAccessExitTA0441RoundedToWholePeso() {
        // Cod_Prod 0110002000001 antes de TA0441: 349 kg, $6.697.726 -> 19191,19197.. x 280 = 5.373.533,75
        mockCommonSaves();
        mockCodeTotals(new CodeTotals(new BigDecimal("349"), new BigDecimal("6697726"), new BigDecimal("33220.76540969172")),
                new CodeTotals(null, null, null));

        var line = dispatch("280").lines().get(0);

        assertThat(line.outputValue()).isEqualByComparingTo("5373534");     // Access: 5373534
        assertThat(line.exitPercentage()).isEqualByComparingTo("95.188440"); // Access: 95.18843956931724
    }

    @Test
    void previousExitsOfTheCodeReduceTheBalanceAndLinesOfOneRemissionChain() {
        mockCommonSaves();
        // Año: entradas 1000 kg / $10.000.000 / KXFC 90000; salidas previas 200 kg / $1.500.000 / 18000
        // -> saldo 800 kg, SALVAL 8.500.000, VRUNIT 10625, frpond 90.
        mockCodeTotals(new CodeTotals(new BigDecimal("1000"), new BigDecimal("10000000"), new BigDecimal("90000")),
                new CodeTotals(new BigDecimal("200"), new BigDecimal("1500000"), new BigDecimal("18000")));
        InventoryMovement a = movement(new BigDecimal("600"), new BigDecimal("600"), new BigDecimal("1"));
        InventoryMovement b = movement(new BigDecimal("600"), new BigDecimal("600"), new BigDecimal("1"));
        b.setId(11L);
        b.setProductCode(a.getProductCode());
        when(inventoryMovementRepository.findById(10L)).thenReturn(Optional.of(a));
        when(inventoryMovementRepository.findById(11L)).thenReturn(Optional.of(b));

        RemissionResponse ok = service.create(new RemissionRequest(1L, LocalDate.of(2026, 5, 7), "ALMACAFE", null,
                java.util.List.of(line(10L, "300"), line(11L, "500"))));
        assertThat(ok.lines().get(0).outputValue()).isEqualByComparingTo("3187500");
        assertThat(ok.lines().get(1).outputValue()).isEqualByComparingTo("5312500");
        assertThat(ok.lines().get(1).exitPercentage()).isEqualByComparingTo("90");

        // Con el mismo saldo de 800 kg, 300 + 501 ya no alcanza: la segunda linea ve el saldo que dejo la primera.
        a.setRemainingKg(new BigDecimal("600"));
        b.setRemainingKg(new BigDecimal("600"));
        assertThatThrownBy(() -> service.create(new RemissionRequest(1L, LocalDate.of(2026, 5, 7), "ALMACAFE", null,
                java.util.List.of(line(10L, "300"), line(11L, "501")))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("saldo");
    }

    @Test
    void cannotDispatchMoreThanTheCodeBalanceOfTheYear() {
        // El movimiento aun tiene saldo propio, pero el Cod_Prod solo tiene 100 kg en el año (Sld3 saldokg).
        mockCommonSaves();
        mockCodeTotals(new CodeTotals(new BigDecimal("100"), new BigDecimal("125500"), new BigDecimal("9000")),
                new CodeTotals(null, null, null));

        assertThatThrownBy(() -> dispatch("120"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("vuelva a intentarlo");
    }
}
