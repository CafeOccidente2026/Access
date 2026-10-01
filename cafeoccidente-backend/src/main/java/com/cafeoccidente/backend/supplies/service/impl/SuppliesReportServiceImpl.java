package com.cafeoccidente.backend.supplies.service.impl;

import static com.cafeoccidente.backend.supplies.service.impl.SuppliesEntryServiceImpl.CASH;
import static com.cafeoccidente.backend.supplies.service.impl.SuppliesEntryServiceImpl.CHECK;

import com.cafeoccidente.backend.supplies.dto.SuppliesReportRow;
import com.cafeoccidente.backend.supplies.entity.CheckRelationMark;
import com.cafeoccidente.backend.supplies.entity.LedgerEntry;
import com.cafeoccidente.backend.supplies.entity.PackagingEntry;
import com.cafeoccidente.backend.supplies.repository.CashEntryRepository;
import com.cafeoccidente.backend.supplies.repository.CheckRelationMarkRepository;
import com.cafeoccidente.backend.supplies.repository.GrowerName;
import com.cafeoccidente.backend.supplies.repository.PackagingEntryRepository;
import com.cafeoccidente.backend.supplies.repository.PettyCashEntryRepository;
import com.cafeoccidente.backend.supplies.repository.PurchasePayment;
import com.cafeoccidente.backend.supplies.repository.SuppliesReadRepository;
import com.cafeoccidente.backend.supplies.repository.SupplyEntryRepository;
import com.cafeoccidente.backend.supplies.service.SuppliesReportService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Access guardaba copias: cada compra se insertaba en Caja (ActualizaCaja/Ch/Dat/Tx) y, si era con
 * cheque, en Suministros (ActualizaSuministros); "Caja suministros" con cheque se copiaba a
 * Suministros (Efectivo con cheques) y quedaba EFECTIVO en Caja (CAMBIO A EFECTIVO); "Cheques
 * Girados" se copiaba a Caja (Cheques girados a caja). Aca no se copia nada: {@link #cashBook} y
 * {@link #supplyBook} arman al leer la misma Caja y el mismo Suministros que veia Access.
 *
 * Corte por año: al cerrar el año Access borraba Caja, CajaMenor, Empaque y Suministros del año
 * anterior (eliminacajaañoanterior, etc.), asi que los informes de movimiento arrancan el 1 de enero.
 */
@Service
public class SuppliesReportServiceImpl implements SuppliesReportService {

    static final String CASH_SOURCE = "CASH";
    static final String SUPPLY_SOURCE = "SUPPLY";

    /** Una fila de la Caja / Suministros de Access, venga de donde venga. */
    record Line(String source, Long sourceId, String agencyName, String transactionId, String fundCode,
            LocalDate date, String idNumber, String detail, BigDecimal inflow, BigDecimal outflow,
            String paymentMethod, Integer checkNumber, Instant createdAt) {

        CheckRelationMark.Key key() {
            return new CheckRelationMark.Key(source, sourceId);
        }
    }

    /** Orden de los informes de movimiento (Access ordena por Id = orden de insercion). */
    private static final Comparator<Line> BY_DATE = Comparator.comparing(Line::date)
            .thenComparing(Line::createdAt, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(Line::sourceId);

    private final CashEntryRepository cashEntryRepository;
    private final PettyCashEntryRepository pettyCashEntryRepository;
    private final SupplyEntryRepository supplyEntryRepository;
    private final PackagingEntryRepository packagingEntryRepository;
    private final CheckRelationMarkRepository checkRelationMarkRepository;
    private final SuppliesReadRepository suppliesReadRepository;

    public SuppliesReportServiceImpl(
            CashEntryRepository cashEntryRepository,
            PettyCashEntryRepository pettyCashEntryRepository,
            SupplyEntryRepository supplyEntryRepository,
            PackagingEntryRepository packagingEntryRepository,
            CheckRelationMarkRepository checkRelationMarkRepository,
            SuppliesReadRepository suppliesReadRepository) {
        this.cashEntryRepository = cashEntryRepository;
        this.pettyCashEntryRepository = pettyCashEntryRepository;
        this.supplyEntryRepository = supplyEntryRepository;
        this.packagingEntryRepository = packagingEntryRepository;
        this.checkRelationMarkRepository = checkRelationMarkRepository;
        this.suppliesReadRepository = suppliesReadRepository;
    }

    @Override
    public List<SuppliesReportRow> cashMovement(Long agencyId, String fundCode, LocalDate today) {
        String fund = likePrefix(fundCode);
        return withBalance(cashBook(agencyId, yearStart(today), yearEnd(today)).stream()
                .filter(l -> CASH.equals(l.paymentMethod()) && l.fundCode().startsWith(fund)));
    }

    @Override
    public List<SuppliesReportRow> pettyCashMovement(Long agencyId, LocalDate today) {
        return withBalance(pettyCashEntryRepository.findInPeriod(agencyId, yearStart(today), yearEnd(today)).stream()
                .map(e -> line("PETTY", e, e.getInflow(), e.getOutflow(), e.getPaymentMethod()))
                .filter(l -> CASH.equals(l.paymentMethod())));
    }

    @Override
    public List<SuppliesReportRow> supplies(Long agencyId, String fundCode, LocalDate today) {
        String fund = likePrefix(fundCode);
        return withBalance(supplyBook(agencyId, yearStart(today), yearEnd(today)).stream()
                .filter(l -> l.fundCode().equals(fund)));
    }

    @Override
    public List<SuppliesReportRow> packaging(Long agencyId, String packagingType, LocalDate today) {
        String type = likePrefix(packagingType);
        return withBalance(packagingEntryRepository.findInPeriod(agencyId, yearStart(today), yearEnd(today)).stream()
                .filter(e -> e.getPackagingType().startsWith(type))
                .map(SuppliesReportServiceImpl::line));
    }

    @Override
    @Transactional
    public List<SuppliesReportRow> checkRelation(Long agencyId, LocalDate from, LocalDate to, LocalDate today) {
        List<Line> checks = cashBook(agencyId, yearStart(today), yearEnd(today)).stream()
                .filter(l -> l.checkNumber() != null && l.checkNumber() > 0)
                .toList();
        if (from != null && to != null) {
            // UnCheckCheq: SET Rel_cheques = 0 WHERE Fecha Between FechaDesde And FechaHasta
            checkRelationMarkRepository.deleteAllById(checks.stream()
                    .filter(l -> !l.date().isBefore(from) && !l.date().isAfter(to))
                    .map(Line::key)
                    .toList());
            checkRelationMarkRepository.flush();
        }
        Set<CheckRelationMark.Key> marked = new HashSet<>();
        checkRelationMarkRepository.findAllById(checks.stream().map(Line::key).toList())
                .forEach(m -> marked.add(new CheckRelationMark.Key(m.getSource(), m.getSourceId())));
        List<Line> pending = checks.stream().filter(l -> !marked.contains(l.key())).toList();

        // Rel cheq caja: al cerrar el informe Access marca todo como relacionado.
        checkRelationMarkRepository.saveAll(pending.stream()
                .map(l -> new CheckRelationMark(l.source(), l.sourceId()))
                .toList());

        Map<String, GrowerName> growers = growers(pending);
        return pending.stream()
                .filter(l -> growers.containsKey(l.idNumber())) // INNER JOIN Asociados
                .sorted(Comparator.comparing(Line::agencyName)
                        .thenComparing(Line::createdAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(Line::sourceId))
                .map(l -> row(l, growers.get(l.idNumber()), null))
                .toList();
    }

    @Override
    public List<SuppliesReportRow> paymentMethods(Long agencyId, LocalDate from, LocalDate to, String method) {
        String wanted = method == null || method.isBlank() || "*".equals(method.trim()) ? null : method.trim().toUpperCase();
        // FormaPago: Caja.Fecha Between [FechaDesde] And [FechaHasta]+1 (incluye tambien el dia siguiente)
        List<Line> lines = cashBook(agencyId, from, to.plusDays(1)).stream()
                .filter(l -> wanted == null || wanted.equals(l.paymentMethod()))
                .toList();
        Map<String, GrowerName> growers = growers(lines);
        return lines.stream()
                .filter(l -> growers.containsKey(l.idNumber())) // INNER JOIN Asociados
                .sorted(Comparator.comparing(Line::agencyName).thenComparing(Line::transactionId))
                .map(l -> {
                    GrowerName g = growers.get(l.idNumber());
                    // FormaPago: Detalle = [1er Nombre] & " " & [2o Nombre] & " " & [1er Apellido] & " " & [2o Apellido]
                    String fullName = (g.firstNames() + " " + g.lastNames()).replaceAll("\\s+", " ").trim();
                    return row(l, g, null, fullName);
                })
                .toList();
    }

    /** Tabla Caja de Access. */
    List<Line> cashBook(Long agencyId, LocalDate from, LocalDate to) {
        List<Line> lines = new ArrayList<>();
        cashEntryRepository.findInPeriod(agencyId, from, to).forEach(e ->
                // CAMBIO A EFECTIVO: UPDATE Caja SET Forma_de_Pago = "EFECTIVO" WHERE Entradas > 0
                lines.add(line(CASH_SOURCE, e, e.getInflow(), e.getOutflow(),
                        e.getInflow().signum() > 0 ? CASH : e.getPaymentMethod())));
        supplyEntryRepository.findInPeriod(agencyId, from, to).stream()
                .filter(e -> CHECK.equals(e.getPaymentMethod()))
                // Cheques girados a caja: Salidas = Vr_Gastos_o_Comp
                .forEach(e -> lines.add(line(SUPPLY_SOURCE, e, BigDecimal.ZERO, e.getOutflow(), CHECK)));
        // ActualizaCaja/Ch/Dat/Tx: Id_Transaccion = Factura, Salidas = lo pagado
        copiedPurchasePayments(agencyId, from, to)
                .forEach(p -> lines.add(line(p, BigDecimal.ZERO, p.netToPay())));
        return lines;
    }

    /** ActualizaCaja/Ch/Dat/Tx y ActualizaSuministros solo copian WHERE FPxx > 0: una compra en $0 nunca
     *  llegaba a Caja ni a Suministros. */
    private Stream<PurchasePayment> copiedPurchasePayments(Long agencyId, LocalDate from, LocalDate to) {
        return suppliesReadRepository.purchasePayments(agencyId, from, to).stream()
                .filter(p -> p.netToPay() != null && p.netToPay().signum() > 0);
    }

    /** Tabla Suministros de Access. */
    List<Line> supplyBook(Long agencyId, LocalDate from, LocalDate to) {
        List<Line> lines = new ArrayList<>();
        supplyEntryRepository.findInPeriod(agencyId, from, to)
                .forEach(e -> lines.add(line(SUPPLY_SOURCE, e, e.getInflow(), e.getOutflow(), e.getPaymentMethod())));
        cashEntryRepository.findInPeriod(agencyId, from, to).stream()
                .filter(e -> CHECK.equals(e.getPaymentMethod()))
                // Efectivo con cheques: Vr_Gastos_o_Comp = Caja.Entradas
                .forEach(e -> lines.add(line(CASH_SOURCE, e, BigDecimal.ZERO, e.getInflow(), CHECK)));
        copiedPurchasePayments(agencyId, from, to)
                .filter(p -> CHECK.equals(p.paymentMethod()))
                // ActualizaSuministros: Vr_Gastos_o_Comp = FPch
                .forEach(p -> lines.add(line(p, BigDecimal.ZERO, p.netToPay())));
        return lines;
    }

    private Map<String, GrowerName> growers(List<Line> lines) {
        Set<String> ids = lines.stream().map(Line::idNumber).collect(Collectors.toSet());
        return suppliesReadRepository.growerNames(ids).stream()
                .collect(Collectors.toMap(GrowerName::idNumber, Function.identity(), (a, b) -> a));
    }

    /** RunningSum "Sobre todo" de =[Entradas]-[Salidas]. */
    private static List<SuppliesReportRow> withBalance(Stream<Line> lines) {
        BigDecimal[] balance = {BigDecimal.ZERO};
        return lines.sorted(BY_DATE)
                .map(l -> {
                    balance[0] = balance[0].add(l.inflow()).subtract(l.outflow());
                    return row(l, null, balance[0]);
                })
                .toList();
    }

    private static Line line(String source, LedgerEntry e, BigDecimal inflow, BigDecimal outflow, String method) {
        return new Line(source, e.getId(), e.getAgency().getName(), e.getTransactionId(), e.getFund().getCode(),
                e.getEntryDate(), e.getIdNumber(), e.getDetail(), inflow, outflow, method, e.getCheckNumber(), e.getCreatedAt());
    }

    private static Line line(PackagingEntry e) {
        return new Line("PACKAGING", e.getId(), e.getAgency().getName(), e.getTransactionId(), e.getPackagingType(),
                e.getEntryDate(), e.getIdNumber(), e.getDetail(), BigDecimal.valueOf(e.getInflow()),
                BigDecimal.valueOf(e.getOutflow()), null, null, e.getCreatedAt());
    }

    private static Line line(PurchasePayment p, BigDecimal inflow, BigDecimal outflow) {
        return new Line(p.module(), p.id(), p.agencyName(), String.valueOf(p.invoiceNumber()), p.fundCode(),
                p.purchaseDate(), p.idNumber(), null, inflow, outflow, p.paymentMethod(), p.checkNumber(), p.createdAt());
    }

    private static SuppliesReportRow row(Line l, GrowerName g, BigDecimal balance) {
        return row(l, g, balance, l.detail());
    }

    private static SuppliesReportRow row(Line l, GrowerName g, BigDecimal balance, String detail) {
        return new SuppliesReportRow(l.agencyName(), l.transactionId(), l.fundCode(), l.date(), l.idNumber(),
                g == null ? null : g.firstNames(), g == null ? null : g.lastNames(), detail,
                l.inflow(), l.outflow(), balance, l.paymentMethod(), l.checkNumber());
    }

    /** Criterio LIKE x & "*" de los InputBox de Access; vacio = todo. */
    private static String likePrefix(String text) {
        return text == null ? "" : text.trim().toUpperCase();
    }

    private static LocalDate yearStart(LocalDate today) {
        return today.withDayOfYear(1);
    }

    private static LocalDate yearEnd(LocalDate today) {
        return today.withMonth(12).withDayOfMonth(31);
    }
}
