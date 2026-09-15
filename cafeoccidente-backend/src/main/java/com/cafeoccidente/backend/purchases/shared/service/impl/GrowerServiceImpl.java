package com.cafeoccidente.backend.purchases.shared.service.impl;

import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.purchases.shared.dto.GrowerProgramResponse;
import com.cafeoccidente.backend.purchases.shared.dto.GrowerResponse;
import com.cafeoccidente.backend.purchases.shared.entity.Grower;
import com.cafeoccidente.backend.purchases.shared.entity.LegacyNessProgram;
import com.cafeoccidente.backend.purchases.shared.repository.GrowerRepository;
import com.cafeoccidente.backend.purchases.shared.repository.LegacyNessProgramRepository;
import com.cafeoccidente.backend.purchases.shared.service.GrowerService;
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
     * TODO(EnProg): el VBA original bloquea la factura si el caficultor no aparece en su programa
     * (EnProg=0) para estos mismos Especiales + RAINFOREST/EXPON (ver Form_COMPRAS.bas lineas
     * 761-779, y "Abrir RAIN/EXPON pcompras" - tablas de programa aun no migradas). No se activa ese
     * bloqueo aqui: con cobertura de datos parcial (3 de ~20 Especiales, y ninguna agencia distinta a
     * la de origen de ness_migrar.csv confirmada), bloquear impediria facturar por falta de dato
     * migrado, no porque el caficultor realmente este fuera del programa. Revisar cuando se
     * migren las tablas de programa de las agencias/Especiales restantes (RAINFOREST, EXPON, etc.)
     * y se pueda confirmar cobertura completa.
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
        List<LegacyNessProgram> matches = legacyNessProgramRepository.findByNormalizedCedula(idNumber.trim());
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        String programa = specialType == null ? null : SPECIAL_TO_PROGRAMA.get(specialType);
        LegacyNessProgram match;
        if (programa != null) {
            match = matches.stream().filter(m -> programa.equals(m.getPrograma())).findFirst().orElse(null);
        } else if (matches.size() == 1) {
            // Especial aun no elegido: solo autocompleta si no hay ambiguedad entre programas.
            match = matches.get(0);
        } else {
            match = null;
        }
        if (match == null) {
            return Optional.empty();
        }
        return Optional.of(new GrowerProgramResponse(match.getPrograma(), normalizeCupo(match.getCupo())));
    }

    /** Quita el sufijo ".0" (artefacto del CSV origen) para mostrar el cupo como entero. */
    private static String normalizeCupo(String cupo) {
        return cupo.endsWith(".0") ? cupo.substring(0, cupo.length() - 2) : cupo;
    }
}
