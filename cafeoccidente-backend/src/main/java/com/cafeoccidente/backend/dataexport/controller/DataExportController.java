package com.cafeoccidente.backend.dataexport.controller;

import com.cafeoccidente.backend.dataexport.service.DataExportService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Exportar Informacion" y "Exportado Especial" del Menu Principal: solo ADMIN, todas las agencias
 * (sin agencyId) o una puntual. POST porque, como en Access, marca como exportado lo que devuelve.
 */
@RestController
@RequestMapping("/api/data-export")
@PreAuthorize("hasRole('ADMIN')")
public class DataExportController {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

    private final DataExportService dataExportService;

    public DataExportController(DataExportService dataExportService) {
        this.dataExportService = dataExportService;
    }

    @PostMapping
    public ResponseEntity<byte[]> export(@RequestParam(required = false) Long agencyId) {
        return zip(dataExportService.export(agencyId));
    }

    @PostMapping("/special")
    public ResponseEntity<byte[]> exportSpecial(
            @RequestParam(required = false) Long agencyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return zip(dataExportService.exportSpecial(agencyId, from, to));
    }

    private static ResponseEntity<byte[]> zip(byte[] content) {
        String name = "AplicComprasArchivos-" + LocalDateTime.now().format(STAMP) + ".zip";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(content);
    }
}
