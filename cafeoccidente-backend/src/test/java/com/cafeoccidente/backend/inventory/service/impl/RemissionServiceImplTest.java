package com.cafeoccidente.backend.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.common.security.CurrentAgencyProvider;
import com.cafeoccidente.backend.common.security.SecurityUtils;
import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import com.cafeoccidente.backend.controlrecord.service.ControlRecordService;
import com.cafeoccidente.backend.history.service.ChangeHistoryService;
import com.cafeoccidente.backend.inventory.dto.RemissionLineRequest;
import com.cafeoccidente.backend.inventory.dto.RemissionRequest;
import com.cafeoccidente.backend.inventory.dto.RemissionResponse;
import com.cafeoccidente.backend.inventory.entity.InventoryMovement;
import com.cafeoccidente.backend.inventory.entity.Remission;
import com.cafeoccidente.backend.inventory.entity.RemissionLine;
import com.cafeoccidente.backend.inventory.repository.CodeTotals;
import com.cafeoccidente.backend.inventory.repository.InventoryMovementRepository;
import com.cafeoccidente.backend.inventory.repository.RemissionConsecutiveRepository;
import com.cafeoccidente.backend.inventory.repository.RemissionLineRepository;
import com.cafeoccidente.backend.inventory.repository.RemissionRepository;
import com.cafeoccidente.backend.purchases.annulment.repository.AnnulmentModule;
import com.cafeoccidente.backend.purchases.remittance.repository.RemittanceRepository;
import com.cafeoccidente.backend.purchases.remittance.repository.RemittanceRepository.LockedPurchase;
import com.cafeoccidente.backend.purchases.shared.entity.Agency;
import com.cafeoccidente.backend.purchases.shared.entity.Fund;
import com.cafeoccidente.backend.purchases.shared.entity.Grower;
import com.cafeoccidente.backend.purchases.shared.entity.ProductCode;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.purchases.shared.repository.GrowerRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Registrar Salidas (Form_EXITS.bas) con las respuestas del area del 23-nov: cada factura sale completa, la remision
 * genera su remesa con el mismo numero PREFIJO-CONSECUTIVO y registra el empaque; Anular Remision la deshace.
 */
@SuppressWarnings("unchecked")
class RemissionServiceImplTest {

    private final RemissionRepository remissionRepository = mock(RemissionRepository.class);
    private final RemissionLineRepository remissionLineRepository = mock(RemissionLineRepository.class);
    private final InventoryMovementRepository inventoryMovementRepository = mock(InventoryMovementRepository.class);
    private final AgencyRepository agencyRepository = mock(AgencyRepository.class);
    private final GrowerRepository growerRepository = mock(GrowerRepository.class);
    private final SecurityUtils securityUtils = mock(SecurityUtils.class);
    private final ControlRecordService controlRecordService = mock(ControlRecordService.class);
    private final RemissionConsecutiveRepository consecutives = mock(RemissionConsecutiveRepository.class);
    private final RemittanceRepository remittances = mock(RemittanceRepository.class);
    private final ChangeHistoryService history = mock(ChangeHistoryService.class);

    /** Agencia de trabajo de la sesion (RF-05.1): la 1, la misma que devuelve agencyRepository. */
    private final CurrentAgencyProvider currentAgencyProvider = mock(CurrentAgencyProvider.class, invocation -> 1L);
    private final RemissionServiceImpl service = new RemissionServiceImpl(
            remissionRepository, remissionLineRepository, inventoryMovementRepository, agencyRepository,
            growerRepository, securityUtils, currentAgencyProvider, controlRecordService, consecutives, remittances, history);

    private static RemissionRequest request(LocalDate date, String conductor, Long... movementIds) {
        return new RemissionRequest(date, "ALMACAFE", conductor,
                Arrays.stream(movementIds).map(RemissionLineRequest::new).toList(), 12, "usado");
    }

