package com.cafeoccidente.backend.supplies.service.impl;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.common.security.CurrentAgencyProvider;
import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import com.cafeoccidente.backend.controlrecord.service.ControlRecordService;
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
import com.cafeoccidente.backend.supplies.repository.TransactionIdRepository;
import com.cafeoccidente.backend.supplies.service.SuppliesEntryService;
import com.cafeoccidente.backend.supplies.service.TransactionCode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Altas de Suministros y Caja Menor (RF-27). El Id de transaccion lo genera el servidor con el prefijo del Registro de
 * Control (RF §2.1, D1) y la fecha es la de Colombia (D8). Todo se valida antes de tomar el Id: un alta rechazada no
 * gasta numero.
 */
@Service
public class SuppliesEntryServiceImpl implements SuppliesEntryService {

    public static final String CASH = "EFECTIVO";
    public static final String CHECK = "CHEQUE";
    public static final String TRANSFER = "TRANSFERENCIA";
    /** Combos de Access: Fondo "LF";"RP", Forma_de_Pago "EFECTIVO";"CHEQUE", Tipo "NUEVO";"USADO". */
    private static final Set<String> FUNDS = Set.of("RP", "LF");
    private static final Set<String> CASH_SUPPLY_METHODS = Set.of(CASH, CHECK);
    private static final Set<String> PACKAGING_TYPES = Set.of("NUEVO", "USADO");
    /** Caja Menor siempre es RP y en efectivo (RN-27.12.1, D6). */
    private static final String PETTY_CASH_FUND = "RP";
    private static final Pattern FOUR_DIGITS = Pattern.compile("[0-9]{4}");

    private final CashEntryRepository cashEntryRepository;
    private final PettyCashEntryRepository pettyCashEntryRepository;
    private final SupplyEntryRepository supplyEntryRepository;
    private final PackagingEntryRepository packagingEntryRepository;
    private final SuppliesReadRepository suppliesReadRepository;
    private final TransactionIdRepository transactionIds;
    private final AgencyRepository agencyRepository;
    private final FundRepository fundRepository;
    private final CurrentAgencyProvider currentAgencyProvider;
    private final ControlRecordService controlRecordService;
    private final SuppliesMapper mapper;
    private final Clock clock;

    /** La agencia de trabajo y su Registro de Control (prefijo y Fiel). */
    private record Point(Agency agency, ControlRecord controlRecord) {
    }

