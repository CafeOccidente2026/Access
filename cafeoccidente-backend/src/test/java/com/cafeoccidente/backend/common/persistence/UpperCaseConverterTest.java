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
            DryCoffeePurchase.class, new String[] {"firstName", "lastName", "address"},
            GreenCoffeePurchase.class, new String[] {"firstName", "lastName", "address"},
            HuskPurchase.class, new String[] {"firstName", "lastName", "address"},
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
}
