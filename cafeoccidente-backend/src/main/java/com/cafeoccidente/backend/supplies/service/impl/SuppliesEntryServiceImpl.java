package com.cafeoccidente.backend.supplies.service.impl;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.common.security.SecurityUtils;
import com.cafeoccidente.backend.purchases.shared.entity.Agency;
import com.cafeoccidente.backend.purchases.shared.entity.Fund;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.purchases.shared.repository.FundRepository;
import com.cafeoccidente.backend.supplies.dto.EntryResponse;
import com.cafeoccidente.backend.supplies.dto.LedgerEntryRequest;
import com.cafeoccidente.backend.supplies.dto.PackagingEntryRequest;
import com.cafeoccidente.backend.supplies.entity.CashEntry;
import com.cafeoccidente.backend.supplies.entity.LedgerEntry;
import com.cafeoccidente.backend.supplies.entity.PackagingEntry;
import com.cafeoccidente.backend.supplies.entity.PettyCashEntry;
import com.cafeoccidente.backend.supplies.entity.SupplyEntry;
import com.cafeoccidente.backend.supplies.mapper.SuppliesMapper;
import com.cafeoccidente.backend.supplies.repository.CashEntryRepository;
import com.cafeoccidente.backend.supplies.repository.GrowerName;
import com.cafeoccidente.backend.supplies.repository.PackagingEntryRepository;
import com.cafeoccidente.backend.supplies.repository.PettyCashEntryRepository;
import com.cafeoccidente.backend.supplies.repository.SuppliesReadRepository;
import com.cafeoccidente.backend.supplies.repository.SupplyEntryRepository;
import com.cafeoccidente.backend.supplies.service.SuppliesEntryService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SuppliesEntryServiceImpl implements SuppliesEntryService {

    public static final String CASH = "EFECTIVO";
    public static final String CHECK = "CHEQUE";
    /** Combos de Access: Fondo "LF";"RP", Forma_de_Pago "EFECTIVO";"CHEQUE", Tipo "NUEVO";"USADO". */
    private static final Set<String> FUNDS = Set.of("RP", "LF");
    private static final Set<String> CASH_SUPPLY_METHODS = Set.of(CASH, CHECK);
    private static final Set<String> PACKAGING_TYPES = Set.of("NUEVO", "USADO");
    /** Caja Menor y Gastos: Fondo bloqueado en RP (decision del usuario 2026-09-30). */
    private static final String PETTY_CASH_FUND = "RP";

    private final CashEntryRepository cashEntryRepository;
    private final PettyCashEntryRepository pettyCashEntryRepository;
    private final SupplyEntryRepository supplyEntryRepository;
    private final PackagingEntryRepository packagingEntryRepository;
    private final SuppliesReadRepository suppliesReadRepository;
    private final AgencyRepository agencyRepository;
    private final FundRepository fundRepository;
    private final SecurityUtils securityUtils;
    private final SuppliesMapper mapper;

    public SuppliesEntryServiceImpl(
            CashEntryRepository cashEntryRepository,
            PettyCashEntryRepository pettyCashEntryRepository,
            SupplyEntryRepository supplyEntryRepository,
            PackagingEntryRepository packagingEntryRepository,
            SuppliesReadRepository suppliesReadRepository,
            AgencyRepository agencyRepository,
            FundRepository fundRepository,
            SecurityUtils securityUtils,
            SuppliesMapper mapper) {
        this.cashEntryRepository = cashEntryRepository;
        this.pettyCashEntryRepository = pettyCashEntryRepository;
        this.supplyEntryRepository = supplyEntryRepository;
        this.packagingEntryRepository = packagingEntryRepository;
        this.suppliesReadRepository = suppliesReadRepository;
        this.agencyRepository = agencyRepository;
        this.fundRepository = fundRepository;
        this.securityUtils = securityUtils;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public EntryResponse createCashSupply(LedgerEntryRequest request) {
        String method = oneOf(request.paymentMethod(), CASH_SUPPLY_METHODS, "Forma de Pago");
        // Access pasa la entrada a Suministros al actualizar el Cheque (Cheque_AfterUpdate): sin
        // numero de cheque una entrada con CHEQUE nunca llegaria a Suministros.
        if (CHECK.equals(method) && !hasCheck(request)) {
            throw new BusinessRuleException("Con forma de pago CHEQUE debe indicar el número de cheque");
        }
        CashEntry entry = fill(new CashEntry(), request, LocalDate.now(), fund(request.fundCode()), method, request.checkNumber());
        entry.setInflow(request.amount());
        return mapper.toResponse(cashEntryRepository.save(entry));
    }

    @Override
    @Transactional
    public EntryResponse createCashAdjustment(LedgerEntryRequest request) {
        CashEntry entry = fill(new CashEntry(), request, LocalDate.now(), fund(request.fundCode()), CASH, null);
        entry.setOutflow(request.amount());
        return mapper.toResponse(cashEntryRepository.save(entry));
    }

    @Override
    @Transactional
    public EntryResponse createPettyCashSupply(LedgerEntryRequest request) {
        PettyCashEntry entry = fill(new PettyCashEntry(), request, LocalDate.now(), fund(PETTY_CASH_FUND), CASH, null);
        entry.setInflow(request.amount());
        return mapper.toResponse(pettyCashEntryRepository.save(entry));
    }

    @Override
    @Transactional
    public EntryResponse createPettyCashExpense(LedgerEntryRequest request) {
        PettyCashEntry entry = fill(new PettyCashEntry(), request, LocalDate.now(), fund(PETTY_CASH_FUND), CASH, null);
        entry.setOutflow(request.amount());
        return mapper.toResponse(pettyCashEntryRepository.save(entry));
    }

    @Override
    @Transactional
    public EntryResponse createSupply(LedgerEntryRequest request) {
        // Unica pantalla con Fecha editable y sin valor por defecto (Form SUMINISTROS).
        if (request.entryDate() == null) {
            throw new BusinessRuleException("La fecha es obligatoria");
        }
        SupplyEntry entry = fill(new SupplyEntry(), request, request.entryDate(), fund(request.fundCode()), null, null);
        entry.setInflow(request.amount());
        return mapper.toResponse(supplyEntryRepository.save(entry));
    }

    @Override
    @Transactional
    public EntryResponse createIssuedCheck(LedgerEntryRequest request) {
        if (!hasCheck(request)) {
            throw new BusinessRuleException("El número de cheque es obligatorio");
        }
        SupplyEntry entry = fill(new SupplyEntry(), request, LocalDate.now(), fund(request.fundCode()), CHECK, request.checkNumber());
        entry.setOutflow(request.amount());
        return mapper.toResponse(supplyEntryRepository.save(entry));
    }

    @Override
    @Transactional
    public EntryResponse createPackagingEntry(PackagingEntryRequest request) {
        // Fecha editable con Date() por defecto (Form Empaque suministros).
        LocalDate date = request.entryDate() != null ? request.entryDate() : LocalDate.now();
        PackagingEntry entry = packaging(request, date);
        entry.setInflow(request.quantity());
        return mapper.toResponse(packagingEntryRepository.save(entry), null);
    }

    @Override
    @Transactional
    public EntryResponse createPackagingLoan(PackagingEntryRequest request) {
        List<GrowerName> growers = suppliesReadRepository.growerNames(List.of(request.idNumber()));
        if (growers.isEmpty()) {
            throw new BusinessRuleException("La cédula no está registrada como asociado");
        }
        PackagingEntry entry = packaging(request, LocalDate.now());
        entry.setOutflow(request.quantity());
        return mapper.toResponse(packagingEntryRepository.save(entry), growers.get(0));
    }

    private <T extends LedgerEntry> T fill(
            T entry, LedgerEntryRequest request, LocalDate date, Fund fund, String method, Integer checkNumber) {
        entry.setTransactionId(request.transactionId().trim());
        entry.setAgency(agency(request.agencyId()));
        entry.setFund(fund);
        entry.setEntryDate(date);
        entry.setIdNumber(request.idNumber());
        entry.setDetail(blankToNull(request.detail()));
        entry.setInflow(BigDecimal.ZERO);
        entry.setOutflow(BigDecimal.ZERO);
        entry.setPaymentMethod(method);
        entry.setCheckNumber(checkNumber);
        entry.setCreatedAt(Instant.now());
        return entry;
    }

    private PackagingEntry packaging(PackagingEntryRequest request, LocalDate date) {
        PackagingEntry entry = new PackagingEntry();
        entry.setTransactionId(request.transactionId().trim());
        entry.setAgency(agency(request.agencyId()));
        entry.setPackagingType(oneOf(request.packagingType(), PACKAGING_TYPES, "Tipo de empaque"));
        entry.setEntryDate(date);
        entry.setIdNumber(request.idNumber());
        entry.setDetail(blankToNull(request.detail()));
        entry.setCreatedAt(Instant.now());
        return entry;
    }

    private Agency agency(Long requestedAgencyId) {
        Long agencyId = securityUtils.resolveAgencyId(requestedAgencyId);
        if (agencyId == null) {
            throw new BusinessRuleException("Elija una agencia");
        }
        return agencyRepository.findById(agencyId)
                .orElseThrow(() -> new ResourceNotFoundException("Agencia no encontrada"));
    }

    private Fund fund(String code) {
        return fundRepository.findByCode(oneOf(code, FUNDS, "Fondo"))
                .orElseThrow(() -> new ResourceNotFoundException("Fondo no encontrado"));
    }

    private static boolean hasCheck(LedgerEntryRequest request) {
        return request.checkNumber() != null && request.checkNumber() > 0;
    }

    private static String oneOf(String value, Set<String> allowed, String field) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        if (!allowed.contains(normalized)) {
            throw new BusinessRuleException(field + " inválido: use " + String.join(" o ", allowed.stream().sorted().toList()));
        }
        return normalized;
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
