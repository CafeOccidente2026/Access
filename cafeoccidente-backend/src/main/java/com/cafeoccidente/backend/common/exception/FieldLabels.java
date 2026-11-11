package com.cafeoccidente.backend.common.exception;

import java.util.Map;

/**
 * Nombre en pantalla de los campos de los request (prueba del 11-nov, punto 4): una validacion dice "Falta o esta
 * mal: Kilos Brutos" en vez de "grossKg". Un campo que no este aca sale con su nombre tal cual.
 */
final class FieldLabels {

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("active", "Activo"), Map.entry("address", "Dirección"), Map.entry("agencyId", "Agencia"),
            Map.entry("almondWeight", "Peso Almendra"), Map.entry("amount", "Valor"),
            Map.entry("announcedKg", "Kilos Anunciados"), Map.entry("announcementNumber", "Anuncio"),
            Map.entry("assignedQuota", "Cupo"), Map.entry("associatePercentage", "% Aportes Asociados"),
            Map.entry("avgHuskPercentage", "% Kg de Pasilla Promedio"), Map.entry("bagsCount", "Sacos"),
            Map.entry("baseFactor", "Factor Base"), Map.entry("baseHusk", "Base Pasilla"), Map.entry("baseLoad", "Base Carga"),
            Map.entry("basePriceLoad", "Precio Base Carga"), Map.entry("baseWithholding", "Base Rete Fuente"),
            Map.entry("businessName", "Razón Social"), Map.entry("cardAmount", "Datáfono"),
            Map.entry("cashAmount", "Efectivo"), Map.entry("checkAmount", "Cheque"), Map.entry("checkNumber", "N° Cheque"),
            Map.entry("classification", "Clasificación"), Map.entry("compKgPrice", "Vr. Kilo Comp"),
            Map.entry("controlNumber", "Núm Registro Control"), Map.entry("costs", "Costos"),
            Map.entry("defectiveStoredWeight", "Peso Tot Pasilla"), Map.entry("defectiveUnitPrice", "Precio Pasilla"),
            Map.entry("deliveryDate", "Fecha de Entrega"), Map.entry("deliveryKg", "Esta Entrega"),
            Map.entry("detail", "Detalle"), Map.entry("dianResolution", "Resol DIAN"), Map.entry("email", "Correo"),
            Map.entry("excelsoKg", "Kg de Excelso"), Map.entry("firstName", "Nombre"),
            Map.entry("freightDiscount", "Descuento Fro"), Map.entry("fundCode", "Fondo"), Map.entry("fundId", "Fondo"),
            Map.entry("futurePurchaseId", "Compra Anunciada"), Map.entry("greenCoffeePercentage", "Porcentaje Café Verde"),
            Map.entry("greenDefectiveUnitPrice", "Pr_AlmDefec CV"), Map.entry("greenHealthyStoredWeight", "Peso Alm Sana CV"),
            Map.entry("greenHealthyUnitPrice", "Pr Alm Sana CV"), Map.entry("greenTotalHuskWeight", "Peso Tot Pasilla CV"),
            Map.entry("greenTotalStoredWeight", "Peso Tot Alm CV"), Map.entry("grossKg", "Kilos Brutos"),
            Map.entry("idNumber", "Cédula"), Map.entry("inventoryMovementId", "Entrada de Inventario"),
            Map.entry("lastName", "Apellido"), Map.entry("lines", "Líneas"), Map.entry("module", "Formulario"),
            Map.entry("name", "Nombre"), Map.entry("nonAssociateDiscount", "% Desc Coop No Asociados"),
            Map.entry("otherDiscounts", "Otros Descuentos"), Map.entry("packagingQuantity", "Cantidad de Empaques"),
            Map.entry("packagingType", "Tipo de Empaque"), Map.entry("password", "Contraseña"), Map.entry("payment", "Forma de Pago"),
            Map.entry("penalty", "Castigo"), Map.entry("phone", "Teléfono"), Map.entry("pointPrice", "Pr Punto"),
            Map.entry("postalCode", "Código Postal"), Map.entry("prefix", "Prefijo"), Map.entry("purchases", "Compras"),
            Map.entry("quantity", "Cantidad"), Map.entry("quotaAnnouncementNumber", "Anuncio con Cupo"),
            Map.entry("remissionDate", "Fecha de Remisión"), Map.entry("resolutionDate", "Fecha Res"),
            Map.entry("resolutionFrom", "Desde Res"), Map.entry("resolutionTo", "Hasta Res"), Map.entry("roleId", "Rol"),
            Map.entry("sacos", "Sacos"), Map.entry("sampleSize", "Tamaño Muestra"), Map.entry("screens", "Pantallas"),
            Map.entry("secondLastName", "Segundo Apellido"), Map.entry("secondName", "Segundo Nombre"), Map.entry("sex", "Sexo"),
            Map.entry("shrinkageDiscount", "Descuento Fro"), Map.entry("specialSurcharge", "SobrePr Carga Esp"),
            Map.entry("specialType", "Especial"), Map.entry("specialtyThreshold", "Umbral Especiales"),
            Map.entry("tareKg", "Destare"), Map.entry("totalStoredWeight", "Peso Tot Alm"),
            Map.entry("transferAmount", "Transferencia"), Map.entry("trustedId", "Fiel"), Map.entry("userId", "Usuario"),
            Map.entry("username", "Usuario"), Map.entry("validity", "Vigencia"),
            Map.entry("withholdingPercentage", "Porcentaje Rete Fuente"));

    private FieldLabels() {
    }

    /** "payment.cashAmount" -> "Efectivo": cuenta el ultimo tramo del campo anidado. */
    static String of(String field) {
        String last = field.substring(field.lastIndexOf('.') + 1);
        int index = last.indexOf('[');
        String name = index < 0 ? last : last.substring(0, index);
        return LABELS.getOrDefault(name, name);
    }
}
