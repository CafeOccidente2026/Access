-- Cupo asignado a un anuncio (pantalla "Asignar Cupo", Access "ACTUALIZA CUPOS"): tope total de
-- kilos que se puede recibir bajo ESE anuncio en particular, no un cupo por caficultor. Confirmado
-- contra el VBA real: Form_ACTUALIZA CUPOS.bas / ACTUALIZA CUPOS.txt tiene RecordSource="ACTUALIZA
-- CUPOS" con controles Cupo/Saldo/Entregados/Anuncio - sin campo de cedula. "Entregados"/"Saldo" no
-- se guardan: se recalculan en cada consulta sumando las compras reales contra ese anuncio (igual
-- que el boton "Verificar Entregados" del VBA original, que suma SumaDeKilos_Netos a mano).
ALTER TABLE announcement_quota
    ADD COLUMN agency_announcement_number_id BIGINT NOT NULL REFERENCES agency_announcement_number (id),
    ADD COLUMN assigned_quota NUMERIC(10, 2) NOT NULL,
    ADD CONSTRAINT uk_announcement_quota_agency_announcement_number UNIQUE (agency_announcement_number_id);
