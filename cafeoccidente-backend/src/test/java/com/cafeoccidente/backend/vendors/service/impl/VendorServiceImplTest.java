package com.cafeoccidente.backend.vendors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cafeoccidente.backend.common.config.ClockConfig;
import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import com.cafeoccidente.backend.controlrecord.repository.ControlRecordRepository;
import com.cafeoccidente.backend.purchases.shared.entity.Agency;
import com.cafeoccidente.backend.purchases.shared.entity.Grower;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.purchases.shared.repository.GrowerRepository;
import com.cafeoccidente.backend.vendors.dto.VendorCreateRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class VendorServiceImplTest {

    /** 2026-01-14 20:00 en Colombia (ya 2026-01-15 en UTC). */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-15T01:00:00Z"), ClockConfig.COLOMBIA);

    private final GrowerRepository growers = mock(GrowerRepository.class);
    private final AgencyRepository agencies = mock(AgencyRepository.class);
    private final ControlRecordRepository controlRecords = mock(ControlRecordRepository.class);
    private final VendorServiceImpl service = new VendorServiceImpl(growers, agencies, controlRecords, CLOCK);
    private final Agency agency = new Agency();

    @BeforeEach
    void setUp() {
        agency.setId(4L);
        agency.setName("El Tambo");
        ControlRecord control = new ControlRecord();
        control.setControlNumber(2);
        when(agencies.findById(4L)).thenReturn(Optional.of(agency));
        when(controlRecords.findByAgencyIdAndActiveTrue(4L)).thenReturn(Optional.of(control));
        when(growers.findByIdNumber(any())).thenReturn(Optional.empty());
        when(growers.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private Grower saved() {
        ArgumentCaptor<Grower> captor = ArgumentCaptor.forClass(Grower.class);
        verify(growers).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void legalPersonKeepsBusinessNameClassificationAndContactData() {
        service.create(legal(" 900723205 ", " FUNDACION SUYUSAMA ", "FUNDACION", "M"), 4L);

        Grower g = saved();
        assertThat(g.getIdNumber()).isEqualTo("900723205");
        assertThat(g.getBusinessName()).isEqualTo("FUNDACION SUYUSAMA");
        // Compras y los informes muestran primer_nombre: la razon social va tambien ahi.
        assertThat(g.getFirstName()).isEqualTo("FUNDACION SUYUSAMA");
        assertThat(g.getClassification()).isEqualTo("FUNDACION");
        assertThat(g.getLastName()).isNull();
        assertThat(g.getSecondName()).isNull();
        assertThat(g.getSex()).isEqualTo("M");
        assertThat(g.getPhone()).isEqualTo("3001234567");
        assertThat(g.getPostalCode()).isEqualTo("520001");
        assertThat(g.getEmail()).isEqualTo("info@suyusama.org");
        assertThat(g.getIsAssociation()).isTrue();
        assertThat(g.getAddress()).isEqualTo("CLL 20 24 64");
        assertThat(g.getGrowerType()).isEqualTo("C");
        assertThat(g.getAffiliationDate()).isEqualTo(LocalDate.of(2026, 1, 14));
        assertThat(g.getAgency()).isSameAs(agency);
        assertThat(g.getRegistryNumber()).isEqualTo(2);
        assertThat(g.getExported()).isFalse();
    }

    @Test
    void legalPersonWithoutSexIsE() {
        service.create(legal("900723205", "ASOCIACION ASOPEMA", "ASOCIACION", null), 4L);

        assertThat(saved().getSex()).isEqualTo("E");
    }

    @Test
    void legalPersonNeedsBusinessNameAndClassification() {
        assertThatThrownBy(() -> service.create(legal("900723205", " ", "ASOCIACION", "E"), 4L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Razón Social");
        assertThatThrownBy(() -> service.create(legal("900723205", "ASOPEMA", null, "E"), 4L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Clasificación");
        verify(growers, never()).save(any());
    }

    @Test
    void naturalPersonNeedsFirstName() {
        assertThatThrownBy(() -> service.create(natural("59124124", " "), 4L))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("1er Nombre");
        verify(growers, never()).save(any());
    }

    private static VendorCreateRequest legal(String nit, String businessName, String classification, String sex) {
        return new VendorCreateRequest(true, nit, null, null, null, null, sex, "3001234567", "CLL 20 24 64",
                "520001", "info@suyusama.org", businessName, classification);
    }

    private static VendorCreateRequest natural(String idNumber, String firstName) {
        return new VendorCreateRequest(false, idNumber, firstName, null, null, null, null, null, null, null, null,
                null, null);
    }

    @Test
    void personKeepsAllThirteenFields() {
        service.create(new VendorCreateRequest(false, "59124124", "AURA", "MARIA", "TUTISTAR", " ", "F", "3001234567",
                "VDA LA PALMA", "522060", "aura@correo.co", "IGNORADA", "ASOCIACION"), 4L);

        Grower g = saved();
        assertThat(g.getSecondName()).isEqualTo("MARIA");
        assertThat(g.getLastName()).isEqualTo("TUTISTAR");
        assertThat(g.getSecondLastName()).isNull();
        assertThat(g.getSex()).isEqualTo("F");
        assertThat(g.getPhone()).isEqualTo("3001234567");
        assertThat(g.getPostalCode()).isEqualTo("522060");
        assertThat(g.getEmail()).isEqualTo("aura@correo.co");
        assertThat(g.getIsAssociation()).isFalse();
        assertThat(g.getBusinessName()).isNull();
        assertThat(g.getClassification()).isNull();
        assertThat(g.getAccepted()).isFalse();
        assertThat(g.getEligible()).isFalse();
    }

    @Test
    void duplicateIdNumberIsRejected() {
        when(growers.findByIdNumber("59124124")).thenReturn(Optional.of(new Grower()));

        assertThatThrownBy(() -> service.create(natural("59124124", "AURA"), 4L))
                .isInstanceOf(BusinessRuleException.class);
        verify(growers, never()).save(any());
    }
}
