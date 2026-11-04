package com.cafeoccidente.backend.supplies.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.common.security.SecurityUser;
import com.cafeoccidente.backend.controlrecord.service.ControlRecordService;
import com.cafeoccidente.backend.inventory.service.InventoryMovementService;
import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseRequest;
import com.cafeoccidente.backend.purchases.drycoffee.service.DryCoffeePurchaseService;
import com.cafeoccidente.backend.purchases.shared.dto.PurchasePaymentRequest;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.purchases.shared.service.InvoiceConsecutiveService;
import com.cafeoccidente.backend.users.entity.User;
import com.cafeoccidente.backend.users.repository.RoleRepository;
import com.cafeoccidente.backend.users.repository.UserRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 6e (HU-22): el inventario va en la misma transaccion de la compra. Si no se puede registrar, la
 * compra entera se revierte: no queda compra, ni forma de pago, ni cuenta por cobrar, ni numero de
 * factura consumido. Lo mismo si el pago no cuadra con el neto. Sin @Transactional para ver el rollback real; deja la base como estaba.
 */
@SpringBootTest
@SuppressWarnings("null")
class PurchaseInventoryRollbackIntegrationTest {

    private static final String SELLER = "990000666";
    private static final long AGENCY = 1L;

    @Autowired
    private DryCoffeePurchaseService dry;
    @Autowired
    private InvoiceConsecutiveService consecutive;
    @Autowired
    private ControlRecordService controlRecords;
    @Autowired
    private UserRepository users;
    @Autowired
    private RoleRepository roles;
    @Autowired
    private AgencyRepository agencies;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoBean
    private InventoryMovementService inventory;

    private final List<Runnable> cleanup = new ArrayList<>();
    private Long rp;

    @BeforeEach
    void committedFixtures() {
        User user = new User();
        user.setUsername("prueba_6e_rollback");
        user.setPasswordHash("x");
        user.setRole(roles.findByName(Roles.ADMINISTRADOR).orElseThrow());
        user.setAgency(agencies.findById(AGENCY).orElseThrow());
        User saved = users.save(user);
        cleanup.add(() -> jdbc.update("DELETE FROM usuarios WHERE id = ?", saved.getId()));
        SecurityUser principal = new SecurityUser(saved);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        rp = jdbc.queryForObject("SELECT id FROM fondos WHERE codigo = 'RP'", Long.class);
        Long announcement = jdbc.queryForObject("INSERT INTO anuncios (fecha_anuncio, pr_base_cps, pr_alm_defec,"
                + " sobrepr_carga_esp, pr_inc_calidad, fondo_id, especial, activo) VALUES (CURRENT_DATE, 1546900, 6000, 80000,"
                + " 50, ?, 'RN', true) RETURNING id", Long.class, rp);
        jdbc.update("INSERT INTO anuncios_agencias (agencia_id, anuncio_id, numero_anuncio, fecha_asignacion)"
                + " SELECT ?, ?, COALESCE(MAX(numero_anuncio), 0) + 1, CURRENT_DATE FROM anuncios_agencias WHERE agencia_id = ?",
                AGENCY, announcement, AGENCY);
        cleanup.add(() -> jdbc.update("DELETE FROM anuncios WHERE id = ?", announcement));
        cleanup.add(() -> jdbc.update("DELETE FROM anuncios_agencias WHERE anuncio_id = ?", announcement));
        jdbc.update("INSERT INTO terceros (identificacion, primer_nombre, primer_apellido, agencia_id, direccion, telefono, tipo,"
                + " asociacion) VALUES (?, 'PRUEBA', 'ROLLBACK', ?, 'VEREDA', '3001112233', 'C', false)", SELLER, AGENCY);
        cleanup.add(() -> jdbc.update("DELETE FROM terceros WHERE identificacion = ?", SELLER));
    }

    @AfterEach
    void restore() {
        SecurityContextHolder.clearContext();
        for (int i = cleanup.size() - 1; i >= 0; i--) {
            cleanup.get(i).run();
        }
    }

    @Test
    void aPaymentThatDoesNotMatchTheNetTakesNoInvoice() {
        DryCoffeePurchaseRequest preview = request(null);
        BigDecimal net = dry.preview(preview).netToPay();
        int next = consecutive.next(AGENCY, controlRecords.getActive(AGENCY)).invoiceNumber();

        for (BigDecimal cash : new BigDecimal[] {net.add(BigDecimal.ONE), net.subtract(BigDecimal.ONE)}) {
            assertThatThrownBy(() -> dry.create(request(new PurchasePaymentRequest(
                    cash, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null))))
                    .isInstanceOf(BusinessRuleException.class);
        }

        assertThat(jdbc.queryForObject("SELECT count(*) FROM compras_cafe_seco WHERE cedula = ?", Long.class, SELLER)).isZero();
        assertThat(consecutive.next(AGENCY, controlRecords.getActive(AGENCY)).invoiceNumber()).isEqualTo(next);
    }

    private DryCoffeePurchaseRequest request(PurchasePaymentRequest payment) {
        return new DryCoffeePurchaseRequest(rp, "RN", SELLER, null, null, 5, new BigDecimal("1000"), BigDecimal.ZERO,
                new BigDecimal("200"), new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, payment);
    }

    @Test
    void ifTheInventoryFailsNothingOfThePurchaseRemains() {
        doThrow(new IllegalStateException("inventario caido")).when(inventory).recordFromPurchase(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        BigDecimal net = dry.preview(request(null)).netToPay();
        int next = consecutive.next(AGENCY, controlRecords.getActive(AGENCY)).invoiceNumber();
        BigDecimal card = new BigDecimal("1000");

        assertThatThrownBy(() -> dry.create(request(
                new PurchasePaymentRequest(net.subtract(card), BigDecimal.ZERO, BigDecimal.ZERO, card, null))))
                .isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM compras_cafe_seco WHERE cedula = ?", Long.class, SELLER)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cuentas_por_cobrar WHERE cedula_vendedor = ?", Long.class, SELLER))
                .isZero();
        assertThat(consecutive.next(AGENCY, controlRecords.getActive(AGENCY)).invoiceNumber()).isEqualTo(next);
    }
}
