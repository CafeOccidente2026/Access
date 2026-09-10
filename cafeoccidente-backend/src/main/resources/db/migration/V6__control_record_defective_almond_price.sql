-- Pr_AlmDefec del formulario Access "Compras Cafe Seco". En el VBA original este textbox lo digita
-- el operador (o lo llena el programa externo pcompras); no hay formula ni parametro conocido y el
-- diseno de la pantalla migrada no tiene campo visible para capturarlo.
-- TODO: fijar el valor real cuando se conozca. Con 0, var4 de Sacos_LostFocus queda en 0, que es
-- justo lo que hace el VBA hoy (ahi Pr_AlmDefec solo se pone en 0 en los bloques de reset).
ALTER TABLE control_record
    ADD COLUMN defective_almond_unit_price NUMERIC(15, 2) NOT NULL DEFAULT 0;
