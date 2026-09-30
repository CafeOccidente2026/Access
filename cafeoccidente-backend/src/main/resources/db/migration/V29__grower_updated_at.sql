-- "Ingresar Conductores" > Actualizar: fecha y hora de la ultima modificacion de Emp. Transp. /
-- Vehiculo. Nula (las filas existentes nunca se actualizaron por esta pantalla). affiliation_date
-- (FechaAfiliacion en Access, Form_Conductores.txt) NO se toca al actualizar.
ALTER TABLE grower ADD COLUMN updated_at TIMESTAMP;
