package com.cafeoccidente.backend.purchases.shared.service.impl;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.purchases.shared.dto.GrowerProgramResponse;
import com.cafeoccidente.backend.purchases.shared.dto.GrowerResponse;
import com.cafeoccidente.backend.purchases.shared.entity.Grower;
import com.cafeoccidente.backend.purchases.shared.entity.LegacyNessProgram;
import com.cafeoccidente.backend.purchases.shared.repository.GrowerRepository;
import com.cafeoccidente.backend.purchases.shared.repository.LegacyNessProgramRepository;
import com.cafeoccidente.backend.purchases.shared.service.GrowerService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class GrowerServiceImpl implements GrowerService {

    /**
     * Especial (formulario Compras) -> Programa (staging_legacy_ness). Evidencia: Form_COMPRAS.bas,
     * Cuadro_combinado61_AfterUpdate - solo estos 3 Especiales llaman una macro "Abrir NESS/NESSLH/
     * RN4C pcompras" que cruza contra la tabla de programa; el resto de Especiales (~17) no tiene
     * tabla de programa migrada todavia.
     *
     * Form_COMPRAS.bas tiene DOS bloqueos distintos sobre estos mismos Especiales, evidencia
     * separada en el VBA:
     * <ul>
     *   <li><b>Cupo excedido</b> ({@code Kilos_Netos > Texto118}, lineas 412/465/518/571/624):
     *       implementado en {@link #checkQuota} desde 2026-09-23 (Item C).</li>
     *   <li><b>TODO(EnProg)</b>: bloqueo si el caficultor no aparece en su programa (EnProg=0),
     *       para estos mismos Especiales + RAINFOREST/EXPON (lineas 761-779, "Abrir RAIN/EXPON
     *       pcompras" - tablas de programa de esos dos aun no migradas). Sigue SIN activar: con
     *       cobertura de datos parcial (3 de ~20 Especiales), bloquear impediria facturar por falta
     *       de dato migrado, no porque el caficultor realmente este fuera del programa. Revisar
     *       cuando se migren las tablas de programa restantes.</li>
     * </ul>
     */
    private static final Map<String, String> SPECIAL_TO_PROGRAMA = Map.of(
            "NESPRESSO - FTUSA", "NESPRESSO",
            "NESPRESSO LATE HARVEST - FTUSA", "NESPRESSO LATE HARVEST",
            "REGIONAL NARIÑO 4C", "REGIONAL NARIÑO 4C");

    private final GrowerRepository growerRepository;
    private final LegacyNessProgramRepository legacyNessProgramRepository;

    public GrowerServiceImpl(
            GrowerRepository growerRepository, LegacyNessProgramRepository legacyNessProgramRepository) {
        this.growerRepository = growerRepository;
        this.legacyNessProgramRepository = legacyNessProgramRepository;
    }

    @Override
    public GrowerResponse findByIdNumber(String idNumber) {
        Grower grower = growerRepository.findByIdNumber(idNumber)
                .orElseThrow(() -> new ResourceNotFoundException("No existe un caficultor con esa cedula"));
        return new GrowerResponse(
                grower.getId(),
                grower.getIdNumber(),
                grower.getFirstName(),
                grower.getSecondName(),
                grower.getLastName(),
                grower.getSecondLastName(),
                grower.getAddress(),
                grower.getPhone(),
                grower.getGrowerType(),
                grower.isActive(),
                grower.isDeceased(),
                grower.isWithdrawn());
    }

    @Override
    public Optional<GrowerProgramResponse> findProgram(String idNumber, String specialType) {
        LegacyNessProgram match = findMatch(idNumber, specialType);
        if (match == null) {
            return Optional.empty();
        }
        return Optional.of(new GrowerProgramResponse(match.getPrograma(), normalizeCupo(match.getCupo())));
    }

    @Override
    public void checkQuota(String idNumber, String specialType, BigDecimal netKg) {
        LegacyNessProgram match = findMatch(idNumber, specialType);
        if (match == null) {
            // Sin dato migrado para este caficultor/Especial: no hay evidencia de que exista un
            // cupo, asi que no se bloquea (mismo criterio de cobertura parcial que findProgram).
            return;
        }
        BigDecimal quota = new BigDecimal(match.getCupo());
        if (netKg.compareTo(quota) > 0) {
            throw new BusinessRuleException(
                    "CAFICULTOR EXCEDE CUPO ASIGNADO, NO LE PUEDE FACTURAR (cupo: " + normalizeCupo(match.getCupo())
                            + " kg, esta compra: " + netKg + " kg)");
        }
    }

    /** Mismo cruce cedula+Especial->Programa que usan findProgram/checkQuota (Form_COMPRAS.bas
     *  Cuadro_combinado61_AfterUpdate). Null si no hay match o es ambiguo sin Especial elegido. */
    private LegacyNessProgram findMatch(String idNumber, String specialType) {
        List<LegacyNessProgram> matches = legacyNessProgramRepository.findByNormalizedCedula(idNumber.trim());
        if (matches.isEmpty()) {
            return null;
        }
        String programa = specialType == null ? null : SPECIAL_TO_PROGRAMA.get(specialType);
        if (programa != null) {
            return matches.stream().filter(m -> programa.equals(m.getPrograma())).findFirst().orElse(null);
        } else if (matches.size() == 1) {
            // Especial aun no elegido: solo autocompleta/valida si no hay ambiguedad entre programas.
            return matches.get(0);
        }
        return null;
    }

    /** Quita el sufijo ".0" (artefacto del CSV origen) para mostrar el cupo como entero. */
    private static String normalizeCupo(String cupo) {
        return cupo.endsWith(".0") ? cupo.substring(0, cupo.length() - 2) : cupo;
    }
}
