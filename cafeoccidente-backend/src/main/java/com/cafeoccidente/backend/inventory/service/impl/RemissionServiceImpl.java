package com.cafeoccidente.backend.inventory.service.impl;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.common.security.SecurityUtils;
import com.cafeoccidente.backend.controlrecord.service.ControlRecordService;
import com.cafeoccidente.backend.inventory.dto.RemissionLineRequest;
import com.cafeoccidente.backend.inventory.dto.RemissionLineResponse;
import com.cafeoccidente.backend.inventory.dto.RemissionRequest;
import com.cafeoccidente.backend.inventory.dto.RemissionResponse;
import com.cafeoccidente.backend.inventory.entity.InventoryMovement;
import com.cafeoccidente.backend.inventory.entity.Remission;
import com.cafeoccidente.backend.inventory.entity.RemissionLine;
import com.cafeoccidente.backend.inventory.repository.CodeTotals;
import com.cafeoccidente.backend.inventory.repository.InventoryMovementRepository;
import com.cafeoccidente.backend.inventory.repository.RemissionLineRepository;
import com.cafeoccidente.backend.inventory.repository.RemissionRepository;
import com.cafeoccidente.backend.inventory.service.RemissionService;
import com.cafeoccidente.backend.purchases.shared.entity.Agency;
import com.cafeoccidente.backend.purchases.shared.entity.Fund;
import com.cafeoccidente.backend.purchases.shared.entity.Grower;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.purchases.shared.repository.GrowerRepository;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** "Salidas" (Form_EXITS.bas, Cantidad_AfterUpdate): despacha una o mas entradas de inventario. */
@Service
public class RemissionServiceImpl implements RemissionService {

    private final RemissionRepository remissionRepository;
    private final RemissionLineRepository remissionLineRepository;
    private final InventoryMovementRepository inventoryMovementRepository;
    private final AgencyRepository agencyRepository;
    private final GrowerRepository growerRepository;
    private final SecurityUtils securityUtils;
    private final ControlRecordService controlRecordService;

    public RemissionServiceImpl(
            RemissionRepository remissionRepository,
            RemissionLineRepository remissionLineRepository,
            InventoryMovementRepository inventoryMovementRepository,
            AgencyRepository agencyRepository,
            GrowerRepository growerRepository,
            SecurityUtils securityUtils,
            ControlRecordService controlRecordService) {
        this.controlRecordService = controlRecordService;
        this.remissionRepository = remissionRepository;
        this.remissionLineRepository = remissionLineRepository;
        this.inventoryMovementRepository = inventoryMovementRepository;
        this.agencyRepository = agencyRepository;
        this.growerRepository = growerRepository;
        this.securityUtils = securityUtils;
    }

    @Override
    @Transactional
    public RemissionResponse create(RemissionRequest request) {
        Agency agency = agencyRepository.findById(request.agencyId())
                .orElseThrow(() -> new ResourceNotFoundException("Agencia no encontrada"));
        Fund fund = requireSingleFund(request);
        Grower conductor = request.conductorIdNumber() == null || request.conductorIdNumber().isBlank()
                ? null
                : growerRepository.findByIdNumber(request.conductorIdNumber())
                        .orElseThrow(() -> new ResourceNotFoundException("No existe un caficultor con esa cedula"));

        Remission remission = new Remission();
        Integer maxUsed = remissionRepository.findMaxRemissionNumber(agency.getId());
        remission.setRemissionNumber(maxUsed == null ? 1 : maxUsed + 1);
        // Consecutivo propio por agencia + fondo (LF y RP no comparten serie).
        Integer lastSequence = remissionRepository.findMaxSequenceNumber(agency.getId(), fund.getId());
        int sequence = lastSequence == null ? 1 : lastSequence + 1;
        remission.setFund(fund);
        remission.setSequenceNumber(sequence);
        remission.setDisplayNumber(displayNumber(
                controlRecordService.getActive(agency.getId()).getPrefix(), fund.getCode(), sequence));
        remission.setAgency(agency);
        remission.setRemissionDate(request.remissionDate());
        remission.setDestination(request.destination());
        remission.setConductor(conductor);
        remission.setExported(false);
        remission.setCreatedByUserId(securityUtils.getCurrentUserId());
        remission.setCreatedAt(Instant.now());
        Remission savedRemission = remissionRepository.save(remission);

        // Saldo de cada Cod_Prod (Sld3), cargado una vez y descontado linea a linea: dos lineas del
        // mismo codigo en una remision se valoran en cadena, como dos salidas seguidas en Access.
        Map<Long, CodeBalance> balances = new HashMap<>();
        List<RemissionLine> lines = request.lines().stream()
                .map(lineRequest -> buildLine(savedRemission, lineRequest, balances))
                .toList();
        remissionLineRepository.saveAll(lines);

        return toResponse(savedRemission, lines);
    }

