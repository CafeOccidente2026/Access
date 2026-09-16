package com.cafeoccidente.backend.purchases.shared.controller;

import com.cafeoccidente.backend.purchases.shared.dto.GrowerProgramResponse;
import com.cafeoccidente.backend.purchases.shared.dto.GrowerResponse;
import com.cafeoccidente.backend.purchases.shared.service.GrowerService;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Busqueda de caficultor por cedula (paso "Cedula" del formulario Compras Cafe Seco). */
@RestController
@RequestMapping("/api/growers/{idNumber}")
public class GrowerController {

    private final GrowerService growerService;

    public GrowerController(GrowerService growerService) {
        this.growerService = growerService;
    }

    @GetMapping
    public GrowerResponse findByIdNumber(@PathVariable String idNumber) {
        return growerService.findByIdNumber(idNumber);
    }

    /** Programa/Cupo informativos (staging_legacy_ness); 204 si no hay match, nunca bloquea. */
    @GetMapping("/program")
    public ResponseEntity<GrowerProgramResponse> findProgram(
            @PathVariable String idNumber, @RequestParam(required = false) String special) {
        Optional<GrowerProgramResponse> program = growerService.findProgram(idNumber, special);
        return program.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }
}
