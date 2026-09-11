-- Factura (numero consecutivo dentro del rango autorizado por la resolucion DIAN vigente en
-- ControlRecord: resolution_from/resolution_to/prefix). Se reserva al confirmar "Fondo" y se
-- persiste junto con la compra.

ALTER TABLE dry_coffee_purchase ADD COLUMN invoice_number INT NOT NULL UNIQUE;
