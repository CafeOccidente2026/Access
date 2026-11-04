package com.cafeoccidente.backend.purchases.shared.dto;

/** Form_Conductores Actualizacion: solo Emp_Transp y Vehiculo son editables (el resto esta Locked). */
public record ConductorUpdateRequest(String transportCompany, String vehiclePlate) {
}
