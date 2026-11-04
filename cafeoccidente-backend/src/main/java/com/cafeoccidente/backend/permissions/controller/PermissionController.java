package com.cafeoccidente.backend.permissions.controller;

import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.permissions.dto.PermissionGrantRequest;
import com.cafeoccidente.backend.permissions.dto.PermissionResponse;
import com.cafeoccidente.backend.permissions.entity.Screen;
import com.cafeoccidente.backend.permissions.service.PermissionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Pantalla Permisos (Acceso Principal, RF-02), solo para el Administrador. */
@RestController
@RequestMapping("/api/permissions")
@PreAuthorize(Roles.ADMIN)
public class PermissionController {

    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping
    public List<PermissionResponse> list(@RequestParam Long userId) {
        return permissionService.list(userId);
    }

    @PostMapping
    public List<PermissionResponse> grant(@Valid @RequestBody PermissionGrantRequest request) {
        return permissionService.grant(request.userId(), request.screens());
    }

    @DeleteMapping("/{userId}/{screen}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable Long userId, @PathVariable Screen screen) {
        permissionService.revoke(userId, screen);
    }
}
