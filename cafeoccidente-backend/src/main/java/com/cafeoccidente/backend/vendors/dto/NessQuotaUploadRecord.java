package com.cafeoccidente.backend.vendors.dto;

import java.time.OffsetDateTime;

/** Una carga anterior de Actualiza Cupos NESS (tabla cargas_cupos_ness): quien, cuando, que archivo y conteos. */
public record NessQuotaUploadRecord(
        Long id, OffsetDateTime date, String username, String fileName, int rows, int added, int changed,
        int unchanged, int removed) {
}
