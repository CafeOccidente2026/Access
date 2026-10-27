package com.cafeoccidente.backend.purchases.shared.service.impl;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.history.service.ChangeHistoryService;
import com.cafeoccidente.backend.history.service.ChangeHistoryService.Action;
import com.cafeoccidente.backend.history.service.ChangeHistoryService.Entity;
import com.cafeoccidente.backend.purchases.shared.dto.PurchaseSeller;
import com.cafeoccidente.backend.purchases.shared.entity.Grower;
import com.cafeoccidente.backend.purchases.shared.repository.GrowerRepository;
import com.cafeoccidente.backend.purchases.shared.service.PurchaseSellerService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PurchaseSellerServiceImpl implements PurchaseSellerService {

    static final String NOT_FOUND = "Este número de cédula no existe.";
    static final String DECEASED = "No se le puede facturar a un fallecido.";
    static final String ADDRESS_REQUIRED = "Digite la dirección del vendedor.";
    static final String CELLPHONE_REQUIRED = "Digite el celular del vendedor.";

    private final GrowerRepository growerRepository;
    private final ChangeHistoryService history;

    public PurchaseSellerServiceImpl(GrowerRepository growerRepository, ChangeHistoryService history) {
        this.growerRepository = growerRepository;
        this.history = history;
    }

    @Override
    @Transactional(readOnly = true)
    public PurchaseSeller resolve(String idNumber, String typedAddress, String typedCellphone) {
        return seller(grower(idNumber), typedAddress, typedCellphone);
    }

    @Override
    @Transactional
    public PurchaseSeller resolveAndComplete(String idNumber, String typedAddress, String typedCellphone) {
        Grower grower = grower(idNumber);
        PurchaseSeller seller = seller(grower, typedAddress, typedCellphone);
        Map<String, String> before = new LinkedHashMap<>();
        Map<String, String> after = new LinkedHashMap<>();
        if (isBlank(grower.getAddress())) {
            before.put("address", "");
            after.put("address", seller.address());
            grower.setAddress(seller.address());
        }
        if (isBlank(grower.getPhone())) {
            before.put("phone", "");
            after.put("phone", seller.cellphone());
            grower.setPhone(seller.cellphone());
        }
        if (!after.isEmpty()) {
            grower.setUpdatedAt(Instant.now());
            growerRepository.save(grower);
            history.record(Entity.TERCERO, grower.getId(), Action.MODIFICAR,
                    grower.getAgency() == null ? null : grower.getAgency().getId(), before, after);
        }
        return seller;
    }

    private Grower grower(String idNumber) {
        Grower grower = growerRepository.findByIdNumber(idNumber == null ? "" : idNumber.trim())
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        // Igual que la pantalla (Access): a un fallecido no se le factura.
        if (grower.isDeceased()) {
            throw new BusinessRuleException(DECEASED);
        }
        return grower;
    }

    /** Lo de TERCEROS manda; lo digitado solo llena lo que alla esta vacio. */
    private static PurchaseSeller seller(Grower grower, String typedAddress, String typedCellphone) {
        return new PurchaseSeller(
                grower.getIdNumber(),
                join(grower.getFirstName(), grower.getSecondName()),
                join(grower.getLastName(), grower.getSecondLastName()),
                grower.getGrowerType() == null ? "" : grower.getGrowerType().trim().toUpperCase(),
                pick(grower.getAddress(), typedAddress, ADDRESS_REQUIRED),
                pick(grower.getPhone(), typedCellphone, CELLPHONE_REQUIRED),
                Boolean.TRUE.equals(grower.getIsAssociation()));
    }

    private static String pick(String stored, String typed, String requiredMessage) {
        if (!isBlank(stored)) {
            return stored.trim();
        }
        if (isBlank(typed)) {
            throw new BusinessRuleException(requiredMessage);
        }
        return typed.trim();
    }

    private static String join(String first, String second) {
        return ((first == null ? "" : first.trim()) + " " + (second == null ? "" : second.trim())).trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
