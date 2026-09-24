package com.cafeoccidente.backend.inventory.service.impl;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.common.security.SecurityUtils;
import com.cafeoccidente.backend.inventory.dto.RemissionLineRequest;
import com.cafeoccidente.backend.inventory.dto.RemissionLineResponse;
import com.cafeoccidente.backend.inventory.dto.RemissionRequest;
import com.cafeoccidente.backend.inventory.dto.RemissionResponse;
import com.cafeoccidente.backend.inventory.entity.InventoryMovement;
import com.cafeoccidente.backend.inventory.entity.Remission;
import com.cafeoccidente.backend.inventory.entity.RemissionLine;
import com.cafeoccidente.backend.inventory.repository.InventoryMovementRepository;
import com.cafeoccidente.backend.inventory.repository.RemissionLineRepository;
import com.cafeoccidente.backend.inventory.repository.RemissionRepository;
import com.cafeoccidente.backend.inventory.service.RemissionService;
import com.cafeoccidente.backend.purchases.shared.entity.Agency;
import com.cafeoccidente.backend.purchases.shared.entity.Grower;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.purchases.shared.repository.GrowerRepository;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
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

    public RemissionServiceImpl(
            RemissionRepository remissionRepository,
            RemissionLineRepository remissionLineRepository,
            InventoryMovementRepository inventoryMovementRepository,
            AgencyRepository agencyRepository,
            GrowerRepository growerRepository,
            SecurityUtils securityUtils) {
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
        Grower conductor = request.conductorIdNumber() == null || request.conductorIdNumber().isBlank()
                ? null
                : growerRepository.findByIdNumber(request.conductorIdNumber())
                        .orElseThrow(() -> new ResourceNotFoundException("No existe un caficultor con esa cedula"));

        Remission remission = new Remission();
        Integer maxUsed = remissionRepository.findMaxRemissionNumber(agency.getId());
        remission.setRemissionNumber(maxUsed == null ? 1 : maxUsed + 1);
        remission.setAgency(agency);
        remission.setRemissionDate(request.remissionDate());
        remission.setDestination(request.destination());
        remission.setConductor(conductor);
        remission.setExported(false);
        remission.setCreatedByUserId(securityUtils.getCurrentUserId());
        remission.setCreatedAt(Instant.now());
        Remission savedRemission = remissionRepository.save(remission);

        List<RemissionLine> lines = request.lines().stream()
                .map(lineRequest -> buildLine(savedRemission, lineRequest))
                .toList();
        remissionLineRepository.saveAll(lines);

        return toResponse(savedRemission, lines);
    }

    /** Cantidad_AfterUpdate: "La salida no puede ser superior al saldo, vuelva a intentarlo". */
    private RemissionLine buildLine(Remission remission, RemissionLineRequest lineRequest) {
        InventoryMovement movement = inventoryMovementRepository.findById(lineRequest.inventoryMovementId())
                .orElseThrow(() -> new ResourceNotFoundException("Movimiento de inventario no encontrado"));
        if (lineRequest.quantity().compareTo(movement.getRemainingKg()) > 0) {
            throw new BusinessRuleException(
                    "La salida no puede ser superior al saldo disponible (saldo: " + movement.getRemainingKg()
                            + " kg, factura " + movement.getInvoiceNumber() + ")");
        }

        // Valor_unitario (ParaSalidas!VRUNIT): unitario de la entrada origen. inventoryValue =
        // unitPrice * netKg en los 5 calculadores de compra, asi que se recupera dividiendo.
        BigDecimal unitValue = movement.getInventoryValue()
                .divide(movement.getNetKg(), MathContext.DECIMAL64)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal outputValue = unitValue.multiply(lineRequest.quantity()).setScale(2, RoundingMode.HALF_UP);

        movement.setRemainingKg(movement.getRemainingKg().subtract(lineRequest.quantity()));
        inventoryMovementRepository.save(movement);

        RemissionLine line = new RemissionLine();
        line.setRemission(remission);
        line.setInventoryMovement(movement);
        line.setQuantity(lineRequest.quantity());
        line.setUnitValue(unitValue);
        line.setOutputValue(outputValue);
        return line;
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
    public Optional<RemissionResponse> findByNumber(Long agencyId, Integer remissionNumber) {
        return remissionRepository
                .findByAgencyIdAndRemissionNumber(agencyId, remissionNumber)
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
                                line.getOutputValue()))
                        .toList());
    }
}