    public SuppliesEntryServiceImpl(
            CashEntryRepository cashEntryRepository,
            PettyCashEntryRepository pettyCashEntryRepository,
            SupplyEntryRepository supplyEntryRepository,
            PackagingEntryRepository packagingEntryRepository,
            SuppliesReadRepository suppliesReadRepository,
            TransactionIdRepository transactionIds,
            AgencyRepository agencyRepository,
            FundRepository fundRepository,
            CurrentAgencyProvider currentAgencyProvider,
            ControlRecordService controlRecordService,
            SuppliesMapper mapper,
            Clock clock) {
        this.cashEntryRepository = cashEntryRepository;
        this.pettyCashEntryRepository = pettyCashEntryRepository;
        this.supplyEntryRepository = supplyEntryRepository;
        this.packagingEntryRepository = packagingEntryRepository;
        this.suppliesReadRepository = suppliesReadRepository;
        this.transactionIds = transactionIds;
        this.agencyRepository = agencyRepository;
        this.fundRepository = fundRepository;
        this.currentAgencyProvider = currentAgencyProvider;
        this.controlRecordService = controlRecordService;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public String nextTransactionId(String screen, String value) {
        Point point = point();
        TransactionCode code = TransactionCode.of(screen, value == null ? null : value.trim().toUpperCase());
        return format(point, code, transactionIds.peek(point.agency().getId(), code.code()));
    }

    @Override
    @Transactional
    public EntryResponse createCashSupply(LedgerEntryRequest request) {
        String method = oneOf(request.paymentMethod(), CASH_SUPPLY_METHODS, "Forma de Pago");
        String check = checkNumber(request);
        // Access pasa la entrada a Suministros al actualizar el Cheque (Cheque_AfterUpdate): sin
        // numero de cheque una entrada con CHEQUE nunca llegaria a Suministros.
        if (CHECK.equals(method) && check == null) {
            throw new BusinessRuleException("Con forma de pago CHEQUE debe indicar el número de cheque");
        }
        // D9: con cheque pasa por la Relacion Cheques, que cruza con TERCEROS.
        String idNumber = CHECK.equals(method) ? registered(request.idNumber()).idNumber() : required(request.idNumber());
        Fund fund = fund(request.fundCode());
        CashEntry entry = fill(new CashEntry(), request, fund, idNumber, method, check);
        entry.setInflow(request.amount());
        return save(entry, "LF".equals(fund.getCode()) ? TransactionCode.CASH_LF : TransactionCode.CASH_RP);
    }

    @Override
    @Transactional
    public EntryResponse createCashAdjustment(LedgerEntryRequest request) {
        Fund fund = fund(request.fundCode());
        CashEntry entry = fill(new CashEntry(), request, fund, required(request.idNumber()), CASH, null);
        entry.setOutflow(request.amount());
        return save(entry, "LF".equals(fund.getCode()) ? TransactionCode.ADJUSTMENT_LF : TransactionCode.ADJUSTMENT_RP);
    }

    @Override
    @Transactional
    public EntryResponse createPettyCashSupply(LedgerEntryRequest request) {
        // RN-27.12.1.2: la cedula es la del Fiel del Registro de Control; la que mande el cliente se ignora.
        String fiel = point().controlRecord().getTrustedId();
        PettyCashEntry entry = fill(new PettyCashEntry(), request, fund(PETTY_CASH_FUND), fiel, CASH, null);
        entry.setInflow(request.amount());
        return save(entry, TransactionCode.PETTY_CASH_IN);
    }

    @Override
    @Transactional
    public EntryResponse createPettyCashExpense(LedgerEntryRequest request) {
        // RN-27.12.2.1: la persona a quien se paga tiene que estar en TERCEROS.
        String idNumber = registered(request.idNumber()).idNumber();
        PettyCashEntry entry = fill(new PettyCashEntry(), request, fund(PETTY_CASH_FUND), idNumber, CASH, null);
        entry.setOutflow(request.amount());
        return save(entry, TransactionCode.PETTY_CASH_OUT);
    }

    @Override
    @Transactional
    public EntryResponse createSupply(LedgerEntryRequest request) {
        // RN-27.9.3: el fiel tiene que estar en TERCEROS (los usuarios tambien estan, HU-08).
        String idNumber = registered(request.idNumber()).idNumber();
        Fund fund = fund(request.fundCode());
        SupplyEntry entry = fill(new SupplyEntry(), request, fund, idNumber, null, null);
        // D2 (Q16): unica alta con fecha editable, como Access; vacia es la de hoy.
        if (request.entryDate() != null) {
            entry.setEntryDate(request.entryDate());
        }
        entry.setInflow(request.amount());
        return save(entry, "LF".equals(fund.getCode()) ? TransactionCode.SUPPLY_LF : TransactionCode.SUPPLY_RP);
    }

    @Override
    @Transactional
    public EntryResponse createIssuedCheck(LedgerEntryRequest request) {
        String check = checkNumber(request);
        if (check == null) {
            throw new BusinessRuleException("El número de cheque es obligatorio");
        }
        // D9: el cheque girado sale en la Relacion Cheques, que cruza con TERCEROS.
        String idNumber = registered(request.idNumber()).idNumber();
        Fund fund = fund(request.fundCode());
        SupplyEntry entry = fill(new SupplyEntry(), request, fund, idNumber, CHECK, check);
        entry.setOutflow(request.amount());
        return save(entry, "LF".equals(fund.getCode()) ? TransactionCode.ISSUED_CHECK_LF : TransactionCode.ISSUED_CHECK_RP);
    }

    @Override
    @Transactional
    public EntryResponse createPackagingEntry(PackagingEntryRequest request) {
        // RN-27.6.3: la cedula del fiel tiene que estar en TERCEROS.
        GrowerName fiel = registered(request.idNumber());
        PackagingEntry entry = packaging(request);
        entry.setInflow(request.quantity());
        boolean used = "USADO".equals(entry.getPackagingType());
        return save(entry, used ? TransactionCode.PACKAGING_USED : TransactionCode.PACKAGING_NEW, fiel);
    }

    @Override
    @Transactional
    public EntryResponse createPackagingLoan(PackagingEntryRequest request) {
        // RN-27.7.3: la cedula del caficultor tiene que estar en TERCEROS.
        GrowerName grower = registered(request.idNumber());
        PackagingEntry entry = packaging(request);
        entry.setOutflow(request.quantity());
        boolean used = "USADO".equals(entry.getPackagingType());
        return save(entry, used ? TransactionCode.LOAN_USED : TransactionCode.LOAN_NEW, grower);
    }

    private <T extends LedgerEntry> T fill(
            T entry, LedgerEntryRequest request, Fund fund, String idNumber, String method, String checkNumber) {
        entry.setAgency(point().agency());
        entry.setFund(fund);
        entry.setEntryDate(LocalDate.now(clock));
        entry.setIdNumber(idNumber);
        entry.setDetail(blankToNull(request.detail()));
        entry.setInflow(BigDecimal.ZERO);
        entry.setOutflow(BigDecimal.ZERO);
        entry.setPaymentMethod(method);
        entry.setCheckNumber(checkNumber);
        entry.setCreatedAt(Instant.now());
        return entry;
    }

    private PackagingEntry packaging(PackagingEntryRequest request) {
        PackagingEntry entry = new PackagingEntry();
        entry.setAgency(point().agency());
        entry.setPackagingType(oneOf(request.packagingType(), PACKAGING_TYPES, "Tipo de empaque"));
        entry.setEntryDate(LocalDate.now(clock));
        entry.setIdNumber(request.idNumber());
        entry.setDetail(blankToNull(request.detail()));
        entry.setCreatedAt(Instant.now());
        return entry;
    }

    /** Toma el Id dentro de la transaccion del guardado: si algo falla despues, el numero no se gasta. */
    private EntryResponse save(LedgerEntry entry, TransactionCode code) {
        entry.setTransactionId(take(code));
        if (entry instanceof CashEntry cash) {
            return mapper.toResponse(cashEntryRepository.save(cash));
        }
        if (entry instanceof PettyCashEntry pettyCash) {
            return mapper.toResponse(pettyCashEntryRepository.save(pettyCash));
        }
        return mapper.toResponse(supplyEntryRepository.save((SupplyEntry) entry));
    }

    private EntryResponse save(PackagingEntry entry, TransactionCode code, GrowerName grower) {
        entry.setTransactionId(take(code));
        return mapper.toResponse(packagingEntryRepository.save(entry), grower);
    }

    private String take(TransactionCode code) {
        Point point = point();
        return format(point, code, transactionIds.take(point.agency().getId(), code.code()));
    }

    /** PREFIJO + codigo + consecutivo de 3 cifras, que sigue creciendo despues de 999 (SDBU5SUM1000). */
    private static String format(Point point, TransactionCode code, int consecutive) {
        return point.controlRecord().getPrefix() + code.code() + String.format("%03d", consecutive);
    }

    /**
     * Agencia de trabajo (RF-05.1): nunca la que mande el cliente. Sin Registro de Control la agencia no
     * opera todavia (fase 3, C6): getActive avisa que falta.
     */
    private Point point() {
        Long agencyId = currentAgencyProvider.currentAgencyId();
        ControlRecord controlRecord = controlRecordService.getActive(agencyId);
        Agency agency = agencyRepository.findById(agencyId)
                .orElseThrow(() -> new ResourceNotFoundException("Agencia no encontrada"));
        return new Point(agency, controlRecord);
    }

    private Fund fund(String code) {
        return fundRepository.findByCode(oneOf(code, FUNDS, "Fondo"))
                .orElseThrow(() -> new ResourceNotFoundException("Fondo no encontrado"));
    }

    /** La cedula tiene que estar en TERCEROS (D9). */
    private GrowerName registered(String idNumber) {
        String id = required(idNumber);
        List<GrowerName> growers = suppliesReadRepository.growerNames(List.of(id));
        if (growers.isEmpty()) {
            throw new BusinessRuleException("La cédula " + id + " no está registrada en TERCEROS");
        }
        return growers.get(0);
    }

    private static String required(String idNumber) {
        if (idNumber == null || idNumber.isBlank()) {
            throw new BusinessRuleException("La cédula es obligatoria");
        }
        return idNumber.trim();
    }

    /** RN-27.1.4 y RN-27.10.3: los ultimos 4 digitos, con sus ceros (0548); vacio = sin cheque. */
    private static String checkNumber(LedgerEntryRequest request) {
        String check = blankToNull(request.checkNumber());
        if (check != null && !FOUR_DIGITS.matcher(check).matches()) {
            throw new BusinessRuleException("El cheque son los últimos 4 dígitos");
        }
        return check;
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
