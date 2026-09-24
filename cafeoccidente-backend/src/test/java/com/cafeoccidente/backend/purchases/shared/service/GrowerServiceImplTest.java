package com.cafeoccidente.backend.purchases.shared.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.purchases.shared.dto.GrowerCreateRequest;
import com.cafeoccidente.backend.purchases.shared.dto.GrowerResponse;
import com.cafeoccidente.backend.purchases.shared.entity.Agency;
import com.cafeoccidente.backend.purchases.shared.entity.Grower;
import com.cafeoccidente.backend.purchases.shared.entity.LegacyNessProgram;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.purchases.shared.repository.GrowerRepository;
import com.cafeoccidente.backend.purchases.shared.repository.LegacyNessProgramRepository;
import com.cafeoccidente.backend.purchases.shared.service.impl.GrowerServiceImpl;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Item C (cupo) - ver GrowerService.checkQuota y Form_COMPRAS.bas lineas 412/465/518/571/624. */
class GrowerServiceImplTest {

    private final GrowerRepository growerRepository = mock(GrowerRepository.class);
    private final LegacyNessProgramRepository legacyNessProgramRepository = mock(LegacyNessProgramRepository.class);
    private final AgencyRepository agencyRepository = mock(AgencyRepository.class);
    private final GrowerService growerService =
            new GrowerServiceImpl(growerRepository, legacyNessProgramRepository, agencyRepository);

    /** LegacyNessProgram es una entidad de staging de solo lectura (solo @Getter, sin setters) -
     *  se instancia real y se llenan los campos por reflexion en vez de mockearla. */
    private static LegacyNessProgram program(String programa, String cupo) {
        LegacyNessProgram program = new LegacyNessProgram();
        ReflectionTestUtils.setField(program, "programa", programa);
        ReflectionTestUtils.setField(program, "cupo", cupo);
        return program;
    }

    @Test
    void allowsPurchaseWithinQuota() {
        when(legacyNessProgramRepository.findByNormalizedCedula(anyString()))
                .thenReturn(List.of(program("REGIONAL NARIÑO 4C", "500.0")));

        growerService.checkQuota("123456", "REGIONAL NARIÑO 4C", new BigDecimal("499.99"));
        growerService.checkQuota("123456", "REGIONAL NARIÑO 4C", new BigDecimal("500"));
        // Ninguna de las dos lanza excepcion - la asercion es que llegamos hasta aca.
        assertThat(true).isTrue();
    }

    @Test
    void rejectsPurchaseThatExceedsQuota() {
        when(legacyNessProgramRepository.findByNormalizedCedula(anyString()))
                .thenReturn(List.of(program("REGIONAL NARIÑO 4C", "500.0")));

        assertThatThrownBy(() -> growerService.checkQuota("123456", "REGIONAL NARIÑO 4C", new BigDecimal("500.01")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("CUPO");
    }

    @Test
    void doesNotBlockWhenGrowerHasNoMigratedProgramData() {
        when(legacyNessProgramRepository.findByNormalizedCedula(anyString())).thenReturn(List.of());

        growerService.checkQuota("999999", "REGIONAL NARIÑO 4C", new BigDecimal("999999"));
        assertThat(true).isTrue();
    }

    @Test
    void doesNotBlockWhenSpecialTypeIsUnmappedAndTheGrowerHasMultiplePrograms() {
        // "ESTANDAR" no esta en SPECIAL_TO_PROGRAMA: sin Especial para desambiguar, y el caficultor
        // esta en mas de un programa a la vez, no hay match seguro - mismo criterio que findProgram.
        when(legacyNessProgramRepository.findByNormalizedCedula(anyString()))
                .thenReturn(List.of(program("REGIONAL NARIÑO 4C", "1"), program("NESPRESSO", "1")));

        growerService.checkQuota("123456", "ESTANDAR", new BigDecimal("999999"));
        assertThat(true).isTrue();
    }

    @Test
    void requireProgramMembershipRejectsGrowerNotInAMappedProgram() {
        // Form_COMPRAS A FUTURO.bas, Texto34_AfterUpdate: Id_NessOcci=0 bloquea.
        when(legacyNessProgramRepository.findByNormalizedCedula(anyString())).thenReturn(List.of());

        assertThatThrownBy(() -> growerService.requireProgramMembership("123456", "REGIONAL NARIÑO 4C"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("NO PERTENECE A NINGUN PROGRAMA");
    }

    @Test
    void requireProgramMembershipAllowsGrowerInTheMatchingProgram() {
        when(legacyNessProgramRepository.findByNormalizedCedula(anyString()))
                .thenReturn(List.of(program("REGIONAL NARIÑO 4C", "500.0")));

        growerService.requireProgramMembership("123456", "REGIONAL NARIÑO 4C");
        assertThat(true).isTrue();
    }

    @Test
    void requireProgramMembershipDoesNotBlockSpecialTypesOutsideTheMigratedCoverage() {
        // "rain"/"te"/etc del VBA real de Compras a Futuro no estan en SPECIAL_TO_PROGRAMA todavia
        // (sin tabla de programa migrada) - no se puede bloquear sin evidencia real.
        when(legacyNessProgramRepository.findByNormalizedCedula(anyString())).thenReturn(List.of());

        growerService.requireProgramMembership("123456", "rain");
        assertThat(true).isTrue();
    }

    @Test
    void createConductorRejectsDuplicateIdNumber() {
        when(growerRepository.findByIdNumber("123456")).thenReturn(Optional.of(new Grower()));

        GrowerCreateRequest request =
                new GrowerCreateRequest("123456", "Juan", null, "Perez", null, 1L, "Coop", "ABC123");

        assertThatThrownBy(() -> growerService.createConductor(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Ya existe");
    }

    @Test
    void createConductorDefaultsGrowerTypeToNoAsociado() {
        when(growerRepository.findByIdNumber("999888")).thenReturn(Optional.empty());
        Agency agency = new Agency();
        agency.setId(1L);
        when(agencyRepository.findById(1L)).thenReturn(Optional.of(agency));
        when(growerRepository.save(org.mockito.ArgumentMatchers.any(Grower.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        GrowerCreateRequest request =
                new GrowerCreateRequest("999888", "Ana", null, "Gomez", null, 1L, "Coop", "XYZ987");
        GrowerResponse response = growerService.createConductor(request);

        assertThat(response.growerType()).isEqualTo("C");
        assertThat(response.transportCompany()).isEqualTo("Coop");
        assertThat(response.vehiclePlate()).isEqualTo("XYZ987");
    }
}
