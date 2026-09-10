package com.cafeoccidente.backend.purchases.shared.service;

import com.cafeoccidente.backend.purchases.shared.entity.ProductCode;

/**
 * Unica responsabilidad: resolver el Cod_Prod a partir de la combinacion Especial + Fondo
 * (Cuadro_combinado61_AfterUpdate en el formulario Access original). Data-driven: la
 * combinacion se busca en la tabla product_code (sembrada por Flyway), no en un if/else.
 */
public interface ProductCodeResolver {
    ProductCode resolve(String specialType, Long fundId);
}
