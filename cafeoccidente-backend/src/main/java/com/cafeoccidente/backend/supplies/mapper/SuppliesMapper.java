package com.cafeoccidente.backend.supplies.mapper;

import com.cafeoccidente.backend.supplies.dto.EntryResponse;
import com.cafeoccidente.backend.supplies.entity.LedgerEntry;
import com.cafeoccidente.backend.supplies.entity.PackagingEntry;
import com.cafeoccidente.backend.supplies.repository.GrowerName;
import org.springframework.stereotype.Component;

/** Unica responsabilidad: traducir los registros de Suministros a su DTO de respuesta. */
@Component
public class SuppliesMapper {

    public EntryResponse toResponse(LedgerEntry entry) {
        return new EntryResponse(entry.getId(), entry.getTransactionId(), entry.getAgency().getName(),
                entry.getEntryDate(), entry.getIdNumber(), null, null);
    }

    public EntryResponse toResponse(PackagingEntry entry, GrowerName grower) {
        return new EntryResponse(entry.getId(), entry.getTransactionId(), entry.getAgency().getName(),
                entry.getEntryDate(), entry.getIdNumber(),
                grower == null ? null : grower.firstNames(), grower == null ? null : grower.lastNames());
    }
}
