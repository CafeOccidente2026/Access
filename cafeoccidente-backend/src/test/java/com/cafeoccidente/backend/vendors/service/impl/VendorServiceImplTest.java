package com.cafeoccidente.backend.vendors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import com.cafeoccidente.backend.controlrecord.repository.ControlRecordRepository;
import com.cafeoccidente.backend.purchases.shared.entity.Agency;
import com.cafeoccidente.backend.purchases.shared.entity.Grower;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.purchases.shared.repository.GrowerRepository;
import com.cafeoccidente.backend.vendors.dto.VendorCreateRequest;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class VendorServiceImplTest {

    private final GrowerRepository growers = mock(GrowerRepository.class);
    private final AgencyRepository agencies = mock(AgencyRepository.class);
    private final ControlRecordRepository controlRecords = mock(ControlRecordRepository.class);
    private final VendorServiceImpl service = new VendorServiceImpl(growers, agencies, controlRecords);
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
    void associationKeepsWholeNameInFirstNameWithSexE() {
        service.create(new VendorCreateRequest(true, " 900723205 ", "FUNDACION SUYUSAMA", "X", "Y", "Z", null,
                "M", "300", "CLL 20 24 64", "520001", "a@b.co"), 4L);

        Grower g = saved();
        assertThat(g.getIdNumber()).isEqualTo("900723205");
        assertThat(g.getFirstName()).isEqualTo("FUNDACION SUYUSAMA");
        // La pantalla de 6 items no tiene apellidos, sexo, telefono, postal ni email.
        assertThat(g.getLastName()).isNull();
        assertThat(g.getSecondName()).isNull();
        assertThat(g.getSex()).isEqualTo("E");
        assertThat(g.getPhone()).isEmpty();
        assertThat(g.getPostalCode()).isNull();
        assertThat(g.getEmail()).isNull();
        assertThat(g.getIsAssociation()).isTrue();
        assertThat(g.getAddress()).isEqualTo("CLL 20 24 64");
        assertThat(g.getGrowerType()).isEqualTo("C");
        assertThat(g.getAffiliationDate()).isEqualTo(LocalDate.now());
        assertThat(g.getAgency()).isSameAs(agency);
        assertThat(g.getRegistryNumber()).isEqualTo(2);
        assertThat(g.getExported()).isFalse();
    }

    @Test
    void personKeepsAllThirteenFields() {
        service.create(new VendorCreateRequest(false, "59124124", "AURA", "MARIA", "TUTISTAR", " ", null,
                "F", "3001234567", "VDA LA PALMA", "522060", "aura@correo.co"), 4L);

        Grower g = saved();
        assertThat(g.getSecondName()).isEqualTo("MARIA");
        assertThat(g.getLastName()).isEqualTo("TUTISTAR");
        assertThat(g.getSecondLastName()).isNull();
        assertThat(g.getSex()).isEqualTo("F");
        assertThat(g.getPhone()).isEqualTo("3001234567");
        assertThat(g.getPostalCode()).isEqualTo("522060");
        assertThat(g.getEmail()).isEqualTo("aura@correo.co");
        assertThat(g.getIsAssociation()).isFalse();
        assertThat(g.getAccepted()).isFalse();
        assertThat(g.getEligible()).isFalse();
    }

    @Test
    void duplicateIdNumberIsRejected() {
        when(growers.findByIdNumber("59124124")).thenReturn(Optional.of(new Grower()));

        assertThatThrownBy(() -> service.create(new VendorCreateRequest(false, "59124124", "AURA", null, null,
                null, null, null, null, null, null, null), 4L)).isInstanceOf(BusinessRuleException.class);
        verify(growers, never()).save(any());
    }
}
