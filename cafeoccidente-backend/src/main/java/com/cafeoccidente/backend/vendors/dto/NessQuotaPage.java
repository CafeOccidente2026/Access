package com.cafeoccidente.backend.vendors.dto;

import java.util.List;

public record NessQuotaPage(List<NessQuotaRow> rows, long total) {
}
