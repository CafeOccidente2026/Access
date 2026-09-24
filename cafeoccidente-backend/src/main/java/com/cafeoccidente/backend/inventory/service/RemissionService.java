package com.cafeoccidente.backend.inventory.service;

import com.cafeoccidente.backend.inventory.dto.RemissionRequest;
import com.cafeoccidente.backend.inventory.dto.RemissionResponse;

public interface RemissionService {

    RemissionResponse create(RemissionRequest request);

    RemissionResponse findById(Long id);
}
