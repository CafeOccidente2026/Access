package com.cafeoccidente.backend.purchases.annulment.repository;

import java.util.List;

/**
 * Las 5 tablas de compra y, en cada una, las columnas que AnularFactura pone en 0. AnularFactura (29
 * SetValue): Sacos, Kilos_Brutos, Destare, Kilos_Verdes, Kilos_Netos, Castigo, Vr_Inventario, Vr_Kilo,
 * Vr_Bruto, Aporte_Socio, Descuento_Coop, Descuento_Fro, OtrosDescuentos, Retefuente, Bonificacion,
 * W_AlmSana, W_AlmDefec, PorcAlmSana, PorcAlmDefec, IncCalidad, Costos, Pr_AlmSana, Pr_AlmDefec,
 * FPef/FPch/FPdat/FPtx (en purchase_payment_split), NumCheque y Neto_a_Pagar. No toca W_TotAlm,
 * PorcMerma, Pr_Base_CPS/Pr_Base_PC, Vr_Kilo_Comp ni VrIncCalidad: esas columnas quedan como estaban.
 */
public enum AnnulmentModule {
    DRY("dry_coffee_purchase", "DRY_COFFEE", Columns.DRY_LIKE),
    OTHER("other_coffee_purchase", "OTHER_COFFEE", Columns.DRY_LIKE),
    GREEN("green_coffee_purchase", "GREEN_COFFEE", List.of("bags_count", "gross_kg", "tare_kg", "green_kg", "net_kg",
            "penalty", "inventory_value", "unit_price", "gross_value", "associate_contribution",
            "cooperative_discount", "shrinkage_discount", "other_discounts", "withholding", "bonus", "costs",
            "healthy_unit_price", "defective_unit_price", "net_to_pay")),
    HUSK("husk_purchase", "HUSK", List.of("bags_count", "gross_kg", "tare_kg", "net_kg", "inventory_value",
            "unit_price", "gross_value", "associate_contribution", "cooperative_discount", "shrinkage_discount",
            "other_discounts", "withholding", "costs", "almond_weight", "almond_percentage", "point_price",
            "net_to_pay")),
    FERTI("ferti_futuro_purchase", "FERTI_FUTURO", List.of("sacos", "gross_kg", "tare_kg", "net_kg", "penalty",
            "inventory_value", "unit_price", "gross_value", "associate_contribution", "cooperative_discount",
            "freight_discount", "other_discounts", "withholding", "bonus", "healthy_stored_weight",
            "defective_stored_weight", "healthy_percentage", "defective_percentage", "quality_increment_amount",
            "costs", "healthy_unit_price", "defective_unit_price", "net_to_pay"));

    /** Tabla de la compra; su nombre de enum es la clave de purchase_payment_split.source. */
    final String table;
    /** inventory_movement.purchase_module de sus entradas de inventario. */
    final String inventoryModule;
    final List<String> zeroed;

    /** Cafe Seco y Cafes Otros tienen las mismas columnas (formularios COMPRAS y COMPRASESP). */
    private static final class Columns {
        static final List<String> DRY_LIKE = List.of("bags_count", "gross_kg", "tare_kg", "net_kg", "penalty",
                "inventory_value", "unit_price", "gross_value", "associate_contribution", "cooperative_discount",
                "freight_discount", "other_discounts", "withholding", "bonus", "healthy_stored_weight",
                "defective_stored_weight", "healthy_percentage", "defective_percentage", "costs",
                "healthy_unit_price", "defective_unit_price", "net_to_pay");
    }

    AnnulmentModule(String table, String inventoryModule, List<String> zeroed) {
        this.table = table;
        this.inventoryModule = inventoryModule;
        this.zeroed = zeroed;
    }
}
