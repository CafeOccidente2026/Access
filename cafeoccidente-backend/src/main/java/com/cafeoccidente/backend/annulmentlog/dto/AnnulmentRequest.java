package com.cafeoccidente.backend.annulmentlog.dto;

import jakarta.validation.constraints.Size;

/** Anular Documento y Anular Remision: el motivo es obligatorio (26-nov). Se valida sin los espacios de los extremos. */
public record AnnulmentRequest(@Size(max = 500) String reason) {
}
