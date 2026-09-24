package com.cafeoccidente.backend.inventory.controller;

import com.cafeoccidente.backend.inventory.dto.RemissionRequest;
import com.cafeoccidente.backend.inventory.dto.RemissionResponse;
import com.cafeoccidente.backend.inventory.service.RemissionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/remissions")
public class RemissionController {

    private final RemissionService remissionService;

    public RemissionController(RemissionService remissionService) {
        this.remissionService = remissionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RemissionResponse create(@Valid @RequestBody RemissionRequest request) {
        return remissionService.create(request);
    }

    @GetMapping("/{id}")
    public RemissionResponse findById(@PathVariable Long id) {
        return remissionService.findById(id);
    }
}
