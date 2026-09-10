package com.cafeoccidente.backend.purchases.shared.service.impl;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.purchases.shared.entity.ProductCode;
import com.cafeoccidente.backend.purchases.shared.repository.ProductCodeRepository;
import com.cafeoccidente.backend.purchases.shared.service.ProductCodeResolver;
import org.springframework.stereotype.Service;

@Service
public class ProductCodeResolverImpl implements ProductCodeResolver {

    private final ProductCodeRepository productCodeRepository;

    public ProductCodeResolverImpl(ProductCodeRepository productCodeRepository) {
        this.productCodeRepository = productCodeRepository;
    }

    @Override
    public ProductCode resolve(String specialType, Long fundId) {
        return productCodeRepository.findBySpecialTypeAndFundId(specialType, fundId)
                .orElseThrow(() -> new BusinessRuleException(
                        "No existe Cod_Prod para la combinacion Especial/Fondo indicada"));
    }
}
