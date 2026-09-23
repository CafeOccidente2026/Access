package com.cafeoccidente.backend.purchases.future.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * Cupo asignado a UN anuncio (pantalla "Asignar Cupo", Access "ACTUALIZA CUPOS"): tope total de
 * kilos que se puede recibir bajo ese anuncio, no un cupo por caficultor (ver
 * {@link com.cafeoccidente.backend.purchases.shared.service.GrowerService#checkQuota} para el cupo
 * por caficultor, que es una fuente de datos distinta - staging_legacy_ness). "Entregados"/"Saldo"
 * no se guardan aqui: se recalculan en cada consulta (ver AnnouncementQuotaServiceImpl), igual que
 * el boton "Verificar Entregados" del VBA original.
 */
@Entity
@Getter
@Setter
public class AnnouncementQuota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "agency_announcement_number_id", nullable = false, unique = true)
    private AgencyAnnouncementNumber agencyAnnouncementNumber;

    @Column(name = "assigned_quota", nullable = false, precision = 10, scale = 2)
    private BigDecimal assignedQuota;
}
