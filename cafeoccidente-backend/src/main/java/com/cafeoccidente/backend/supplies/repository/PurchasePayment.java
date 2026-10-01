package com.cafeoccidente.backend.supplies.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Pago de una compra (Seco/Verde/Pasilla/Otros/Fertifuturo) tal como lo leen los informes. */
public record PurchasePayment(
        String module,
        Long id,
        Integer invoiceNumber,
        Long agencyId,
        String agencyName,
        String fundCode,
        LocalDate purchaseDate,
        String idNumber,
        BigDecimal netToPay,
        String paymentMethod,
        Integer checkNumber,
        Instant createdAt) {

    /**
     * Compra pagada con varias formas (purchase_payment_split): una fila por forma con monto > 0, como
     * ActualizaCaja (FPef) / ActualizaCh (FPch, con NumCheque) / ActualizaTx (FPtx) / ActualizaDat (FPdat).
     */
    public List<PurchasePayment> splitInto(BigDecimal cash, BigDecimal check, BigDecimal transfer, BigDecimal card,
            Integer splitCheckNumber) {
        List<PurchasePayment> parts = new ArrayList<>();
        addPart(parts, cash, "EFECTIVO", null);
        addPart(parts, check, "CHEQUE", splitCheckNumber);
        addPart(parts, transfer, "TRANSFERENCIA", null);
        addPart(parts, card, "DATAFONO", null);
        return parts;
    }

    private void addPart(List<PurchasePayment> parts, BigDecimal amount, String method, Integer check) {
        if (amount != null && amount.signum() > 0) {
            parts.add(new PurchasePayment(module, id, invoiceNumber, agencyId, agencyName, fundCode, purchaseDate,
                    idNumber, amount, method, check, createdAt));
        }
    }
}
