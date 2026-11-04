package com.cafeoccidente.backend.vendors.mapper;

import com.cafeoccidente.backend.purchases.shared.entity.Grower;
import com.cafeoccidente.backend.vendors.dto.AssociateResponse;

public final class AssociateMapper {

    private AssociateMapper() {
    }

    public static AssociateResponse toResponse(Grower g) {
        return new AssociateResponse(
                g.getIdNumber(),
                g.getFirstName(),
                g.getSecondName(),
                g.getLastName(),
                g.getSecondLastName(),
                g.getAgency() == null ? null : g.getAgency().getName(),
                g.getBirthDate(),
                g.getBirthPlace(),
                g.getMaritalStatus(),
                g.getCoffeeIdCard(),
                g.getAddress(),
                g.getPhone(),
                g.getAffiliationDate(),
                g.getActNumber(),
                g.getAccepted(),
                g.getEligible(),
                g.isWithdrawn(),
                g.isDeceased(),
                g.getObservation(),
                g.getGrowerType(),
                g.getTransportCompany(),
                g.getVehiclePlate(),
                g.getExported(),
                g.getIsAssociation(),
                g.getIsNew(),
                g.getCityCode(),
                g.getSex(),
                g.getCountry());
    }
}
