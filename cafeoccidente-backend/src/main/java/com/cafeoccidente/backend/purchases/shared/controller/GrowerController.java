package com.cafeoccidente.backend.purchases.shared.controller;

import com.cafeoccidente.backend.purchases.shared.dto.GrowerResponse;
import com.cafeoccidente.backend.purchases.shared.service.GrowerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Busqueda de caficultor por cedula (paso "Cedula" del formulario Compras Cafe Seco). */
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
}
