package com.cafeoccidente.backend.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.cafeoccidente.backend.inventory.entity.Remission;
import com.cafeoccidente.backend.purchases.drycoffee.entity.DryCoffeePurchase;
import com.cafeoccidente.backend.purchases.future.entity.FuturePurchase;
import com.cafeoccidente.backend.purchases.greencoffee.entity.GreenCoffeePurchase;
import com.cafeoccidente.backend.purchases.husk.entity.HuskPurchase;
import com.cafeoccidente.backend.purchases.shared.entity.Grower;
import com.cafeoccidente.backend.supplies.entity.LedgerEntry;
import com.cafeoccidente.backend.supplies.entity.PackagingEntry;
import jakarta.persistence.Convert;
import jakarta.persistence.EntityListeners;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Demo del 23-nov: lo que digita el usuario se guarda en mayusculas, aunque se mande a mano por la API. */
class UpperCaseConverterTest {

    private final UpperCaseConverter converter = new UpperCaseConverter();

    @Test
    void writesInUpperCaseWithTheSpanishLettersAndLeavesNullAlone() {
        assertThat(converter.convertToDatabaseColumn("trans oriente ñandú")).isEqualTo("TRANS ORIENTE ÑANDÚ");
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute("Como Estaba")).isEqualTo("Como Estaba");
    }

    /** Los campos de texto que se digitan en pantalla: nombres, direcciones, detalles, empresa, placa, finca... */
    private static final Map<Class<?>, String[]> TYPED = Map.of(
            Grower.class, new String[] {"firstName", "secondName", "lastName", "secondLastName", "address",
                "transportCompany", "vehiclePlate", "observation", "birthPlace"},
            LedgerEntry.class, new String[] {"detail"},
            PackagingEntry.class, new String[] {"detail"},
            FuturePurchase.class, new String[] {"firstName", "lastName", "address", "finca", "municipality", "vereda"},
            Remission.class, new String[] {"destination"});

    @Test
    void everyTypedTextFieldGoesThroughTheConverter() throws NoSuchFieldException {
        for (Map.Entry<Class<?>, String[]> entity : TYPED.entrySet()) {
            for (String name : entity.getValue()) {
                Convert convert = entity.getKey().getDeclaredField(name).getAnnotation(Convert.class);
                assertThat(convert).as(entity.getKey().getSimpleName() + "." + name).isNotNull();
                assertThat(convert.converter()).as(entity.getKey().getSimpleName() + "." + name)
                        .isEqualTo(UpperCaseConverter.class);
            }
        }
    }

    /** Seco, Verde y Pasilla guardan el vendedor con un solo listener (sin repetir @Convert en cada compra). */
    @Test
    void thePurchasesUpperCaseTheSellerWithTheSharedListener() {
        for (Class<?> purchase : List.of(DryCoffeePurchase.class, GreenCoffeePurchase.class, HuskPurchase.class)) {
            assertThat(SellerFields.class).as(purchase.getSimpleName()).isAssignableFrom(purchase);
            assertThat(purchase.getAnnotation(EntityListeners.class).value()).as(purchase.getSimpleName())
                    .contains(SellerFieldsUpperCase.class);
        }
        GreenCoffeePurchase purchase = new GreenCoffeePurchase();
        purchase.setFirstName("ana maría");
        purchase.setLastName(null);
        purchase.setAddress("vereda el diviso");

        new SellerFieldsUpperCase().upperCase(purchase);

        assertThat(purchase.getFirstName()).isEqualTo("ANA MARÍA");
        assertThat(purchase.getLastName()).isNull();
        assertThat(purchase.getAddress()).isEqualTo("VEREDA EL DIVISO");
    }
}
