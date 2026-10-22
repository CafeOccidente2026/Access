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
    DRY("compras_cafe_seco", "DRY_COFFEE", Columns.DRY_LIKE),
    GREEN("compras_cafe_verde", "GREEN_COFFEE", List.of("sacos", "kilos_brutos", "destare", "kilos_verdes", "kilos_netos",
            "castigo", "vr_inventario", "vr_kilo", "vr_bruto", "aporte_asociado",
            "descuento_cooperativo", "descuento_financiero", "otros_descuentos", "retefuente", "bonificacion", "costos",
            "pr_alm_sana", "pr_alm_defec", "neto_a_pagar")),
    HUSK("compras_pasilla", "HUSK", List.of("sacos", "kilos_brutos", "destare", "kilos_netos", "vr_inventario",
            "vr_kilo", "vr_bruto", "aporte_asociado", "descuento_cooperativo", "descuento_financiero",
            "otros_descuentos", "retefuente", "costos", "peso_almendra", "porc_almendra", "pr_punto",
            "neto_a_pagar")),
    FERTI("compras_ferti_futuro", "FERTI_FUTURO", List.of("sacos", "kilos_brutos", "destare", "kilos_netos", "castigo",
            "vr_inventario", "vr_kilo", "vr_bruto", "aporte_asociado", "descuento_cooperativo",
            "descuento_financiero", "otros_descuentos", "retefuente", "bonificacion", "peso_alm_sana",
            "peso_tot_pasilla", "factor", "porc_kg_pasilla", "vr_inc_calidad",
            "costos", "pr_alm_sana", "pr_alm_defec", "neto_a_pagar"));

    /** Tabla de la compra; su nombre de enum es la clave de purchase_payment_split.source. */
    final String table;
    /** inventory_movement.purchase_module de sus entradas de inventario. */
    final String inventoryModule;
    final List<String> zeroed;

    /** Columnas de Cafe Seco (formulario COMPRAS). */
    private static final class Columns {
        static final List<String> DRY_LIKE = List.of("sacos", "kilos_brutos", "destare", "kilos_netos", "castigo",
                "vr_inventario", "vr_kilo", "vr_bruto", "aporte_asociado", "descuento_cooperativo",
                "descuento_financiero", "otros_descuentos", "retefuente", "bonificacion", "peso_alm_sana",
                "peso_tot_pasilla", "factor", "porc_kg_pasilla", "costos",
                "pr_alm_sana", "pr_alm_defec", "neto_a_pagar");
    }

    AnnulmentModule(String table, String inventoryModule, List<String> zeroed) {
        this.table = table;
        this.inventoryModule = inventoryModule;
        this.zeroed = zeroed;
    }
}
