package com.cafeoccidente.backend.purchases.shared.repository;

import com.cafeoccidente.backend.purchases.shared.entity.ProductCode;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductCodeRepository extends JpaRepository<ProductCode, Long> {

    Optional<ProductCode> findBySpecialTypeAndFundId(String specialType, Long fundId);

    /** Cuadro "Cód Producto" del reporte de inventario, ordenado por codigo. */
    List<ProductCode> findAllByOrderByCodeAsc();

    /** Tipos de cafe (Especial) de un fondo: los que publica "Actualizar Anuncio con Factor" (RN-08.3). */
    List<ProductCode> findByFundIdOrderByCodeAsc(Long fundId);
}
