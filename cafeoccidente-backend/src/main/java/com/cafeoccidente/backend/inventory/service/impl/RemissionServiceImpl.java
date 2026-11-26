package com.cafeoccidente.backend.inventory.service.impl;

import com.cafeoccidente.backend.annulmentlog.service.AnnulmentLogService;
import com.cafeoccidente.backend.annulmentlog.service.AnnulmentReason;
import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.common.security.CurrentAgencyProvider;
import com.cafeoccidente.backend.common.security.SecurityUtils;
import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import com.cafeoccidente.backend.controlrecord.service.ControlRecordService;
import com.cafeoccidente.backend.history.service.ChangeHistoryService;
import com.cafeoccidente.backend.inventory.dto.RemissionLineRequest;
import com.cafeoccidente.backend.inventory.dto.RemissionLineResponse;
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
import com.cafeoccidente.backend.inventory.service.RemissionService;
import com.cafeoccidente.backend.purchases.annulment.repository.AnnulmentModule;
import com.cafeoccidente.backend.purchases.remittance.repository.RemittanceRepository;
import com.cafeoccidente.backend.purchases.remittance.service.impl.RemittanceServiceImpl;
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
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
    private final CurrentAgencyProvider currentAgencyProvider;
    private final ControlRecordService controlRecordService;
    private final RemissionConsecutiveRepository consecutives;
    private final RemittanceRepository remittances;
    private final ChangeHistoryService history;

    private final AnnulmentLogService annulmentLog;

    public RemissionServiceImpl(
            RemissionRepository remissionRepository,
            RemissionLineRepository remissionLineRepository,
            InventoryMovementRepository inventoryMovementRepository,
            AgencyRepository agencyRepository,
            GrowerRepository growerRepository,
            SecurityUtils securityUtils, CurrentAgencyProvider currentAgencyProvider,
            ControlRecordService controlRecordService, RemissionConsecutiveRepository consecutives,
            RemittanceRepository remittances, ChangeHistoryService history, AnnulmentLogService annulmentLog) {
        this.annulmentLog = annulmentLog;
        this.controlRecordService = controlRecordService;
        this.consecutives = consecutives;
        this.remissionRepository = remissionRepository;
        this.remissionLineRepository = remissionLineRepository;
        this.inventoryMovementRepository = inventoryMovementRepository;
        this.agencyRepository = agencyRepository;
        this.growerRepository = growerRepository;
        this.securityUtils = securityUtils;
        this.currentAgencyProvider = currentAgencyProvider;
        this.remittances = remittances;
        this.history = history;
    }

    /**
     * Demo del 23-nov (respuestas del area): cada factura sale completa, la remision genera su remesa con el mismo
     * numero (PREFIJO-CONSECUTIVO) y registra la salida de empaques, como Salidas + Genera Plano en Access.
     */
    @Override
    @Transactional
    public RemissionResponse create(RemissionRequest request) {
        Agency agency = agencyRepository.findById(currentAgencyProvider.currentAgencyId())
                .orElseThrow(() -> new ResourceNotFoundException("Agencia no encontrada"));
        String packagingType = request.packagingType().trim().toUpperCase();
        if (!RemittanceServiceImpl.PACKAGING_TYPES.contains(packagingType)) {
            throw new BusinessRuleException("El tipo de empaque debe ser NUEVO o USADO");
        }
        List<InventoryMovement> movements = request.lines().stream()
                .map(RemissionLineRequest::inventoryMovementId).distinct()
                .map(id -> inventoryMovementRepository.findById(id)
                        .filter(m -> m.getAgency().getId().equals(agency.getId()))
                        .orElseThrow(() -> new ResourceNotFoundException("Movimiento de inventario no encontrado")))
                .toList();
        Fund fund = requireSingleFund(movements);
        // Las compras de las facturas, bloqueadas: ni otra remision ni una remesa las pueden tomar a la vez.
        Map<AnnulmentModule, List<Long>> purchases = movements.stream().collect(Collectors.groupingBy(
                m -> module(m.getPurchaseModule()), Collectors.mapping(InventoryMovement::getPurchaseId, Collectors.toList())));
        purchases.forEach((module, ids) ->
                RemittanceServiceImpl.check(remittances.lock(module, ids), ids, agency.getId(), fund, null));
        Grower conductor = request.conductorIdNumber() == null || request.conductorIdNumber().isBlank()
                ? null
                : growerRepository.findByIdNumber(request.conductorIdNumber())
                        .orElseThrow(() -> new ResourceNotFoundException("No existe un caficultor con esa cedula"));
        ControlRecord controlRecord = controlRecordService.getActive(agency.getId());

        Remission remission = new Remission();
        Integer maxUsed = remissionRepository.findMaxRemissionNumber(agency.getId());
        remission.setRemissionNumber(maxUsed == null ? 1 : maxUsed + 1);
        int sequence = consecutives.take(agency.getId());
        String number = displayNumber(controlRecord.getPrefix(), sequence);
        remission.setFund(fund);
        remission.setSequenceNumber(sequence);
        remission.setDisplayNumber(number);
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
        List<RemissionLine> lines = movements.stream()
                .map(movement -> buildLine(savedRemission, movement, balances))
                .toList();
        remissionLineRepository.saveAll(lines);

        List<String> specials = movements.stream().map(InventoryMovement::getSpecialType).distinct().toList();
        Long remittanceId = remittances.insert(agency.getId(), fund.getId(), sequence, number,
                specials.size() == 1 ? specials.get(0) : null, request.packagingQuantity(), packagingType,
                savedRemission.getCreatedByUserId(), savedRemission.getId());
        purchases.forEach((module, ids) -> remittances.assign(module, ids, remittanceId));
        remittances.addPackagingExit(agency.getId(), number, packagingType, controlRecord.getTrustedId(),
                request.packagingQuantity());
        return toResponse(savedRemission, lines, remittanceId);
    }

    /**
     * Anular Remision (demo del 23-nov): queda ANULADA (no se borra), vuelve el saldo de sus facturas, se reversa la
     * salida de empaques y sus facturas salen de la remesa y quedan disponibles otra vez.
     */
    @Override
    @Transactional
    public RemissionResponse annul(Long id, String reason) {
        String checkedReason = AnnulmentReason.require(reason);
        Long agencyId = currentAgencyProvider.currentAgencyId();
        Remission remission = remissionRepository.findForUpdate(id)
                .filter(r -> r.getAgency().getId().equals(agencyId))
                .orElseThrow(() -> new ResourceNotFoundException("No existe esa remisión en la agencia"));
        if (remission.isFromAccess()) {
            // Access no guarda a que facturas pertenece cada salida: no hay a donde devolver los kilos.
            throw new BusinessRuleException("La remisión " + remission.getDisplayNumber()
                    + " es una remisión histórica de Access: no se puede anular.");
        }
        if (Remission.ANULADA.equals(remission.getStatus())) {
            throw new BusinessRuleException("La remisión " + remission.getDisplayNumber() + " ya está anulada");
        }
        // 26-nov: queda en el registro de anulaciones con kilos, valor, destino, conductor, fondo y facturas.
        annulmentLog.recordRemission(id, checkedReason);
        List<RemissionLine> lines = remissionLineRepository.findByRemissionId(id);
        for (RemissionLine line : lines) {
            InventoryMovement movement = line.getInventoryMovement();
            movement.setRemainingKg(movement.getRemainingKg().add(line.getQuantity()));
            inventoryMovementRepository.save(movement);
        }
        Long userId = securityUtils.getCurrentUserId();
        remittances.idByRemission(id).ifPresent(remittanceId ->
                remittances.unmark(agencyId, remittanceId, remission.getDisplayNumber(), userId));
        remission.setStatus(Remission.ANULADA);
        remission.setAnnulledByUserId(userId);
        remission.setAnnulledAt(Instant.now());
        remissionRepository.save(remission);
        history.record(ChangeHistoryService.Entity.REMISION, id, ChangeHistoryService.Action.ANULAR, agencyId,
                Map.of("number", remission.getDisplayNumber(), "status", Remission.VIGENTE),
                Map.of("number", remission.getDisplayNumber(), "status", Remission.ANULADA));
        return toResponse(remission, lines, null);
    }

    /** inventarios.modulo_compra -> el modulo de la compra (misma tabla que usa la remesa). */
    private static AnnulmentModule module(InventoryMovement.PurchaseModule purchaseModule) {
        return Stream.of(AnnulmentModule.values())
                .filter(m -> m.inventoryModule().equals(purchaseModule.name())).findFirst().orElseThrow();
    }

    /** En Access cada salida es de un solo Cod_Prod (Sld1 filtra por Formularios!EXITS!Especial) y el
     *  codigo ya trae el fondo: una remision no puede mezclar LF y RP. */
    private static Fund requireSingleFund(List<InventoryMovement> movements) {
        List<Fund> funds = movements.stream().map(m -> m.getProductCode().getFund()).toList();
        if (funds.stream().map(Fund::getId).distinct().count() > 1) {
            throw new BusinessRuleException(
                    "Todas las líneas de una remisión deben ser del mismo fondo; haga una remisión por fondo");
        }
        return funds.get(0);
    }

    /** {prefijo del ControlRecord}-{consecutivo de la agencia}: el mismo numero de la remision y de su remesa. */
    public static String displayNumber(String prefix, int sequence) {
        return prefix + "-" + sequence;
    }

    /** Sale todo el saldo de la factura, con sus sacos y kilos brutos. Cantidad_AfterUpdate: no mas que el saldo
     *  del codigo. */
    private RemissionLine buildLine(Remission remission, InventoryMovement movement, Map<Long, CodeBalance> balances) {
        BigDecimal quantity = movement.getRemainingKg();
        if (quantity.signum() <= 0) {
            throw new BusinessRuleException("La factura " + movement.getInvoiceNumber()
                    + " ya salió en otra remisión; vuelva a consultar");
        }

        // Valor_unitario y % salida = VRUNIT y frpond de la consulta Sld3: promedio exacto del Cod_Prod
        // (entradas - salidas de la agencia, toda la historia), no el costo de la entrada origen.
        CodeBalance balance = balances.computeIfAbsent(
                movement.getProductCode().getId(),
                productCodeId -> codeBalance(remission.getAgency().getId(), productCodeId, remission.getRemissionDate()));
        if (quantity.compareTo(balance.kg) > 0) {
            // Cantidad_AfterUpdate: If Cantidad > Saldo (saldokg de Sld3) Then MsgBox ...
            throw new BusinessRuleException("La salida no puede ser superior al saldo, vuelva a intentarlo (saldo del "
                    + "código " + movement.getProductCode().getCode() + ": " + balance.kg + " kg)");
        }
        BigDecimal unitValue = balance.value.divide(balance.kg, MathContext.DECIMAL64);
        BigDecimal exitPercentage = balance.kgFactor.divide(balance.kg, MathContext.DECIMAL64).setScale(6, RoundingMode.HALF_UP);
        // Vr_Salida = Valor_unitario * Cantidad, guardado en pesos enteros (inventario_migrar.csv: TA0441
        // 19191,19 x 280 = 5.373.533,75 -> 5373534).
        BigDecimal outputValue = unitValue.multiply(quantity).setScale(0, RoundingMode.HALF_UP).setScale(2);
        balance.subtract(quantity, outputValue, quantity.multiply(exitPercentage));

        movement.setRemainingKg(quantity.subtract(quantity));
        inventoryMovementRepository.save(movement);

        RemissionLine line = new RemissionLine();
        line.setRemission(remission);
        line.setInventoryMovement(movement);
        line.setQuantity(quantity);
        line.setUnitValue(unitValue.setScale(2, RoundingMode.HALF_UP));
        line.setOutputValue(outputValue);
        line.setExitPercentage(exitPercentage);
        line.setSacos(movement.getSacos());
        line.setGrossKg(movement.getGrossKg());
        return line;
    }

    /** Sld2/Sld3 del Cod_Prod: entradas - salidas hasta la fecha de la remision, con toda la historia. Access
     *  borraba el año anterior ("eliminainventarioañoanterior"); aca la base no se corta por año (24-nov). */
    private CodeBalance codeBalance(Long agencyId, Long productCodeId, LocalDate remissionDate) {
        CodeTotals entries = inventoryMovementRepository.sumEntries(agencyId, productCodeId, remissionDate);
        CodeTotals exits = remissionLineRepository.sumExits(agencyId, productCodeId, remissionDate);
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
        // Solo las de la agencia de la sesion (la de trabajo del Administrador); una de otra agencia no existe (404).
        Remission remission = remissionRepository.findById(id)
                .filter(r -> r.getAgency().getId().equals(currentAgencyProvider.currentAgencyId()))
                .orElseThrow(() -> new ResourceNotFoundException("Remision no encontrada"));
        return toResponseWithLines(remission);
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

    private RemissionResponse toResponseWithLines(Remission remission) {
        return toResponse(remission, remissionLineRepository.findByRemissionId(remission.getId()),
                remittances.idByRemission(remission.getId()).orElse(null));
    }

    private RemissionResponse toResponse(Remission remission, List<RemissionLine> lines, Long remittanceId) {
        Grower conductor = remission.getConductor();
        return new RemissionResponse(
                remission.getId(),
                remission.getRemissionNumber(),
                remission.getAgency().getId(),
                remission.getAgency().getName(),
                remission.getRemissionDate(),
                remission.getDestination(),
                conductor == null ? null : conductor.getIdNumber(),
                conductor == null ? null : Stream.of(conductor.getFirstName(), conductor.getLastName())
                        .filter(Objects::nonNull).collect(Collectors.joining(" ")),
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
                remission.getDisplayNumber(),
                remission.getStatus(),
                remittanceId,
                remission.isFromAccess());
    }
}
