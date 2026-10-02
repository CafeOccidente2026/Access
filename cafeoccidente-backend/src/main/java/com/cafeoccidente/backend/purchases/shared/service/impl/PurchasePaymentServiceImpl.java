package com.cafeoccidente.backend.purchases.shared.service.impl;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.purchases.shared.dto.PurchasePaymentRequest;
import com.cafeoccidente.backend.purchases.shared.service.PurchasePaymentService;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PurchasePaymentServiceImpl implements PurchasePaymentService {

    static final String UNBALANCED = "FORMA DE PAGO DESCUADRADA, REVISE";
    static final String CHECK_NUMBER_REQUIRED = "Digite el numero de cheque";

    private final JdbcTemplate jdbc;

    public PurchasePaymentServiceImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ResolvedPayment validate(PurchasePaymentRequest payment, BigDecimal netToPay) {
        if (payment == null) {
            throw new BusinessRuleException(UNBALANCED);
        }
        Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        amounts.put("EFECTIVO", payment.cashAmount());
        amounts.put("CHEQUE", payment.checkAmount());
        amounts.put("TRANSFERENCIA", payment.transferAmount());
        amounts.put("DATAFONO", payment.cardAmount());
        BigDecimal total = amounts.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        // Texto206 = FPef + FPch + FPtx + FPdat tiene que ser igual al Neto a Pagar para poder imprimir.
        if (total.compareTo(netToPay) != 0) {
            throw new BusinessRuleException(UNBALANCED);
        }
        String checkNumber = null;
        if (payment.checkAmount().signum() > 0) {
            // Access no lo exige, pero las 322 compras reales con cheque de El Tambo lo tienen (decision
            // del usuario 2026-10-02). Numerico como Compras.NumCheque y Caja.Cheque.
            checkNumber = payment.checkNumber() == null ? "" : payment.checkNumber().trim();
            if (!checkNumber.matches("[0-9]{1,9}") || Integer.parseInt(checkNumber) == 0) {
                throw new BusinessRuleException(CHECK_NUMBER_REQUIRED);
            }
        }
        long used = amounts.values().stream().filter(a -> a.signum() > 0).count();
        String method = used > 1 ? "MIXTO"
                : amounts.entrySet().stream().filter(e -> e.getValue().signum() > 0).map(Map.Entry::getKey)
                        .findFirst().orElse("EFECTIVO");
        return new ResolvedPayment(method, checkNumber);
    }

    @Override
    public void record(String source, Long purchaseId, PurchasePaymentRequest payment) {
        String checkNumber = payment.checkAmount().signum() > 0 ? payment.checkNumber().trim() : null;
        jdbc.update("INSERT INTO purchase_payment_split (source, source_id, cash_amount, check_amount,"
                        + " transfer_amount, card_amount, check_number) VALUES (?, ?, ?, ?, ?, ?, ?)",
                source, purchaseId, payment.cashAmount(), payment.checkAmount(), payment.transferAmount(),
                payment.cardAmount(), checkNumber == null ? null : Integer.valueOf(checkNumber));
    }
}
