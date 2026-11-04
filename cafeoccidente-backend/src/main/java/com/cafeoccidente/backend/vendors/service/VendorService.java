package com.cafeoccidente.backend.vendors.service;

import com.cafeoccidente.backend.vendors.dto.AssociatePage;
import com.cafeoccidente.backend.vendors.dto.AssociateResponse;
import com.cafeoccidente.backend.vendors.dto.VendorCreateRequest;

/** Altas y consulta de la tabla Asociados (grower) desde "MENUS VENDEDORES". */
public interface VendorService {

    /** "Ingresar Vendedores": agencyId ya resuelto por rol (USER = la de su sesion). */
    AssociateResponse create(VendorCreateRequest request, Long agencyId);

    /** "Consultar Asociados": registro en la posicion dada (0-based), en el orden de la tabla. */
    AssociatePage associateAt(long position);

    /** "Buscar" del formulario: posicion del asociado con esa cedula. */
    AssociatePage findAssociate(String idNumber);
}
