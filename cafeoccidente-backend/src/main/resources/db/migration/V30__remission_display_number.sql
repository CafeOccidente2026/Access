-- Numero de remision generado por el sistema: {prefijo ControlRecord}-{LF|RP}-{consecutivo}, con un
-- consecutivo propio por agencia + fondo (en Access se digitaba del talonario preimpreso: TA0436...
-- para RP y TA0941... para LF, rangos separados por fondo). remission_number no se toca.
ALTER TABLE remission ADD COLUMN fund_id BIGINT REFERENCES fund (id);
ALTER TABLE remission ADD COLUMN sequence_number INTEGER;
ALTER TABLE remission ADD COLUMN display_number VARCHAR(40);

-- Remisiones existentes: fondo tomado de sus lineas (todas son de un solo fondo) y numero visible =
-- su numero actual, sin reformatear. sequence_number queda NULL: no pertenecen a la serie nueva.
UPDATE remission r
SET fund_id = (SELECT MIN(p.fund_id)
               FROM remission_line l
               JOIN inventory_movement m ON m.id = l.inventory_movement_id
               JOIN product_code p ON p.id = m.product_code_id
               WHERE l.remission_id = r.id);
UPDATE remission SET display_number = remission_number::text;

ALTER TABLE remission ALTER COLUMN display_number SET NOT NULL;
CREATE UNIQUE INDEX ux_remission_agency_display_number ON remission (agency_id, display_number);
CREATE UNIQUE INDEX ux_remission_agency_fund_sequence ON remission (agency_id, fund_id, sequence_number);