    /** En Access cada salida es de un solo Cod_Prod (Sld1 filtra por Formularios!EXITS!Especial) y el
     *  codigo ya trae el fondo: una remision no puede mezclar LF y RP. */
    private Fund requireSingleFund(RemissionRequest request) {
        List<Fund> funds = request.lines().stream()
                .map(line -> inventoryMovementRepository.findById(line.inventoryMovementId())
                        .orElseThrow(() -> new ResourceNotFoundException("Movimiento de inventario no encontrado"))
                        .getProductCode().getFund())
                .toList();
        if (funds.stream().map(fund -> fund.getId()).distinct().count() > 1) {
            throw new BusinessRuleException(
                    "Todas las líneas de una remisión deben ser del mismo fondo; haga una remisión por fondo");
        }
        return funds.get(0);
    }

    /** {prefijo del ControlRecord}-{LF|RP}-{consecutivo}: minimo 4 digitos, crece sin truncar. */
    static String displayNumber(String prefix, String fundCode, int sequence) {
        return prefix + "-" + fundCode + "-" + String.format("%04d", sequence);
    }

    /** Cantidad_AfterUpdate: "La salida no puede ser superior al saldo, vuelva a intentarlo". */
    private RemissionLine buildLine(
            Remission remission, RemissionLineRequest lineRequest, Map<Long, CodeBalance> balances) {
        InventoryMovement movement = inventoryMovementRepository.findById(lineRequest.inventoryMovementId())
                .orElseThrow(() -> new ResourceNotFoundException("Movimiento de inventario no encontrado"));
        if (lineRequest.quantity().compareTo(movement.getRemainingKg()) > 0) {
            throw new BusinessRuleException(
                    "La salida no puede ser superior al saldo disponible (saldo: " + movement.getRemainingKg()
                            + " kg, factura " + movement.getInvoiceNumber() + ")");
        }

        // Valor_unitario y % salida = VRUNIT y frpond de la consulta Sld3: promedio del Cod_Prod
        // (entradas - salidas del año en curso de la agencia), no el costo de la entrada origen.
        CodeBalance balance = balances.computeIfAbsent(
                movement.getProductCode().getId(),
                productCodeId -> codeBalance(remission.getAgency().getId(), productCodeId, remission.getRemissionDate()));
        if (lineRequest.quantity().compareTo(balance.kg) > 0) {
            // Cantidad_AfterUpdate: If Cantidad > Saldo (saldokg de Sld3) Then MsgBox ...
            throw new BusinessRuleException("La salida no puede ser superior al saldo, vuelva a intentarlo (saldo del "
                    + "código " + movement.getProductCode().getCode() + " en el año: " + balance.kg + " kg)");
        }
        BigDecimal unitValue = balance.value.divide(balance.kg, MathContext.DECIMAL64);
        BigDecimal exitPercentage = balance.kgFactor.divide(balance.kg, MathContext.DECIMAL64).setScale(6, RoundingMode.HALF_UP);
        // Vr_Salida = Valor_unitario * Cantidad, guardado en pesos enteros (inventario_migrar.csv: TA0441
        // 19191,19 x 280 = 5.373.533,75 -> 5373534).
        BigDecimal outputValue = unitValue.multiply(lineRequest.quantity())
                .setScale(0, RoundingMode.HALF_UP).setScale(2);
        balance.subtract(lineRequest.quantity(), outputValue, lineRequest.quantity().multiply(exitPercentage));

        movement.setRemainingKg(movement.getRemainingKg().subtract(lineRequest.quantity()));
        inventoryMovementRepository.save(movement);

        RemissionLine line = new RemissionLine();
        line.setRemission(remission);
        line.setInventoryMovement(movement);
        line.setQuantity(lineRequest.quantity());
        line.setUnitValue(unitValue.setScale(2, RoundingMode.HALF_UP));
        line.setOutputValue(outputValue);
        line.setExitPercentage(exitPercentage);
        line.setSacos(lineRequest.sacos());
        line.setGrossKg(lineRequest.grossKg());
        return line;
    }