    private static RemissionRequest request(Long... movementIds) {
        return request(LocalDate.now(), null, movementIds);
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

    private InventoryMovement movement(long id, String remainingKg) {
        InventoryMovement movement = new InventoryMovement();
        movement.setId(id);
        movement.setProductCode(productCode(1L, "RP"));
        movement.setAgency(agency());
        movement.setPurchaseModule(InventoryMovement.PurchaseModule.DRY_COFFEE);
        movement.setPurchaseId(100L + id);
        movement.setInvoiceNumber(27867);
        movement.setSpecialType("RN");
        movement.setNetKg(new BigDecimal("5000"));
        movement.setRemainingKg(new BigDecimal(remainingKg));
        movement.setSacos(9);
        movement.setGrossKg(new BigDecimal("365"));
        when(inventoryMovementRepository.findById(id)).thenReturn(Optional.of(movement));
        return movement;
    }

    private void mockCommonSaves() {
        when(agencyRepository.findById(1L)).thenReturn(Optional.of(agency()));
        ControlRecord controlRecord = new ControlRecord();
        controlRecord.setPrefix("SDBU");
        controlRecord.setTrustedId("98765");
        when(controlRecordService.getActive(1L)).thenReturn(controlRecord);
        when(consecutives.take(1L)).thenReturn(969);
        when(remittances.insert(any(), any(), eq(969), any(), any(), eq(12), any(), any(), any())).thenReturn(55L);
        // Las compras de las facturas: de la agencia, RP, validas y sin remesa.
        when(remittances.lock(any(), anyList())).thenAnswer(inv -> ((List<Long>) inv.getArgument(1)).stream()
                .map(id -> new LockedPurchase(id, 1L, "RP", "RN", "VALIDA", BigDecimal.TEN, null, 27867)).toList());
        // Sld3 por defecto: 1200 kg a 1255/kg y factor 90 en el año, sin salidas previas.
        mockCodeTotals(new CodeTotals(new BigDecimal("1200"), new BigDecimal("1506000"), new BigDecimal("108000")),
                new CodeTotals(null, null, null));
    }

    private void mockCodeTotals(CodeTotals entries, CodeTotals exits) {
        when(inventoryMovementRepository.sumEntries(any(), any(), any())).thenReturn(entries);
        when(remissionLineRepository.sumExits(any(), any(), any())).thenReturn(exits);
        when(remissionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void theWholeBalanceOfTheInvoiceGoesOutValuedAtTheCodeAverage() {
        mockCommonSaves();
        InventoryMovement movement = movement(10L, "500.00");

        RemissionResponse response = service.create(request(10L));

        // VRUNIT (Sld3) = 1506000 / 1200 = 1255.00 ; Vr_Salida = 1255 * 500 = 627500
        assertThat(response.lines()).hasSize(1);
        assertThat(response.lines().get(0).quantity()).isEqualByComparingTo("500");
        assertThat(response.lines().get(0).unitValue()).isEqualByComparingTo("1255.00");
        assertThat(response.lines().get(0).outputValue()).isEqualByComparingTo("627500.00");
        assertThat(movement.getRemainingKg()).isEqualByComparingTo("0");
        verify(inventoryMovementRepository).save(movement);
    }

    @Test
    void takesSacosAndGrossKgFromTheInvoice() {
        mockCommonSaves();
        movement(10L, "400.00");

        RemissionResponse response = service.create(request(10L));

        assertThat(response.lines().get(0).sacos()).isEqualTo(9);
        assertThat(response.lines().get(0).grossKg()).isEqualByComparingTo("365");
    }

    @Test
    void anInvoiceThatAlreadyWentOutIsRejected() {
        mockCommonSaves();
        movement(10L, "0.00");

        assertThatThrownBy(() -> service.create(request(10L)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ya salió en otra remisión");
        verify(inventoryMovementRepository, never()).save(any());
    }

    @Test
    void anInvoiceAlreadyInARemittanceIsRejected() {
        mockCommonSaves();
        movement(10L, "500.00");
        when(remittances.lock(any(), anyList())).thenReturn(
                List.of(new LockedPurchase(110L, 1L, "RP", "RN", "VALIDA", BigDecimal.TEN, 7L, 27867)));

        assertThatThrownBy(() -> service.create(request(10L)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ya no está pendiente de remesar");
        verify(remissionRepository, never()).save(any());
    }

    @Test
    void thePackagingTypeMustBeNewOrUsed() {
        mockCommonSaves();
        movement(10L, "500.00");
        RemissionRequest broken = new RemissionRequest(LocalDate.now(), "ALMACAFE", null,
                List.of(new RemissionLineRequest(10L)), 12, "ROTO");

        assertThatThrownBy(() -> service.create(broken))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("NUEVO o USADO");
    }

    @Test
    void linesFromDifferentFundsAreRejected() {
        mockCommonSaves();
        movement(10L, "100.00");
        movement(11L, "100.00").setProductCode(productCode(2L, "LF"));
        RemissionRequest mixed = request(10L, 11L);

        assertThatThrownBy(() -> service.create(mixed))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("mismo fondo");
        verify(remissionRepository, never()).save(any());
        verify(inventoryMovementRepository, never()).save(any());
    }

    @Test
    void theRemissionAndItsRemittanceShareThePrefixAndTheAgencyConsecutive() {
        mockCommonSaves();
        movement(10L, "100.00");

        RemissionResponse response = service.create(request(10L));

        assertThat(response.displayNumber()).isEqualTo("SDBU-969");
        assertThat(response.status()).isEqualTo("VIGENTE");
        assertThat(response.remittanceId()).isEqualTo(55L);
        verify(remittances).insert(eq(1L), eq(1L), eq(969), eq("SDBU-969"), eq("RN"), eq(12), eq("USADO"), any(), any());
        verify(remittances).assign(AnnulmentModule.DRY, List.of(110L), 55L);
        verify(remittances).addPackagingExit(1L, "SDBU-969", "USADO", "98765", 12);
    }

    @Test
    void conductorWithoutLastNameOrAgencyShowsOnlyTheFirstName() {
        mockCommonSaves();
        movement(10L, "100.00");
        Grower conductor = new Grower();
        conductor.setIdNumber("990723205");
        conductor.setFirstName("FUNDACION PRUEBA");
        when(growerRepository.findByIdNumber("990723205")).thenReturn(Optional.of(conductor));

        RemissionResponse response = service.create(request(LocalDate.now(), "990723205", 10L));

        assertThat(response.conductorName()).isEqualTo("FUNDACION PRUEBA");
    }

    @Test
    void displayNumberIsPrefixAndConsecutive() {
        assertThat(RemissionServiceImpl.displayNumber("SDTA", 125)).isEqualTo("SDTA-125");
        assertThat(RemissionServiceImpl.displayNumber("SDTA", 10000)).isEqualTo("SDTA-10000");
    }

    private RemissionResponse dispatch(String quantity) {
        movement(10L, quantity);
        return service.create(request(LocalDate.of(2026, 4, 11), null, 10L));
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
        InventoryMovement a = movement(10L, "300");
        movement(11L, "500").setProductCode(a.getProductCode());

        RemissionResponse ok = service.create(request(LocalDate.of(2026, 5, 7), null, 10L, 11L));
        assertThat(ok.lines().get(0).outputValue()).isEqualByComparingTo("3187500");
        assertThat(ok.lines().get(1).outputValue()).isEqualByComparingTo("5312500");
        assertThat(ok.lines().get(1).exitPercentage()).isEqualByComparingTo("90");

        // Con el mismo saldo de 800 kg, 300 + 501 ya no alcanza: la segunda linea ve el saldo que dejo la primera.
        movement(10L, "300").setProductCode(a.getProductCode());
        movement(11L, "501").setProductCode(a.getProductCode());
        RemissionRequest tooMuch = request(LocalDate.of(2026, 5, 7), null, 10L, 11L);
        assertThatThrownBy(() -> service.create(tooMuch))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("saldo");
    }

    @Test
    void theBalanceIsTheWholeHistoryOfTheCodeUpToTheRemissionDate() {
        // Sin corte por año (decision del 24-nov): el 1 de enero el saldo no arranca en cero.
        mockCommonSaves();
        InventoryMovement movement = movement(10L, "100");
        movement.getProductCode().setId(7L);

        service.create(request(LocalDate.of(2027, 1, 2), null, 10L));

        verify(inventoryMovementRepository).sumEntries(1L, 7L, LocalDate.of(2027, 1, 2));
        verify(remissionLineRepository).sumExits(1L, 7L, LocalDate.of(2027, 1, 2));
    }

    @Test
    void theExitUsesTheExactAverageAndRoundsOnlyTheResultToThePeso() {
        // Ejemplo del 24-nov (datos inventados con las cifras del area): 5.424,50 kg y $97.898.409
        // -> promedio exacto 18.047,4530... -> 7 kg = 126.332,17 -> 126.332.
        mockCommonSaves();
        mockCodeTotals(new CodeTotals(new BigDecimal("5424.50"), new BigDecimal("97898409"), new BigDecimal("488205")),
                new CodeTotals(null, null, null));

        var line = dispatch("7").lines().get(0);

        assertThat(line.outputValue()).isEqualByComparingTo("126332");
        assertThat(line.unitValue()).isEqualByComparingTo("18047.45");
    }

    @Test
    void cannotDispatchMoreThanTheCodeBalance() {
        // El movimiento aun tiene saldo propio, pero el Cod_Prod solo tiene 100 kg de saldo (Sld3 saldokg).
        mockCommonSaves();
        mockCodeTotals(new CodeTotals(new BigDecimal("100"), new BigDecimal("125500"), new BigDecimal("9000")),
                new CodeTotals(null, null, null));

        assertThatThrownBy(() -> dispatch("120"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("vuelva a intentarlo");
    }

    @Test
    void aRemissionOfAnotherAgencyIsNotFoundById() {
        // Hallazgo 2 (24-nov): la remision 7 es de la agencia 2 y la sesion trabaja en la 1.
        Remission other = new Remission();
        other.setId(7L);
        Agency agency2 = new Agency();
        agency2.setId(2L);
        other.setAgency(agency2);
        when(remissionRepository.findById(7L)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.findById(7L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aMovementOfAnotherAgencyCannotGoOutInARemission() {
        mockCommonSaves();
        InventoryMovement other = movement(10L, "100");
        Agency agency2 = new Agency();
        agency2.setId(2L);
        other.setAgency(agency2);

        assertThatThrownBy(() -> service.create(request(10L))).isInstanceOf(ResourceNotFoundException.class);
        verify(remittances, never()).lock(any(), anyList());
        verify(inventoryMovementRepository, never()).save(any());
    }

    private Remission saved(String status) {
        Remission remission = new Remission();
        remission.setId(5L);
        remission.setAgency(agency());
        remission.setDisplayNumber("SDBU-969");
        remission.setStatus(status);
        when(remissionRepository.findForUpdate(5L)).thenReturn(Optional.of(remission));
        return remission;
    }

    @Test
    void annullingGivesTheBalanceBackUnmarksTheRemittanceAndIsRecorded() {
        Remission remission = saved(Remission.VIGENTE);
        InventoryMovement movement = movement(10L, "0");
        RemissionLine line = new RemissionLine();
        line.setRemission(remission);
        line.setInventoryMovement(movement);
        line.setQuantity(new BigDecimal("500"));
        when(remissionLineRepository.findByRemissionId(5L)).thenReturn(List.of(line));
        when(remittances.idByRemission(5L)).thenReturn(Optional.of(55L));
        when(securityUtils.getCurrentUserId()).thenReturn(3L);

        RemissionResponse response = service.annul(5L);

        assertThat(response.status()).isEqualTo("ANULADA");
        assertThat(response.fromAccess()).isFalse();
        assertThat(remission.getAnnulledByUserId()).isEqualTo(3L);
        assertThat(remission.getAnnulledAt()).isNotNull();
        assertThat(movement.getRemainingKg()).isEqualByComparingTo("500");
        verify(remittances).unmark(1L, 55L, "SDBU-969", 3L);
        verify(history).record(ChangeHistoryService.Entity.REMISION, 5L, ChangeHistoryService.Action.ANULAR, 1L,
                Map.of("number", "SDBU-969", "status", "VIGENTE"), Map.of("number", "SDBU-969", "status", "ANULADA"));
    }

    /** 25-nov: una salida historica que vino de Access no se anula (no se sabe a que facturas devolver los kilos). */
    @Test
    void aRemissionFromAccessCannotBeAnnulled() {
        Remission remission = saved(Remission.VIGENTE);
        remission.setFromAccess(true);

        assertThatThrownBy(() -> service.annul(5L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("La remisión SDBU-969 es una remisión histórica de Access: no se puede anular.");
        assertThat(remission.getStatus()).isEqualTo(Remission.VIGENTE);
        verify(remissionLineRepository, never()).findByRemissionId(any());
        verify(remittances, never()).unmark(any(), any(), any(), any());
        verify(history, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void anAnnulledRemissionCannotBeAnnulledAgain() {
        saved(Remission.ANULADA);

        assertThatThrownBy(() -> service.annul(5L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ya está anulada");
        verify(remittances, never()).unmark(any(), any(), any(), any());
    }

    @Test
    void aRemissionOfAnotherAgencyIsNotFound() {
        Remission remission = saved(Remission.VIGENTE);
        Agency other = new Agency();
        other.setId(2L);
        remission.setAgency(other);

        assertThatThrownBy(() -> service.annul(5L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
