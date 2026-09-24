-- announcement_number tambien se mudo a agency_announcement_number (V16) - un anuncio maestro nuevo
-- ya no tiene un numero propio, cada agencia tiene el suyo. Se me paso relajar esta columna en V16
-- junto con agency_id/costs/healthy_unit_price/bonus; sin esto, un anuncio maestro nuevo viola el
-- NOT NULL (announcement_number siempre llega null desde AnnouncementServiceImpl.create()).
ALTER TABLE announcement ALTER COLUMN announcement_number DROP NOT NULL;