    /** Sld2/Sld3 del Cod_Prod: entradas - salidas del año de la remision (Access borraba el año
     *  anterior con "eliminainventarioañoanterior", ver InventoryReportService). */
    private CodeBalance codeBalance(Long agencyId, Long productCodeId, LocalDate remissionDate) {
        LocalDate from = remissionDate.withDayOfYear(1);
        LocalDate to = remissionDate.withDayOfYear(remissionDate.lengthOfYear());
        CodeTotals entries = inventoryMovementRepository.sumEntries(agencyId, productCodeId, from, to);
        CodeTotals exits = remissionLineRepository.sumExits(agencyId, productCodeId, from, to);
        CodeBalance balance = new CodeBalance();
        balance.kg = entries.kg().subtract(exits.kg());                // saldokg
        balance.value = entries.value().subtract(exits.value());       // SALVAL
        balance.kgFactor = entries.kgFactor().subtract(exits.kgFactor()); // ac
        return balance;
    }

    /** Saldo corrido de un Cod_Prod mientras se arma la remision. */
    private static final class CodeBalance {
        private BigDecimal kg;
        private BigDecimal value;
        private BigDecimal kgFactor;

        private void subtract(BigDecimal quantity, BigDecimal outputValue, BigDecimal quantityFactor) {
            kg = kg.subtract(quantity);
            value = value.subtract(outputValue);
            kgFactor = kgFactor.subtract(quantityFactor);
        }
    }

    @Override
    public RemissionResponse findById(Long id) {
        Remission remission = remissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Remision no encontrada"));
        return toResponse(remission, remissionLineRepository.findByRemissionId(id));
    }

    @Override
    public List<RemissionResponse> listByAgency(Long agencyId) {
        return remissionRepository.findByAgencyIdOrderByRemissionNumberDesc(agencyId).stream()
                .map(this::toResponseWithLines)
                .toList();
    }

    @Override
    public Optional<RemissionResponse> findByNumber(Long agencyId, String displayNumber) {
        return remissionRepository
                .findByAgencyIdAndDisplayNumberIgnoreCase(agencyId, displayNumber.trim())
                .map(this::toResponseWithLines);
    }

    @Override
    public List<RemissionResponse> listPendingExport(Long agencyId) {
        return remissionRepository.findByAgencyIdAndExportedFalseOrderByRemissionNumberAsc(agencyId).stream()
                .map(this::toResponseWithLines)
                .toList();
    }

    @Override
    @Transactional
    public List<RemissionResponse> markExported(List<Long> remissionIds) {
        List<Remission> remissions = remissionRepository.findAllById(remissionIds);
        remissions.forEach(r -> r.setExported(true));
        remissionRepository.saveAll(remissions);
        return remissions.stream().map(this::toResponseWithLines).toList();
    }

    private RemissionResponse toResponseWithLines(Remission remission) {
        return toResponse(remission, remissionLineRepository.findByRemissionId(remission.getId()));
    }

    private RemissionResponse toResponse(Remission remission, List<RemissionLine> lines) {
        Grower conductor = remission.getConductor();
        return new RemissionResponse(
                remission.getId(),
                remission.getRemissionNumber(),
                remission.getAgency().getId(),
                remission.getAgency().getName(),
                remission.getRemissionDate(),
                remission.getDestination(),
                conductor == null ? null : conductor.getIdNumber(),
                conductor == null ? null : conductor.getFirstName() + " " + conductor.getLastName(),
                conductor == null ? null : conductor.getTransportCompany(),
                conductor == null ? null : conductor.getVehiclePlate(),
                remission.isExported(),
                lines.stream()
                        .map(line -> new RemissionLineResponse(
                                line.getId(),
                                line.getInventoryMovement().getId(),
                                line.getQuantity(),
                                line.getUnitValue(),
                                line.getOutputValue(),
                                line.getInventoryMovement().getSpecialType(),
                                line.getInventoryMovement().getHealthyPercentage(),
                                line.getInventoryMovement().getProductCode().getFund().getCode(),
                                line.getSacos(),
                                line.getGrossKg(),
                                line.getExitPercentage()))
                        .toList(),
                remission.getDisplayNumber());
    }
}
