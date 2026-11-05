package com.cafeoccidente.backend.vendors.controller;

import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.vendors.dto.NessQuotaUploadRecord;
import com.cafeoccidente.backend.vendors.dto.NessQuotaUploadResult;
import com.cafeoccidente.backend.vendors.service.NessQuotaUploadService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Actualiza Cupos NESS (HU-54, RF-28.4): solo el Administrador (RN-28.4.1). El navegador manda el archivo tal
 * cual en el cuerpo (RN-28.4.2) y su nombre en {@code fileName}; la tabla NESS no es por agencia.
 */
@RestController
@RequestMapping("/api/vendors/ness-quotas")
@PreAuthorize(Roles.ADMIN)
public class NessQuotaUploadController {

    private final NessQuotaUploadService service;

    public NessQuotaUploadController(NessQuotaUploadService service) {
        this.service = service;
    }

    @PostMapping("/upload/preview")
    public NessQuotaUploadResult preview(@RequestParam String fileName, @RequestBody byte[] content) {
        return service.preview(fileName, content);
    }

    @PostMapping("/upload")
    public NessQuotaUploadResult apply(@RequestParam String fileName, @RequestBody byte[] content) {
        return service.apply(fileName, content);
    }

    @GetMapping("/uploads")
    public List<NessQuotaUploadRecord> history() {
        return service.history();
    }
}
