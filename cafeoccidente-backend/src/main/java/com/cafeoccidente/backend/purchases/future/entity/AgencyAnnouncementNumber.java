package com.cafeoccidente.backend.purchases.future.entity;

import com.cafeoccidente.backend.purchases.shared.entity.Agency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * Numero de anuncio propio de una agencia para un {@link Announcement} maestro (ver
 * docs/diseno-anuncios-compartidos.md). Se crea una fila por cada agencia con ControlRecord activo
 * cada vez que el admin publica un anuncio maestro (fan-out en AnnouncementServiceImpl.create()).
 * El consecutivo es por agencia (UNIQUE agency_id+announcementNumber), igual que el numero de
 * factura; el prefijo a mostrar junto al numero se toma de ControlRecord.prefix de esa agencia, no
 * se duplica aqui.
 */
@Entity
@Getter
@Setter
public class AgencyAnnouncementNumber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "agency_id", nullable = false)
    private Agency agency;

    @ManyToOne
    @JoinColumn(name = "master_announcement_id", nullable = false)
    private Announcement masterAnnouncement;

    @Column(name = "announcement_number", nullable = false)
    private Integer announcementNumber;

    @Column(name = "assigned_at", nullable = false)
    private LocalDate assignedAt;
}
