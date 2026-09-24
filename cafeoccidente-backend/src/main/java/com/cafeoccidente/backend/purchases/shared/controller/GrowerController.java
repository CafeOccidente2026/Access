package com.cafeoccidente.backend.purchases.shared.controller;

import com.cafeoccidente.backend.purchases.shared.dto.GrowerCreateRequest;
import com.cafeoccidente.backend.purchases.shared.dto.GrowerProgramResponse;
import com.cafeoccidente.backend.purchases.shared.dto.GrowerResponse;
import com.cafeoccidente.backend.purchases.shared.service.GrowerService;
import jakarta.validation.Valid;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Busqueda de caficultor por cedula (paso "Cedula" del formulario Compras Cafe Seco) y alta de conductores. */
@RestController
@RequestMapping("/api/growers")
public class GrowerController {

    private final GrowerService growerService;

    public GrowerController(GrowerService growerService) {
        this.growerService = growerService;
    }

    @GetMapping("/{idNumber}")
    public GrowerResponse findByIdNumber(@PathVariable String idNumber) {
        return growerService.findByIdNumber(idNumber);
    }

    /** Programa/Cupo informativos (staging_legacy_ness); 204 si no hay match, nunca bloquea. */
    @GetMapping("/{idNumber}/program")
    public ResponseEntity<GrowerProgramResponse> findProgram(
            @PathVariable String idNumber, @RequestParam(required = false) String special) {
        Optional<GrowerProgramResponse> program = growerService.findProgram(idNumber, special);
        return program.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** "Ingresar Conductores" (Form_Conductores.bas) - alta rapida desde Registrar Salidas. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GrowerResponse createConductor(@Valid @RequestBody GrowerCreateRequest request) {
        return growerService.createConductor(request);
    }
}
